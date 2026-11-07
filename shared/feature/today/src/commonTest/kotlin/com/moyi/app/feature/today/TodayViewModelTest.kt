package com.moyi.app.feature.today

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.cash.turbine.test
import com.moyi.app.feature.today.common.DailyLoopState
import com.moyi.app.feature.today.common.DeleteResult
import com.moyi.app.feature.today.common.RevealLedger
import com.moyi.app.feature.today.common.TodayStatus
import com.moyi.app.feature.today.today.TodayAction
import com.moyi.app.feature.today.today.TodayEvent
import com.moyi.app.feature.today.today.TodayLoad
import com.moyi.app.feature.today.today.TodayViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModelTest : MainDispatcherTest() {
    private val loop = FakeDailyLoop()
    private val ledger = RevealLedger()
    private val viewModel by lazy { TodayViewModel(loop, ledger) }

    private fun TestScope.collectState() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
    }

    // ---- Loading ----

    @Test
    fun opensOnTheBareSurfaceAndForgetsAnyDayLeftFromBefore() = runTest {
        // A day from a previous session must not flash up under the next person.
        loop.show(dayView(TodayStatus.Revealed))
        collectState()
        assertEquals(1, loop.clears)
        assertEquals(TodayLoad.Loading, viewModel.state.value.load)
    }

    @Test
    fun everyForegroundRefreshesTheDay() = runTest {
        loop.onRefresh = DailyLoopState.Ready(dayView(TodayStatus.NotWritten))
        collectState()
        viewModel.onAction(TodayAction.OnForeground)
        viewModel.onAction(TodayAction.OnForeground)
        assertEquals(2, loop.refreshes)
        assertEquals(TodayLoad.Ready, viewModel.state.value.load)
        assertEquals(TodayStatus.NotWritten, viewModel.state.value.status)
        assertEquals(PROMPT.text, viewModel.state.value.prompt)
        assertEquals(DAY, viewModel.state.value.date)
    }

    @Test
    fun aFailedFirstLoadIsTheErrorAndTryAgainAsksAgain() = runTest {
        loop.onRefresh = DailyLoopState.Failed
        collectState()
        viewModel.onAction(TodayAction.OnForeground)
        assertEquals(TodayLoad.Failed, viewModel.state.value.load)

        loop.onRefresh = DailyLoopState.Ready(dayView(TodayStatus.Waiting))
        viewModel.onAction(TodayAction.OnRetryClick)
        assertEquals(TodayLoad.Ready, viewModel.state.value.load)
        assertEquals(TodayStatus.Waiting, viewModel.state.value.status)
    }

    @Test
    fun noOpenBondSendsThePersonBackToTheStart() = runTest {
        collectState()
        viewModel.events.test {
            loop.state.value = DailyLoopState.NoOpenBond
            assertEquals(TodayEvent.NavigateToStart, awaitItem())
        }
        // Nothing of the old day is drawn while the shell re-routes.
        assertEquals(TodayLoad.Loading, viewModel.state.value.load)
    }

    // ---- The reveal ----

    @Test
    fun aDayFoundRevealedPlaysTheRevealOnce() = runTest {
        collectState()
        viewModel.events.test {
            loop.show(dayView(TodayStatus.Revealed))
            assertEquals(TodayEvent.NavigateToReveal, awaitItem())

            // The next refresh of the same open day is not a second moment.
            loop.show(dayView(TodayStatus.Revealed, myEntry = "The same day, refreshed."))
            expectNoEvents()
        }
    }

    @Test
    fun aDayThatOpenedInAnEarlierVisitDoesNotPlayAgain() = runTest {
        ledger.firstShowing("bond-1", DAY)
        collectState()
        viewModel.events.test {
            loop.show(dayView(TodayStatus.Revealed))
            expectNoEvents()
        }
    }

    @Test
    fun waitingATombstoneAndASoloDayNeverPlayTheReveal() = runTest {
        collectState()
        viewModel.events.test {
            // PENDING_REVEAL maps to Waiting (states.md §5a): both wrote, the time has not come.
            for (status in listOf(TodayStatus.Waiting, TodayStatus.Tombstone, TodayStatus.SoloTheyWrote, TodayStatus.SoloYouWrote)) {
                loop.show(dayView(status))
            }
            expectNoEvents()
        }
    }

    @Test
    fun aRevealedDayWithoutThePartnersWordsDoesNotPlay() = runTest {
        // The partner deleted theirs: there is nothing to turn to.
        collectState()
        viewModel.events.test {
            loop.show(dayView(TodayStatus.Revealed, partnerEntry = null))
            expectNoEvents()
        }
    }

    // ---- What the state may hold ----

    @Test
    fun thePartnersWordsAreHeldOnlyWhenTheDayIsOpenOrSolo() = runTest {
        collectState()
        // A screen cannot leak what its state does not hold (states.md §3). The second lock:
        // even a day handed over with the words in a hidden layout drops them.
        for (status in TodayStatus.entries) {
            loop.show(dayView(status).copy(partnerEntry = THEIRS))
            val held = viewModel.state.value.partnerEntry
            if (status in listOf(TodayStatus.Revealed, TodayStatus.Tombstone, TodayStatus.SoloTheyWrote)) {
                assertEquals(THEIRS, held, "partnerEntry must be set in $status")
            } else {
                assertNull(held, "partnerEntry must be null in $status")
            }
        }
    }

    // ---- Writing and deleting ----

    @Test
    fun editingAndWritingOpenTheComposer() = runTest {
        viewModel.events.test {
            viewModel.onAction(TodayAction.OnEditClick)
            assertEquals(TodayEvent.NavigateToWrite(editing = true), awaitItem())
            viewModel.onAction(TodayAction.OnWriteClick)
            assertEquals(TodayEvent.NavigateToWrite(editing = false), awaitItem())
        }
    }

    @Test
    fun deletingBeforeTheRevealNeedsNoConfirmation() = runTest {
        collectState()
        loop.show(dayView(TodayStatus.Waiting))
        viewModel.events.test {
            viewModel.onAction(TodayAction.OnDeleteBeforeRevealClick)
            expectNoEvents()
        }
        assertEquals(1, loop.deletesBefore)
        assertFalse(viewModel.state.value.confirmingDelete)
        assertFalse(viewModel.state.value.actionFailed)
    }

    @Test
    fun aFailedDeleteBeforeTheRevealSaysSoAndAGoneOneDoesNot() = runTest {
        collectState()
        loop.show(dayView(TodayStatus.Waiting))
        loop.deleteBeforeResult = DeleteResult.Failed
        viewModel.onAction(TodayAction.OnDeleteBeforeRevealClick)
        assertTrue(viewModel.state.value.actionFailed)

        // Already gone (404) is not a failure: Today has caught up.
        loop.deleteBeforeResult = DeleteResult.Gone
        viewModel.onAction(TodayAction.OnDeleteBeforeRevealClick)
        assertFalse(viewModel.state.value.actionFailed)
    }

    @Test
    fun deletingAfterTheRevealAsksInADialogOverTodayAndNavigatesNowhere() = runTest {
        ledger.firstShowing("bond-1", DAY) // the reveal already played
        collectState()
        loop.show(dayView(TodayStatus.Revealed))
        viewModel.events.test {
            viewModel.onAction(TodayAction.OnDeleteAfterRevealClick)
            assertTrue(viewModel.state.value.confirmingDelete)
            expectNoEvents()
        }
        assertEquals(0, loop.deletesAfter)
    }

    @Test
    fun confirmingTheDeleteSendsItOnceAndClosesTheDialog() = runTest {
        collectState()
        loop.show(dayView(TodayStatus.Revealed))
        viewModel.onAction(TodayAction.OnDeleteAfterRevealClick)
        viewModel.onAction(TodayAction.OnDeleteConfirm)
        assertEquals(1, loop.deletesAfter)
        assertFalse(viewModel.state.value.confirmingDelete)
        assertFalse(viewModel.state.value.deleting)
        assertFalse(viewModel.state.value.actionFailed)
    }

    @Test
    fun whileDeletingTheDeleteCannotBeAskedForOrSentAgain() = runTest {
        collectState()
        loop.show(dayView(TodayStatus.Revealed))
        val gate = CompletableDeferred<Unit>()
        loop.gate = gate
        viewModel.onAction(TodayAction.OnDeleteAfterRevealClick)
        viewModel.onAction(TodayAction.OnDeleteConfirm)
        assertTrue(viewModel.state.value.deleting)

        viewModel.onAction(TodayAction.OnDeleteAfterRevealClick)
        viewModel.onAction(TodayAction.OnDeleteConfirm)
        assertFalse(viewModel.state.value.confirmingDelete)
        assertEquals(1, loop.deletesAfter)

        gate.complete(Unit)
        assertFalse(viewModel.state.value.deleting)
    }

    @Test
    fun aFailedDeleteAfterTheRevealKeepsTheEntryAndSaysSo() = runTest {
        collectState()
        loop.show(dayView(TodayStatus.Revealed))
        loop.deleteAfterResult = DeleteResult.Failed
        viewModel.onAction(TodayAction.OnDeleteAfterRevealClick)
        viewModel.onAction(TodayAction.OnDeleteConfirm)
        assertTrue(viewModel.state.value.actionFailed)
        assertEquals(TodayStatus.Revealed, viewModel.state.value.status)
    }

    @Test
    fun cancellingTheDeleteClosesTheDialogAndDeletesNothing() = runTest {
        collectState()
        loop.show(dayView(TodayStatus.Revealed))
        viewModel.onAction(TodayAction.OnDeleteAfterRevealClick)
        viewModel.onAction(TodayAction.OnDeleteDismiss)
        assertFalse(viewModel.state.value.confirmingDelete)
        assertEquals(0, loop.deletesAfter)
    }

    @Test
    fun aDeleteQuestionDoesNotStayOpenOverADayThatMovedOn() = runTest {
        collectState()
        loop.show(dayView(TodayStatus.Revealed))
        viewModel.onAction(TodayAction.OnDeleteAfterRevealClick)
        // Deleted from the other phone: the day is a tombstone now, and the question is moot.
        loop.show(dayView(TodayStatus.Tombstone))
        assertFalse(viewModel.state.value.confirmingDelete)
    }

    @Test
    fun theDayIsForgottenWhenTodayGoes() {
        // Today is the root of the signed-in app: it goes with a sign-out, and the day goes with it.
        val store = ViewModelStore()
        val factory = viewModelFactory { initializer { TodayViewModel(loop, ledger) } }
        ViewModelProvider.create(store, factory)[TodayViewModel::class]
        loop.show(dayView(TodayStatus.Revealed))
        val clearsBefore = loop.clears

        store.clear()

        assertEquals(clearsBefore + 1, loop.clears)
        assertEquals(DailyLoopState.Loading, loop.state.value)
    }
}
