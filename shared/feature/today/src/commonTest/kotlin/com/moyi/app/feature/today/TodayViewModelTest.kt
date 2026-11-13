package com.moyi.app.feature.today

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.cash.turbine.test
import com.moyi.app.core.ApiErrorCode
import com.moyi.app.core.ApiProblem
import com.moyi.app.core.DataError
import com.moyi.app.core.EmptyResult
import com.moyi.app.core.Result
import com.moyi.app.domain.bond.Bond
import com.moyi.app.domain.bond.BondRepository
import com.moyi.app.domain.bond.BondStatus
import com.moyi.app.domain.bond.BondType
import com.moyi.app.domain.bond.Invite
import com.moyi.app.domain.bond.InvitePreview
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
import kotlin.test.assertIs
import kotlin.test.assertNull
import com.moyi.app.domain.today.OutboxRefusal
import com.moyi.app.feature.today.common.QueuedDay
import kotlinx.datetime.LocalDate
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModelTest : MainDispatcherTest() {
    private val loop = FakeDailyLoop()
    private val outbox = FakeOutbox()
    private val ledger = RevealLedger { "me" }
    private val bonds = InviteBonds()
    private val viewModel by lazy { TodayViewModel(loop, ledger, bonds, outbox, FakeNotificationAskMemory()) }

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

    // ---- The outbox ----

    @Test
    fun aQueuedEntryIsTodaysQueuedLayoutWithTheDayItCountsFor() = runTest {
        loop.onRefresh = DailyLoopState.Ready(
            dayView(TodayStatus.Queued).copy(queuedDay = QueuedDay.EarlierDay(LocalDate(2026, 9, 13))),
        )
        collectState()
        viewModel.onAction(TodayAction.OnForeground)
        assertEquals(TodayStatus.Queued, viewModel.state.value.status)
        assertEquals(MINE, viewModel.state.value.myEntry)
        assertEquals(QueuedDay.EarlierDay(LocalDate(2026, 9, 13)), viewModel.state.value.queuedDay)
        assertNull(viewModel.state.value.partnerEntry)
    }

    @Test
    fun everyForegroundAndTryAgainAlsoTriesTheQueueAgain() = runTest {
        collectState()
        viewModel.onAction(TodayAction.OnForeground)
        viewModel.onAction(TodayAction.OnRetryClick)
        assertEquals(2, outbox.retries)
    }

    @Test
    fun anEntrySentOrRefusedBehindTodayReloadsTheDay() = runTest {
        collectState()
        val before = loop.refreshes
        outbox.changeFlow.emit(Unit)
        assertEquals(before + 1, loop.refreshes, "the server wins: Today shows what it holds now")
    }

    @Test
    fun aRefusalIsSaidOnTodayAndOpeningWriteDismissesIt() = runTest {
        loop.onRefresh = DailyLoopState.Ready(dayView(TodayStatus.NotWritten).copy(refusal = OutboxRefusal.NotAccepted))
        collectState()
        viewModel.onAction(TodayAction.OnForeground)
        assertEquals(OutboxRefusal.NotAccepted, viewModel.state.value.refusal)

        viewModel.onAction(TodayAction.OnWriteClick)
        assertEquals(1, loop.refusalDismissals)
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
    fun theCalendarTargetOpensTheArchive() = runTest {
        loop.show(dayView(TodayStatus.NotWritten))
        collectState()
        viewModel.events.test {
            viewModel.onAction(TodayAction.OnArchiveClick)
            assertEquals(TodayEvent.NavigateToArchive, awaitItem())
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
    fun tappingDeleteBeforeTheRevealAsksFirstAndDeletesNothing() = runTest {
        // Owner ruling 2026-11-09: deleting before the reveal asks too (states.md §4 said not).
        collectState()
        loop.show(dayView(TodayStatus.Waiting))
        viewModel.events.test {
            viewModel.onAction(TodayAction.OnDeleteBeforeRevealClick)
            expectNoEvents()
        }
        assertTrue(viewModel.state.value.confirmingDeleteBeforeReveal)
        // The after-reveal question, with its other line, is not the one asked.
        assertFalse(viewModel.state.value.confirmingDelete)
        assertEquals(0, loop.deletesBefore)
        assertEquals(TodayStatus.Waiting, viewModel.state.value.status)
    }

    @Test
    fun dismissingTheDeleteBeforeTheRevealDeletesNothing() = runTest {
        collectState()
        loop.show(dayView(TodayStatus.Waiting))
        viewModel.onAction(TodayAction.OnDeleteBeforeRevealClick)
        viewModel.onAction(TodayAction.OnDeleteDismiss)
        assertFalse(viewModel.state.value.confirmingDeleteBeforeReveal)
        assertEquals(0, loop.deletesBefore)
        assertEquals(0, loop.deletesAfter)
        assertEquals(TodayStatus.Waiting, viewModel.state.value.status)
    }

    @Test
    fun confirmingTheDeleteBeforeTheRevealSendsItOnceAndClosesTheDialog() = runTest {
        collectState()
        loop.show(dayView(TodayStatus.Waiting))
        val gate = CompletableDeferred<Unit>()
        loop.gate = gate
        viewModel.onAction(TodayAction.OnDeleteBeforeRevealClick)
        viewModel.onAction(TodayAction.OnDeleteConfirm)
        assertFalse(viewModel.state.value.confirmingDeleteBeforeReveal)
        assertTrue(viewModel.state.value.deleting)

        // A second answer, or a second tap while it is on its way, sends nothing more.
        viewModel.onAction(TodayAction.OnDeleteConfirm)
        viewModel.onAction(TodayAction.OnDeleteBeforeRevealClick)
        assertFalse(viewModel.state.value.confirmingDeleteBeforeReveal)

        gate.complete(Unit)
        assertEquals(1, loop.deletesBefore)
        assertEquals(0, loop.deletesAfter)
        assertFalse(viewModel.state.value.deleting)
        assertFalse(viewModel.state.value.actionFailed)
    }

    @Test
    fun aDeleteBeforeTheRevealIsNotAskedOverADayThatRevealedMeanwhile() = runTest {
        collectState()
        loop.show(dayView(TodayStatus.Waiting))
        viewModel.onAction(TodayAction.OnDeleteBeforeRevealClick)
        // The partner wrote and the day opened: "you can write again today" is no longer true.
        loop.show(dayView(TodayStatus.Revealed))
        assertFalse(viewModel.state.value.confirmingDeleteBeforeReveal)
        assertFalse(viewModel.state.value.confirmingDelete)
    }

    @Test
    fun aFailedDeleteBeforeTheRevealSaysSoAndAGoneOneDoesNot() = runTest {
        collectState()
        loop.show(dayView(TodayStatus.Waiting))
        loop.deleteBeforeResult = DeleteResult.Failed
        viewModel.onAction(TodayAction.OnDeleteBeforeRevealClick)
        viewModel.onAction(TodayAction.OnDeleteConfirm)
        assertTrue(viewModel.state.value.actionFailed)

        // Already gone (404) is not a failure: Today has caught up.
        loop.deleteBeforeResult = DeleteResult.Gone
        viewModel.onAction(TodayAction.OnDeleteBeforeRevealClick)
        viewModel.onAction(TodayAction.OnDeleteConfirm)
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
        val factory = viewModelFactory { initializer { TodayViewModel(loop, ledger, bonds, outbox, FakeNotificationAskMemory()) } }
        ViewModelProvider.create(store, factory)[TodayViewModel::class]
        loop.show(dayView(TodayStatus.Revealed))
        val clearsBefore = loop.clears

        store.clear()

        assertEquals(clearsBefore + 1, loop.clears)
        assertEquals(DailyLoopState.Loading, loop.state.value)
    }

    @Test
    fun theTodayUnderAJoinGoingDoesNotForgetTheJoinedBondsDay() {
        // Joining from Today: a new Today is put in place of the one under the join flow, and
        // Navigation clears the old one's ViewModel only after the new one has started. The
        // smoke test of PR 101 saw the bare surface stay until the next foreground.
        val factory = viewModelFactory { initializer { TodayViewModel(loop, ledger, bonds, outbox, FakeNotificationAskMemory()) } }
        val under = ViewModelStore()
        ViewModelProvider.create(under, factory)[TodayViewModel::class]
        val joined = ViewModelStore()
        ViewModelProvider.create(joined, factory)[TodayViewModel::class]
        loop.show(dayView(TodayStatus.NotWritten))

        under.clear()

        assertIs<DailyLoopState.Ready>(loop.state.value, "the joined bond's day must stay drawn")
        joined.clear()
        assertEquals(DailyLoopState.Loading, loop.state.value, "the last Today going still forgets the day")
    }

    // ---- Share the invite again (states.md §8) ----

    @Test
    fun shareInviteAgainSharesTheLiveInviteWithoutIssuingANewOne() = runTest {
        bonds.list = Result.Success(listOf(bond(invite = LIVE)))
        collectState()
        loop.show(dayView(TodayStatus.PartnerNotJoined))
        viewModel.events.test {
            viewModel.onAction(TodayAction.OnShareInviteClick)
            assertEquals(TodayEvent.ShareInvite(LIVE.link, LIVE.code), awaitItem())
        }
        assertEquals(0, bonds.created, "issuing one would revoke the code the partner may already hold")
    }

    @Test
    fun shareInviteAgainIssuesOneOnlyWhenTheBondHasNone() = runTest {
        bonds.list = Result.Success(listOf(bond(invite = null)))
        collectState()
        loop.show(dayView(TodayStatus.PartnerNotJoined))
        viewModel.events.test {
            viewModel.onAction(TodayAction.OnShareInviteClick)
            assertEquals(TodayEvent.ShareInvite(NEW.link, NEW.code), awaitItem())
        }
        assertEquals(listOf("bond-1"), bonds.createdFor)
    }

    @Test
    fun shareInviteAgainIssuesNothingWhenTheBondsCannotBeRead() = runTest {
        bonds.list = Result.Failure(DataError.Network.NoInternet)
        collectState()
        loop.show(dayView(TodayStatus.PartnerNotJoined))
        viewModel.onAction(TodayAction.OnShareInviteClick)
        assertEquals(0, bonds.created)
        assertTrue(viewModel.state.value.actionFailed)
    }

    @Test
    fun aBondThatFilledMeanwhileRefreshesTodayInsteadOfSharing() = runTest {
        bonds.list = Result.Success(listOf(bond(invite = null)))
        bonds.create = Result.Failure(DataError.Network.Api(ApiProblem(409, ApiErrorCode.BOND_FULL)))
        collectState()
        loop.show(dayView(TodayStatus.PartnerNotJoined))
        val before = loop.refreshes
        viewModel.events.test {
            viewModel.onAction(TodayAction.OnShareInviteClick)
            expectNoEvents()
        }
        assertEquals(before + 1, loop.refreshes)
        assertFalse(viewModel.state.value.actionFailed)
    }
}

private val LIVE = Invite("i1", "K7Q2PD", "https://getmoyi.app/i/K7Q2PD")
private val NEW = Invite("i2", "M4XR9T", "https://getmoyi.app/i/M4XR9T")

private fun bond(invite: Invite?) = Bond("bond-1", "Amara and Tunde", BondType.COUPLE, BondStatus.PENDING_MEMBER, invite)

/** The bonds Today reads for "Share the invite again", and the invites it issues. */
internal class InviteBonds : BondRepository {
    var list: Result<List<Bond>, DataError.Network> = Result.Success(emptyList())
    var create: Result<Invite, DataError.Network> = Result.Success(NEW)
    val createdFor = mutableListOf<String>()
    val created get() = createdFor.size
    override suspend fun bonds() = list
    override suspend fun createInvite(bondId: String): Result<Invite, DataError.Network> {
        createdFor += bondId
        return create
    }
    override suspend fun createBond(name: String, type: BondType, anchorTimezone: String): Result<Bond, DataError.Network> = error("not used")
    override suspend fun revokeInvite(bondId: String, inviteId: String): EmptyResult<DataError.Network> = error("not used")
    override suspend fun resolveInvite(code: String): Result<InvitePreview, DataError.Network> = error("not used")
    override suspend fun acceptInvite(code: String): Result<Bond, DataError.Network> = error("not used")
}
