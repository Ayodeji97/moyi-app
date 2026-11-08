package com.moyi.app.feature.today

import com.moyi.app.core.Result
import com.moyi.app.domain.archive.ArchiveDay
import com.moyi.app.domain.archive.ArchivePage
import com.moyi.app.domain.today.BondDayStatus
import com.moyi.app.domain.today.EntryStatus
import com.moyi.app.domain.today.MyEntry
import com.moyi.app.domain.today.PartnerEntry
import com.moyi.app.feature.today.archive.ArchiveAction
import com.moyi.app.feature.today.archive.ArchiveDayAction
import com.moyi.app.feature.today.archive.ArchiveDayViewModel
import com.moyi.app.feature.today.archive.ArchiveFeed
import com.moyi.app.feature.today.archive.ArchiveLoad
import com.moyi.app.feature.today.archive.ArchiveViewModel
import com.moyi.app.feature.today.common.DailyLoopState
import com.moyi.app.feature.today.common.TodayStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Archive list and day view over fakes: the first load, the empty state, a
 * failure and its retry, paging by cursor, the day view, delete after the reveal,
 * and the rule that matters most: the partner's words are never held for a locked entry.
 */
class ArchiveViewModelTest : MainDispatcherTest() {
    private val loop = FakeDailyLoop(DailyLoopState.Ready(dayView(TodayStatus.Revealed)))
    private val repository = FakeArchiveRepository()
    private val entries = FakeEntries()
    private val cache = FakeArchiveCache()
    private val feed = ArchiveFeed(loop, repository, cache)

    /** The list ViewModel, with its state collected (it is shared WhileSubscribed, as the screen would). */
    private fun TestScope.list(): ArchiveViewModel = ArchiveViewModel(feed).also { viewModel ->
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
    }

    private fun dayView(date: String) = ArchiveDayViewModel(date, feed, repository, entries, loop)

    // ---- The list ----

    @Test
    fun theFirstPageIsShownNewestFirstGroupedByMonth() = runTest {
        repository.pages[null] = ArchivePage(listOf(revealedDay("2026-09-02"), revealedDay("2026-09-01"), revealedDay("2026-08-31")), null)

        val state = list().state.value

        assertEquals(ArchiveLoad.Ready, state.load)
        assertEquals(listOf(LocalDate(2026, 9, 2), LocalDate(2026, 8, 31)), state.months.map { it.month })
        assertEquals(2, state.months.first().days.size)
        assertFalse(state.canLoadMore)
        assertEquals(PARTNER, state.partnerName)
    }

    @Test
    fun noDaysAndNoCursorIsTheEmptyState() = runTest {
        repository.pages[null] = ArchivePage(emptyList(), null)
        assertTrue(list().state.value.isEmpty)
    }

    @Test
    fun aFailedFirstLoadIsTheErrorAndTryAgainLoadsIt() = runTest {
        repository.failNext = true
        repository.pages[null] = ArchivePage(listOf(revealedDay("2026-09-02")), null)
        val viewModel = list()
        assertEquals(ArchiveLoad.Failed, viewModel.state.value.load)

        viewModel.onAction(ArchiveAction.OnRetryClick)

        assertEquals(ArchiveLoad.Ready, viewModel.state.value.load)
        assertEquals(1, viewModel.state.value.months.single().days.size)
    }

    @Test
    fun withNoDayOnTodayTheFirstLoadFailsRatherThanGuessAtABond() = runTest {
        loop.state.value = DailyLoopState.Loading
        assertEquals(ArchiveLoad.Failed, list().state.value.load)
        assertTrue(repository.cursors.isEmpty())
    }

    @Test
    fun theNextPageIsAskedWithTheCursorAndAppended() = runTest {
        repository.pages[null] = ArchivePage(listOf(revealedDay("2026-09-02")), "c1")
        repository.pages["c1"] = ArchivePage(listOf(revealedDay("2026-09-01")), null)
        val viewModel = list()
        assertTrue(viewModel.state.value.canLoadMore)

        viewModel.onAction(ArchiveAction.OnLoadMore)

        assertEquals(listOf(null, "c1"), repository.cursors)
        assertEquals(2, viewModel.state.value.months.single().days.size)
        assertFalse(viewModel.state.value.canLoadMore)
    }

    @Test
    fun aNullCursorStopsPaging() = runTest {
        repository.pages[null] = ArchivePage(listOf(revealedDay("2026-09-02")), null)
        val viewModel = list()

        viewModel.onAction(ArchiveAction.OnLoadMore)

        assertEquals(listOf<String?>(null), repository.cursors)
    }

    @Test
    fun aShortPageWithACursorIsNotTheEnd() = runTest {
        // Shorter than the limit, and nothing in it to draw, yet the cursor says there is more.
        repository.pages[null] = ArchivePage(emptyList(), "c1")
        repository.pages["c1"] = ArchivePage(listOf(revealedDay("2026-09-01")), null)

        val state = list().state.value

        assertFalse(state.isEmpty)
        assertEquals(listOf(null, "c1"), repository.cursors)
        assertEquals(1, state.months.single().days.size)
    }

    @Test
    fun aFailedNextPageKeepsTheListAndTryAgainAsksForTheSamePage() = runTest {
        repository.pages[null] = ArchivePage(listOf(revealedDay("2026-09-02")), "c1")
        repository.pages["c1"] = ArchivePage(listOf(revealedDay("2026-09-01")), null)
        val viewModel = list()
        repository.failNext = true

        viewModel.onAction(ArchiveAction.OnLoadMore)

        assertEquals(ArchiveLoad.Ready, viewModel.state.value.load)
        assertTrue(viewModel.state.value.pageFailed)
        assertFalse(viewModel.state.value.canLoadMore)
        assertEquals(1, viewModel.state.value.months.single().days.size)

        viewModel.onAction(ArchiveAction.OnRetryPageClick)

        assertEquals(listOf(null, "c1", "c1"), repository.cursors)
        assertFalse(viewModel.state.value.pageFailed)
        assertEquals(2, viewModel.state.value.months.single().days.size)
    }

    @Test
    fun aDayIsDrawnFromItsEntriesNotItsStatus() = runTest {
        repository.pages[null] = ArchivePage(
            listOf(
                // EMPTY, yet mine is there as a tombstone: drawn.
                ArchiveDay("2026-09-02", BondDayStatus.EMPTY, MyEntry("m1", null, EntryStatus.DELETED), null),
                // Nothing of mine and nothing of theirs to show: not drawn.
                ArchiveDay("2026-09-01", BondDayStatus.REVEALED, null, PartnerEntry.Deleted("t1")),
            ),
            null,
        )

        val day = list().state.value.months.single().days.single()

        assertEquals(LocalDate(2026, 9, 2), day.date)
        assertTrue(day.myTombstone)
        assertNull(day.myEntry)
        assertFalse(day.canDelete)
    }

    @Test
    fun thePartnersWordsAreNeverHeldForALockedEntryNorBeforeTheReveal() = runTest {
        repository.pages[null] = ArchivePage(
            listOf(
                ArchiveDay("2026-09-03", BondDayStatus.PARTIAL, MyEntry("m3", MINE, EntryStatus.SUBMITTED), PartnerEntry.Locked),
                // Even if a body ever carried the words on a day that has not opened, they are not held.
                ArchiveDay("2026-09-02", BondDayStatus.PENDING_REVEAL, MyEntry("m2", MINE, EntryStatus.SUBMITTED), PartnerEntry.Submitted("t2", THEIRS)),
                ArchiveDay("2026-09-01", BondDayStatus.SOLO, null, PartnerEntry.Submitted("t1", THEIRS)),
            ),
            null,
        )

        val days = list().state.value.months.single().days

        assertEquals(listOf(null, null, THEIRS), days.map { it.partnerEntry })
        assertEquals(listOf(MINE, MINE, null), days.map { it.myEntry })
        assertTrue(days.none { it.canDelete })
    }

    @Test
    fun aDayOpensItsDayViewAndBackGoesBack() = runTest {
        repository.pages[null] = ArchivePage(listOf(revealedDay("2026-09-02")), null)
        val viewModel = list()

        viewModel.onAction(ArchiveAction.OnDayClick(LocalDate(2026, 9, 2)))
        assertEquals(com.moyi.app.feature.today.archive.ArchiveEvent.NavigateToDay(LocalDate(2026, 9, 2)), viewModel.events.first())
        viewModel.onAction(ArchiveAction.OnBackClick)
        assertEquals(com.moyi.app.feature.today.archive.ArchiveEvent.NavigateBack, viewModel.events.first())
    }

    // ---- The day view ----

    @Test
    fun theDayViewShowsTheDayTheListLoadedAtOnceAndThenAsksTheServer() = runTest {
        repository.pages[null] = ArchivePage(listOf(revealedDay("2026-09-02")), null)
        list()

        val state = dayView("2026-09-02").state.value

        assertEquals(ArchiveLoad.Ready, state.load)
        assertEquals(MINE, state.day?.myEntry)
        assertEquals(THEIRS, state.day?.partnerEntry)
        assertTrue(state.day?.canDelete == true)
        assertEquals(listOf("2026-09-02"), repository.dayCalls, "cache-then-network: the server's answer replaces it")
    }

    @Test
    fun theDayViewLoadsADayTheListDoesNotHold() = runTest {
        repository.dayAnswer = Result.Success(revealedDay("2026-08-01"))

        val state = dayView("2026-08-01").state.value

        assertEquals(listOf("2026-08-01"), repository.dayCalls)
        assertEquals(ArchiveLoad.Ready, state.load)
        assertEquals(LocalDate(2026, 8, 1), state.date)
    }

    @Test
    fun aDayThatDoesNotLoadIsTheErrorAndTryAgainAsksAgain() = runTest {
        val viewModel = dayView("2026-08-01")
        assertEquals(ArchiveLoad.Failed, viewModel.state.value.load)

        repository.dayAnswer = Result.Success(revealedDay("2026-08-01"))
        viewModel.onAction(ArchiveDayAction.OnRetryClick)

        assertEquals(ArchiveLoad.Ready, viewModel.state.value.load)
    }

    @Test
    fun deleteAfterTheRevealConfirmsThenLeavesTheTombstoneHereAndInTheList() = runTest {
        repository.pages[null] = ArchivePage(listOf(revealedDay("2026-09-02")), null)
        val listViewModel = list()
        val viewModel = dayView("2026-09-02")
        val gate = CompletableDeferred<Unit>().also { entries.gate = it }

        viewModel.onAction(ArchiveDayAction.OnDeleteClick)
        assertTrue(viewModel.state.value.confirmingDelete)
        assertTrue(entries.deletes.isEmpty())

        viewModel.onAction(ArchiveDayAction.OnDeleteConfirm)
        // Not optimistic: until the 204 the entry is still there, and the link is disabled.
        assertTrue(viewModel.state.value.deleting)
        assertEquals(MINE, viewModel.state.value.day?.myEntry)
        gate.complete(Unit)

        val day = viewModel.state.value.day!!
        assertEquals(listOf("mine-2026-09-02"), entries.deletes)
        assertNull(day.myEntry)
        assertTrue(day.myTombstone)
        assertFalse(day.canDelete)
        assertEquals(THEIRS, day.partnerEntry)
        assertFalse(viewModel.state.value.deleting)
        assertTrue(listViewModel.state.value.months.single().days.single().myTombstone)
    }

    @Test
    fun deletingTheDayTodayShowsRefreshesToday() = runTest {
        val today = DAY.toString()
        repository.pages[null] = ArchivePage(listOf(revealedDay(today)), null)
        list()
        val viewModel = dayView(today)

        viewModel.onAction(ArchiveDayAction.OnDeleteClick)
        viewModel.onAction(ArchiveDayAction.OnDeleteConfirm)

        assertEquals(1, loop.refreshes)
    }

    @Test
    fun aFailedDeleteKeepsTheEntryAndSaysSo() = runTest {
        repository.pages[null] = ArchivePage(listOf(revealedDay("2026-09-02")), null)
        list()
        val viewModel = dayView("2026-09-02")
        entries.deleteResult = Result.Failure(com.moyi.app.core.DataError.Network.NoInternet)

        viewModel.onAction(ArchiveDayAction.OnDeleteClick)
        viewModel.onAction(ArchiveDayAction.OnDeleteConfirm)

        assertEquals(MINE, viewModel.state.value.day?.myEntry)
        assertTrue(viewModel.state.value.actionFailed)
        assertTrue(viewModel.state.value.day?.canDelete == true)
        assertEquals(0, loop.refreshes)
    }

    @Test
    fun cancelLeavesEverythingAsItWas() = runTest {
        repository.pages[null] = ArchivePage(listOf(revealedDay("2026-09-02")), null)
        list()
        val viewModel = dayView("2026-09-02")

        viewModel.onAction(ArchiveDayAction.OnDeleteClick)
        viewModel.onAction(ArchiveDayAction.OnDeleteDismiss)

        assertFalse(viewModel.state.value.confirmingDelete)
        assertTrue(entries.deletes.isEmpty())
    }

    @Test
    fun aLiveEntryBeforeTheRevealHasNoDeleteHere() = runTest {
        repository.pages[null] = ArchivePage(
            listOf(ArchiveDay("2026-09-02", BondDayStatus.PARTIAL, MyEntry("m", MINE, EntryStatus.SUBMITTED), PartnerEntry.Locked)),
            null,
        )
        list()
        val viewModel = dayView("2026-09-02")

        viewModel.onAction(ArchiveDayAction.OnDeleteClick)
        viewModel.onAction(ArchiveDayAction.OnDeleteConfirm)

        assertFalse(viewModel.state.value.confirmingDelete)
        assertTrue(entries.deletes.isEmpty())
        assertNull(viewModel.state.value.day?.partnerEntry)
    }

    @Test
    fun noWordsReachToString() = runTest {
        repository.pages[null] = ArchivePage(listOf(revealedDay("2026-09-02")), null)
        val text = list().state.value.toString() + feed.state.value.toString()
        assertFalse(MINE in text || THEIRS in text, text)
    }
}
