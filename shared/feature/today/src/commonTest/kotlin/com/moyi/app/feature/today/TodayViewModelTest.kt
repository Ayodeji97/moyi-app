package com.moyi.app.feature.today

import app.cash.turbine.test
import com.moyi.app.feature.today.common.FakeDailyLoop
import com.moyi.app.feature.today.common.TodayStatus
import com.moyi.app.feature.today.today.TodayAction
import com.moyi.app.feature.today.today.TodayEvent
import com.moyi.app.feature.today.today.TodayViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModelTest : MainDispatcherTest() {
    private val loop = FakeDailyLoop()
    private val viewModel by lazy { TodayViewModel(loop, fakeLatency = Duration.ZERO) }

    @Test
    fun startsNotWritten() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
        assertEquals(TodayStatus.NotWritten, viewModel.state.value.status)
    }

    @Test
    fun theDebugSwitchStepsEveryLayoutAndPassesThroughTheReveal() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
        viewModel.events.test {
            viewModel.onAction(TodayAction.OnDebugCycle)
            assertEquals(TodayStatus.Waiting, viewModel.state.value.status)

            // From waiting, the switch plays the reveal instead of jumping to revealed.
            viewModel.onAction(TodayAction.OnDebugCycle)
            assertEquals(TodayEvent.NavigateToReveal, awaitItem())
            assertEquals(TodayStatus.Waiting, viewModel.state.value.status)
        }

        loop.setStatus(TodayStatus.Revealed)
        val seen = mutableListOf(viewModel.state.value.status)
        repeat(TodayStatus.entries.size - 2) {
            viewModel.onAction(TodayAction.OnDebugCycle)
            seen += viewModel.state.value.status
        }
        assertEquals(
            listOf(
                TodayStatus.Revealed,
                TodayStatus.Tombstone,
                TodayStatus.PartnerNotJoined,
                TodayStatus.SoloYouWrote,
                TodayStatus.SoloTheyWrote,
                TodayStatus.Queued,
                TodayStatus.NotWritten,
            ),
            seen,
        )
    }

    @Test
    fun deletingBeforeTheRevealNeedsNoConfirmation() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
        loop.setStatus(TodayStatus.Waiting)
        viewModel.events.test {
            viewModel.onAction(TodayAction.OnDeleteBeforeRevealClick)
            assertEquals(TodayStatus.NotWritten, viewModel.state.value.status)
            expectNoEvents()
        }
    }

    @Test
    fun deletingAfterTheRevealAsksInADialogOverTodayAndNavigatesNowhere() = runTest {
        loop.setStatus(TodayStatus.Revealed)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
        viewModel.events.test {
            viewModel.onAction(TodayAction.OnDeleteAfterRevealClick)
            assertTrue(viewModel.state.value.confirmingDelete)
            expectNoEvents()
        }
        assertEquals(TodayStatus.Revealed, viewModel.state.value.status)
    }

    @Test
    fun confirmingTheDeleteClosesTheDialogAndLeavesTheTombstone() = runTest {
        loop.setStatus(TodayStatus.Revealed)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
        viewModel.onAction(TodayAction.OnDeleteAfterRevealClick)
        viewModel.onAction(TodayAction.OnDeleteConfirm)
        assertFalse(viewModel.state.value.confirmingDelete)
        assertFalse(viewModel.state.value.deleting)
        assertEquals(TodayStatus.Tombstone, viewModel.state.value.status)
    }

    @Test
    fun whileDeletingTheDeleteCannotBeAskedForOrSentAgain() = runTest {
        // Irreversible, so not optimistic: Today stays revealed until the delete lands.
        loop.setStatus(TodayStatus.Revealed)
        val slow = TodayViewModel(loop, fakeLatency = 1.hours)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { slow.state.collect {} }
        slow.onAction(TodayAction.OnDeleteAfterRevealClick)
        slow.onAction(TodayAction.OnDeleteConfirm)
        assertTrue(slow.state.value.deleting)
        slow.onAction(TodayAction.OnDeleteAfterRevealClick)
        assertFalse(slow.state.value.confirmingDelete)
        assertEquals(TodayStatus.Revealed, slow.state.value.status)
    }

    @Test
    fun cancellingTheDeleteClosesTheDialogAndDeletesNothing() = runTest {
        loop.setStatus(TodayStatus.Revealed)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
        viewModel.onAction(TodayAction.OnDeleteAfterRevealClick)
        viewModel.onAction(TodayAction.OnDeleteDismiss)
        assertFalse(viewModel.state.value.confirmingDelete)
        assertFalse(viewModel.state.value.deleting)
        assertEquals(TodayStatus.Revealed, viewModel.state.value.status)
    }

    @Test
    fun aDeleteQuestionLeftOpenDoesNotReturnWithTheNextRevealedDay() = runTest {
        loop.setStatus(TodayStatus.Revealed)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
        viewModel.onAction(TodayAction.OnDeleteAfterRevealClick)
        viewModel.onAction(TodayAction.OnDebugCycle)
        loop.setStatus(TodayStatus.Revealed)
        assertFalse(viewModel.state.value.confirmingDelete)
    }

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
    fun aSoloDayShowsTheDayBefore() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
        val today = viewModel.state.value.date
        loop.setStatus(TodayStatus.SoloYouWrote)
        assertEquals(false, viewModel.state.value.date == today)
    }

    @Test
    fun thePartnersWordsAreHeldOnlyWhenTheDayIsOpenOrSolo() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
        // A screen cannot leak what its state does not hold (states.md §3).
        for (status in listOf(TodayStatus.NotWritten, TodayStatus.Waiting, TodayStatus.Queued)) {
            loop.setStatus(status)
            assertNull(viewModel.state.value.partnerEntry, "partnerEntry must be null in $status")
        }
        for (status in listOf(TodayStatus.PartnerNotJoined, TodayStatus.SoloYouWrote)) {
            loop.setStatus(status)
            assertNull(viewModel.state.value.partnerEntry, "partnerEntry must be null in $status")
        }
        for (status in listOf(TodayStatus.Revealed, TodayStatus.Tombstone, TodayStatus.SoloTheyWrote)) {
            loop.setStatus(status)
            assertNotNull(viewModel.state.value.partnerEntry, "partnerEntry must be set in $status")
        }
    }
}
