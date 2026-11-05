package com.moyi.app.feature.today

import com.moyi.app.core.DataError
import com.moyi.app.core.Result
import com.moyi.app.domain.archive.ArchiveDay
import com.moyi.app.domain.archive.ArchivePage
import com.moyi.app.domain.today.BondDayStatus
import com.moyi.app.domain.today.EntryStatus
import com.moyi.app.domain.today.MyEntry
import com.moyi.app.domain.today.PartnerEntry
import com.moyi.app.feature.today.archive.ArchiveAction
import com.moyi.app.feature.today.archive.ArchiveDayAction
import com.moyi.app.feature.today.archive.ArchiveDayUi
import com.moyi.app.feature.today.archive.ArchiveDayViewModel
import com.moyi.app.feature.today.archive.ArchiveFeed
import com.moyi.app.feature.today.archive.ArchiveFilter
import com.moyi.app.feature.today.archive.ArchiveLoad
import com.moyi.app.feature.today.archive.ArchiveViewModel
import com.moyi.app.feature.today.archive.FavouriteTarget
import com.moyi.app.feature.today.common.DailyLoopState
import com.moyi.app.feature.today.common.TodayStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Favourites (states.md §6) over fakes: the optimistic toggle and its revert, repeat
 * taps, the toggle absent before the reveal, the Favourites filter paging until the
 * cursor is null, its empty state, the list following the day view, and the rule that
 * matters most: nothing in state can say the partner favourited anything.
 */
class ArchiveFavouritesTest : MainDispatcherTest() {
    private val loop = FakeDailyLoop(DailyLoopState.Ready(dayView(TodayStatus.Revealed)))
    private val repository = FakeArchiveRepository()
    private val entries = FakeEntries()
    private val feed = ArchiveFeed(loop, repository)

    private fun TestScope.list(): ArchiveViewModel = ArchiveViewModel(feed).also { viewModel ->
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
    }

    private fun dayView(date: String) = ArchiveDayViewModel(date, feed, repository, entries, loop)

    private fun ArchiveViewModel.days(): List<ArchiveDayUi> = state.value.months.flatMap { it.days }

    private fun markedTheirs(date: String) = revealedDay(date).copy(markedByMe = setOf("theirs-$date"))

    // ---- The toggle ----

    @Test
    fun theToggleIsOptimisticAndTheListFollowsIt() = runTest {
        repository.pages[null] = ArchivePage(listOf(revealedDay("2026-09-02")), null)
        val list = list()
        val day = dayView("2026-09-02")
        assertEquals(false, day.state.value.day?.myMarkOnTheirs)
        assertFalse(list.days().single().holdsMyFavourite)
        val gate = CompletableDeferred<Unit>().also { repository.markGate = it }

        day.onAction(ArchiveDayAction.OnFavouriteClick(FavouriteTarget.Theirs))

        // Marked before the server has answered, here and on the day card.
        assertEquals(true, day.state.value.day?.myMarkOnTheirs)
        assertEquals(false, day.state.value.day?.myMarkOnMine)
        assertTrue(list.days().single().holdsMyFavourite)
        assertEquals(listOf("theirs-2026-09-02" to true), repository.marks)
        gate.complete(Unit)
        assertEquals(true, day.state.value.day?.myMarkOnTheirs)
        assertNull(day.state.value.markFailed)
    }

    @Test
    fun aFailedToggleRevertsAndSaysSoUnderThatEntry() = runTest {
        repository.pages[null] = ArchivePage(listOf(revealedDay("2026-09-02")), null)
        val list = list()
        val day = dayView("2026-09-02")
        val gate = CompletableDeferred<Unit>().also { repository.markGate = it }
        repository.markResult = Result.Failure(DataError.Network.NoInternet)

        day.onAction(ArchiveDayAction.OnFavouriteClick(FavouriteTarget.Mine))
        assertEquals(true, day.state.value.day?.myMarkOnMine)
        gate.complete(Unit)

        assertEquals(false, day.state.value.day?.myMarkOnMine)
        assertEquals(FavouriteTarget.Mine, day.state.value.markFailed)
        assertFalse(list.days().single().holdsMyFavourite)
    }

    @Test
    fun aTapWhileTheCallIsInFlightIsIgnored() = runTest {
        repository.pages[null] = ArchivePage(listOf(revealedDay("2026-09-02")), null)
        list()
        val day = dayView("2026-09-02")
        val gate = CompletableDeferred<Unit>().also { repository.markGate = it }

        day.onAction(ArchiveDayAction.OnFavouriteClick(FavouriteTarget.Mine))
        day.onAction(ArchiveDayAction.OnFavouriteClick(FavouriteTarget.Mine))
        day.onAction(ArchiveDayAction.OnFavouriteClick(FavouriteTarget.Mine))

        assertEquals(listOf("mine-2026-09-02" to true), repository.marks)
        assertEquals(true, day.state.value.day?.myMarkOnMine)
        gate.complete(Unit)
        // Answered: the next tap is a real one, and unmarks.
        day.onAction(ArchiveDayAction.OnFavouriteClick(FavouriteTarget.Mine))
        assertEquals(listOf("mine-2026-09-02" to true, "mine-2026-09-02" to false), repository.marks)
        assertEquals(false, day.state.value.day?.myMarkOnMine)
    }

    @Test
    fun theToggleIsAbsentBeforeTheRevealAndOnATombstone() = runTest {
        repository.pages[null] = ArchivePage(
            listOf(
                // Both written, not yet revealed: mine is live, theirs locked.
                ArchiveDay("2026-09-03", BondDayStatus.PENDING_REVEAL, MyEntry("m3", MINE, EntryStatus.SUBMITTED), PartnerEntry.Locked),
                // A solo day: their words are readable but the entry was never revealed.
                ArchiveDay("2026-09-02", BondDayStatus.SOLO, null, PartnerEntry.Submitted("t2", THEIRS)),
                // My tombstone beside their revealed entry.
                ArchiveDay("2026-09-01", BondDayStatus.REVEALED, MyEntry("m1", null, EntryStatus.DELETED), PartnerEntry.Revealed("t1", THEIRS)),
            ),
            null,
        )
        val days = list().days()

        assertNull(days[0].myMarkOnMine)
        assertNull(days[0].myMarkOnTheirs)
        assertNull(days[1].myMarkOnTheirs)
        assertNull(days[2].myMarkOnMine)
        assertEquals(false, days[2].myMarkOnTheirs)

        val pending = dayView("2026-09-03")
        pending.onAction(ArchiveDayAction.OnFavouriteClick(FavouriteTarget.Mine))
        pending.onAction(ArchiveDayAction.OnFavouriteClick(FavouriteTarget.Theirs))
        dayView("2026-09-02").onAction(ArchiveDayAction.OnFavouriteClick(FavouriteTarget.Theirs))
        assertTrue(repository.marks.isEmpty())
    }

    // ---- The Favourites filter ----

    @Test
    fun favouritesPagesUntilTheCursorIsNullPastAnEmptyPage() = runTest {
        repository.pages[null] = ArchivePage(listOf(revealedDay("2026-09-02")), null)
        repository.favouritePages[null] = ArchivePage(emptyList(), "c1")
        repository.favouritePages["c1"] = ArchivePage(listOf(markedTheirs("2026-08-20")), "c2")
        repository.favouritePages["c2"] = ArchivePage(emptyList(), "c3")
        repository.favouritePages["c3"] = ArchivePage(listOf(markedTheirs("2026-07-04")), null)
        val list = list()

        list.onAction(ArchiveAction.OnFilterClick(ArchiveFilter.Favourites))

        // The empty first page is not the empty state: the next one was asked for at once.
        assertEquals(ArchiveLoad.Ready, list.state.value.load)
        assertEquals(ArchiveFilter.Favourites, list.state.value.filter)
        assertEquals(listOf("2026-08-20"), list.days().map { it.date.toString() })
        assertTrue(list.state.value.canLoadMore)

        list.onAction(ArchiveAction.OnLoadMore)

        // An empty page with a cursor under a list: still more to come, and the page count
        // moved on, so the screen asks again (ArchiveScreen's LaunchedEffect keyed on it).
        assertEquals(listOf("2026-08-20"), list.days().map { it.date.toString() })
        assertTrue(list.state.value.canLoadMore)
        assertEquals(3, list.state.value.pages)
        list.onAction(ArchiveAction.OnLoadMore)

        assertEquals(listOf("2026-08-20", "2026-07-04"), list.days().map { it.date.toString() })
        assertFalse(list.state.value.canLoadMore)
        assertEquals(listOf<String?>(null, "c1", "c2", "c3"), repository.requests.filter { it.second }.map { it.first })
        assertEquals(listOf<Pair<String?, Boolean>>(null to false), repository.requests.filterNot { it.second })
    }

    @Test
    fun noFavouritesIsTheFavouritesEmptyStateWithTheChipsStillThere() = runTest {
        repository.pages[null] = ArchivePage(listOf(revealedDay("2026-09-02")), null)
        repository.favouritePages[null] = ArchivePage(emptyList(), null)
        val list = list()
        assertTrue(list.state.value.showFilters)

        list.onAction(ArchiveAction.OnFilterClick(ArchiveFilter.Favourites))

        assertTrue(list.state.value.isEmpty)
        assertEquals(ArchiveFilter.Favourites, list.state.value.filter)
        assertTrue(list.state.value.showFilters)
    }

    @Test
    fun anEmptyArchiveHasNoChips() = runTest {
        repository.pages[null] = ArchivePage(emptyList(), null)
        val state = list().state.value
        assertTrue(state.isEmpty)
        assertFalse(state.showFilters)
    }

    @Test
    fun allDaysAsksAgainWithoutTheFilter() = runTest {
        repository.pages[null] = ArchivePage(listOf(revealedDay("2026-09-02"), revealedDay("2026-09-01")), null)
        repository.favouritePages[null] = ArchivePage(listOf(markedTheirs("2026-09-01")), null)
        val list = list()
        list.onAction(ArchiveAction.OnFilterClick(ArchiveFilter.Favourites))
        assertEquals(1, list.days().size)

        list.onAction(ArchiveAction.OnFilterClick(ArchiveFilter.AllDays))

        assertEquals(ArchiveFilter.AllDays, list.state.value.filter)
        assertEquals(2, list.days().size)
        assertEquals(null to false, repository.requests.last())
    }

    @Test
    fun removingADaysLastFavouriteDropsItFromFavouritesAndMarkingItBringsItBack() = runTest {
        repository.pages[null] = ArchivePage(listOf(revealedDay("2026-09-02")), null)
        repository.favouritePages[null] = ArchivePage(listOf(markedTheirs("2026-09-01")), null)
        val list = list()
        list.onAction(ArchiveAction.OnFilterClick(ArchiveFilter.Favourites))
        val day = dayView("2026-09-01")
        assertEquals(true, day.state.value.day?.myMarkOnTheirs)

        day.onAction(ArchiveDayAction.OnFavouriteClick(FavouriteTarget.Theirs))

        // The day view keeps the day (you are still reading it); the list behind it drops it.
        assertEquals(false, day.state.value.day?.myMarkOnTheirs)
        assertTrue(list.state.value.isEmpty)

        day.onAction(ArchiveDayAction.OnFavouriteClick(FavouriteTarget.Theirs))
        assertEquals(listOf("2026-09-01"), list.days().map { it.date.toString() })
    }

    @Test
    fun unmarkingTheLastVisibleFavouriteAsksForTheNextPage() = runTest {
        repository.pages[null] = ArchivePage(emptyList(), null)
        repository.favouritePages[null] = ArchivePage(listOf(markedTheirs("2026-09-01")), "c1")
        repository.favouritePages["c1"] = ArchivePage(listOf(markedTheirs("2026-08-01")), null)
        val list = list()
        list.onAction(ArchiveAction.OnFilterClick(ArchiveFilter.Favourites))

        dayView("2026-09-01").onAction(ArchiveDayAction.OnFavouriteClick(FavouriteTarget.Theirs))

        assertEquals(listOf("2026-08-01"), list.days().map { it.date.toString() })
    }

    // ---- Privacy ----

    @Test
    fun nothingInStateCanExpressThePartnersFavourite() = runTest {
        // markedByMe is the only input any mark is drawn from, and it holds only the
        // caller's own marks (the mapper reads `favourited`, the caller's alone). With it
        // empty, nothing anywhere is marked, whoever else may have kept these entries.
        repository.pages[null] = ArchivePage(listOf(revealedDay("2026-09-02"), revealedDay("2026-09-01").copy(markedByMe = setOf("someone-else"))), null)
        val list = list()
        val day = dayView("2026-09-02")

        list.days().forEach { ui ->
            assertFalse(ui.holdsMyFavourite)
            assertEquals(false, ui.myMarkOnMine)
            assertEquals(false, ui.myMarkOnTheirs)
        }
        assertEquals(false, day.state.value.day?.myMarkOnMine)
        assertEquals(false, day.state.value.day?.myMarkOnTheirs)
        // And the state has no other favourite-shaped field to fill: its every mark is "my mark".
        val fields = list.state.value.toString() + day.state.value.toString()
        val favouriteWords = Regex("[A-Za-z]*(?:[Mm]ark|[Ff]avourit)[A-Za-z]*").findAll(fields).map { it.value }.toSet()
        assertEquals(setOf("myMarkOnMine", "myMarkOnTheirs", "markFailed"), favouriteWords)
    }
}
