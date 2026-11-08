package com.moyi.app.feature.today

import com.moyi.app.domain.archive.ArchivePage
import com.moyi.app.domain.archive.SEARCH_MAX_LENGTH
import com.moyi.app.feature.today.archive.ArchiveAction
import com.moyi.app.feature.today.archive.ArchiveDayViewModel
import com.moyi.app.feature.today.archive.ArchiveEvent
import com.moyi.app.feature.today.archive.ArchiveFeed
import com.moyi.app.feature.today.archive.ArchiveFilter
import com.moyi.app.feature.today.archive.ArchiveLoad
import com.moyi.app.feature.today.archive.ArchiveViewModel
import com.moyi.app.feature.today.archive.SEARCH_DEBOUNCE
import com.moyi.app.feature.today.common.DailyLoopState
import com.moyi.app.feature.today.common.TodayStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/**
 * Search (states.md §6, route 3; `GET /bonds/{bondId}/search`) over fakes: the debounce,
 * the contract's minimum, a stale answer never overwriting a newer one, paging by cursor,
 * no results keeping the query, the chips as the way back, the chip hidden over an empty
 * archive, and the query kept through the day view.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ArchiveSearchTest : MainDispatcherTest() {
    private val loop = FakeDailyLoop(DailyLoopState.Ready(dayView(TodayStatus.Revealed)))
    private val repository = FakeArchiveRepository()
    private val cache = FakeArchiveCache()
    private val feed = ArchiveFeed(loop, repository, cache)

    private fun TestScope.list(): ArchiveViewModel = ArchiveViewModel(feed).also { viewModel ->
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
    }

    private fun ArchiveViewModel.dates(): List<String> = state.value.months.flatMap { it.days }.map { it.date.toString() }

    private fun TestScope.searching(): ArchiveViewModel {
        repository.pages[null] = ArchivePage(listOf(revealedDay("2026-09-02"), revealedDay("2026-09-01"), revealedDay("2026-08-31")), null)
        return list().also { it.onAction(ArchiveAction.OnFilterClick(ArchiveFilter.Search)) }
    }

    private fun TestScope.type(list: ArchiveViewModel, query: String) {
        list.onAction(ArchiveAction.OnQueryChange(query))
        advanceTimeBy(SEARCH_DEBOUNCE + 1.milliseconds)
        runCurrent()
    }

    // ---- The field ----

    @Test
    fun choosingSearchShowsTheFieldFocusedOverTheUnfilteredList() = runTest {
        val list = searching()

        val state = list.state.value
        assertEquals(ArchiveFilter.Search, state.filter)
        assertTrue(state.showFilters, "the chips stay: they are the way back")
        assertTrue(state.focusSearch, "the field takes focus and raises the keyboard")
        assertEquals("", state.query)
        assertEquals(listOf("2026-09-02", "2026-09-01", "2026-08-31"), list.dates())
        assertEquals(emptyList(), repository.searches)
        // Asked once on the way in, not again for the empty field.
        assertEquals(listOf<Pair<String?, Boolean>>(null to false), repository.requests)

        list.onAction(ArchiveAction.OnSearchFocused)
        assertFalse(list.state.value.focusSearch, "focused once, not again when the screen comes back")
    }

    @Test
    fun theSearchChipIsHiddenWhenTheArchiveIsEmpty() = runTest {
        repository.pages[null] = ArchivePage(emptyList(), null)
        val empty = list()
        assertTrue(empty.state.value.isEmpty)
        assertFalse(empty.state.value.showSearch)
        assertFalse(empty.state.value.showFilters)

        feed.clear()
        repository.pages[null] = ArchivePage(listOf(revealedDay("2026-09-02")), null)
        val populated = list()
        assertTrue(populated.state.value.showSearch)
    }

    // ---- The query ----

    @Test
    fun theQueryIsSentOnceItRestsForTheDebounce() = runTest {
        val list = searching()

        list.onAction(ArchiveAction.OnQueryChange("t"))
        advanceTimeBy(100.milliseconds)
        list.onAction(ArchiveAction.OnQueryChange("te"))
        advanceTimeBy(100.milliseconds)
        list.onAction(ArchiveAction.OnQueryChange("tea"))
        advanceTimeBy(SEARCH_DEBOUNCE - 1.milliseconds)
        runCurrent()
        assertEquals(emptyList(), repository.searches, "nothing is sent while the field is still moving")

        advanceTimeBy(2.milliseconds)
        runCurrent()
        assertEquals(listOf<Pair<String, String?>>("tea" to null), repository.searches)

        // The same query again, after an edit that came back to it, is not sent twice.
        list.onAction(ArchiveAction.OnQueryChange("teas"))
        list.onAction(ArchiveAction.OnQueryChange("tea"))
        advanceTimeBy(SEARCH_DEBOUNCE * 2)
        runCurrent()
        assertEquals(1, repository.searches.size)
    }

    @Test
    fun belowTheMinimumTheUnfilteredListShowsAndNothingIsSent() = runTest {
        val list = searching()

        type(list, "   ")

        assertEquals(emptyList(), repository.searches, "q must hold a character once trimmed")
        assertEquals(listOf("2026-09-02", "2026-09-01", "2026-08-31"), list.dates())
        assertFalse(list.state.value.noResults)
    }

    @Test
    fun theQueryIsTrimmedAndCappedAtTheContractsLength() = runTest {
        val list = searching()

        type(list, "  tea  ")
        list.onAction(ArchiveAction.OnQueryChange("a".repeat(SEARCH_MAX_LENGTH + 20)))

        assertEquals("tea" to null, repository.searches.first())
        assertEquals(SEARCH_MAX_LENGTH, list.state.value.query.length)
    }

    @Test
    fun resultsAreOnlyWhatTheServerReturned() = runTest {
        repository.searchPages["tea" to null] = ArchivePage(listOf(revealedDay("2026-09-01")), null)
        val list = searching()

        type(list, "tea")

        assertEquals(ArchiveLoad.Ready, list.state.value.load)
        assertEquals(listOf("2026-09-01"), list.dates())
        assertEquals("tea", list.state.value.query)
    }

    @Test
    fun clearingTheFieldReturnsToTheUnfilteredList() = runTest {
        repository.searchPages["tea" to null] = ArchivePage(listOf(revealedDay("2026-09-01")), null)
        val list = searching()
        type(list, "tea")

        list.onAction(ArchiveAction.OnQueryChange(""))
        runCurrent()

        assertEquals(ArchiveFilter.Search, list.state.value.filter, "still in Search, its field empty")
        assertEquals(listOf("2026-09-02", "2026-09-01", "2026-08-31"), list.dates())
    }

    // ---- Stale answers ----

    @Test
    fun aNewerQueryCancelsTheOneInFlightAndItsAnswerNeverLands() = runTest {
        val slow = CompletableDeferred<Unit>().also { repository.searchGates["tea"] = it }
        val fast = CompletableDeferred<Unit>().also { repository.searchGates["teacup"] = it }
        repository.searchPages["tea" to null] = ArchivePage(listOf(revealedDay("2026-08-31")), null)
        repository.searchPages["teacup" to null] = ArchivePage(listOf(revealedDay("2026-09-02")), null)
        val list = searching()

        type(list, "tea") // on its way, not answered
        type(list, "teacup")
        fast.complete(Unit)
        runCurrent()
        slow.complete(Unit) // the older answer, arriving last
        runCurrent()

        assertEquals(listOf("tea", "teacup"), repository.searches.map { it.first })
        assertEquals(listOf("tea"), repository.cancelledSearches, "the older request was cancelled")
        assertEquals(listOf("2026-09-02"), list.dates(), "the newer query's results, not the older one's")
        assertEquals("teacup", list.state.value.query)
    }

    @Test
    fun anAnswerForAQueryTheFeedNoLongerHoldsIsDropped() = runTest {
        // The feed's own guard, below the cancellation: an answer that arrives anyway.
        repository.pages[null] = ArchivePage(listOf(revealedDay("2026-09-02")), null)
        repository.searchPages["tea" to null] = ArchivePage(listOf(revealedDay("2026-08-31")), null)
        val gate = CompletableDeferred<Unit>().also { repository.searchGates["tea"] = it }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { feed.search("tea") }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { feed.loadFirst() }

        gate.complete(Unit)
        runCurrent()

        val state = feed.state.first()
        assertEquals(null, state.query)
        assertEquals(listOf("2026-09-02"), state.days.map { it.date })
    }

    // ---- Paging, no results, failure ----

    @Test
    fun resultsArePagedByCursorUntilItIsNull() = runTest {
        repository.searchPages["tea" to null] = ArchivePage(listOf(revealedDay("2026-09-02")), "S2")
        repository.searchPages["tea" to "S2"] = ArchivePage(listOf(revealedDay("2026-08-20")), null)
        val list = searching()
        type(list, "tea")
        assertTrue(list.state.value.canLoadMore)

        list.onAction(ArchiveAction.OnLoadMore)

        assertEquals(listOf<Pair<String, String?>>("tea" to null, "tea" to "S2"), repository.searches)
        assertEquals(listOf("2026-09-02", "2026-08-20"), list.dates())
        assertFalse(list.state.value.canLoadMore)
    }

    @Test
    fun noResultsKeepsTheQueryAndShowsTheMessageNotTheEmptyArchive() = runTest {
        val list = searching()

        type(list, "umbrella")

        val state = list.state.value
        assertTrue(state.noResults)
        assertEquals("umbrella", state.query)
        assertTrue(state.showFilters, "the chips stay as the way back")
        assertTrue(state.showSearch)
    }

    @Test
    fun aFailedSearchKeepsTheFieldAndTryAgainAsksAgainForTheSameQuery() = runTest {
        repository.failNextSearch = true
        repository.searchPages["tea" to null] = ArchivePage(listOf(revealedDay("2026-09-01")), null)
        val list = searching()
        type(list, "tea")
        assertEquals(ArchiveLoad.Failed, list.state.value.load)
        assertEquals(ArchiveFilter.Search, list.state.value.filter)
        assertEquals("tea", list.state.value.query)

        list.onAction(ArchiveAction.OnRetryClick)

        assertEquals(listOf("tea", "tea"), repository.searches.map { it.first })
        assertEquals(listOf("2026-09-01"), list.dates())
    }

    // ---- The way back ----

    @Test
    fun theChipsLeaveSearchAndRestoreTheirList() = runTest {
        repository.searchPages["tea" to null] = ArchivePage(listOf(revealedDay("2026-09-01")), null)
        repository.favouritePages[null] = ArchivePage(listOf(revealedDay("2026-08-31").copy(markedByMe = setOf("mine-2026-08-31"))), null)
        val list = searching()
        type(list, "tea")

        list.onAction(ArchiveAction.OnFilterClick(ArchiveFilter.AllDays))
        assertEquals(ArchiveFilter.AllDays, list.state.value.filter)
        assertEquals("", list.state.value.query)
        assertEquals(listOf("2026-09-02", "2026-09-01", "2026-08-31"), list.dates())

        list.onAction(ArchiveAction.OnFilterClick(ArchiveFilter.Search))
        assertEquals("", list.state.value.query, "a new search starts empty")
        type(list, "tea")
        list.onAction(ArchiveAction.OnFilterClick(ArchiveFilter.Favourites))
        assertEquals(ArchiveFilter.Favourites, list.state.value.filter)
        assertEquals(listOf("2026-08-31"), list.dates())

        // A query typed after leaving is not searched.
        list.onAction(ArchiveAction.OnQueryChange("late"))
        advanceTimeBy(SEARCH_DEBOUNCE * 2)
        runCurrent()
        assertEquals(listOf("tea", "tea"), repository.searches.map { it.first })
    }

    @Test
    fun aResultOpensTheDayViewAndBackFindsTheQueryAndResultsKept() = runTest {
        repository.searchPages["tea" to null] = ArchivePage(listOf(revealedDay("2026-09-01")), null)
        val list = searching()
        type(list, "tea")
        val events = mutableListOf<ArchiveEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { list.events.collect { events += it } }

        list.onAction(ArchiveAction.OnDayClick(kotlinx.datetime.LocalDate.parse("2026-09-01")))
        val day = ArchiveDayViewModel("2026-09-01", feed, repository, FakeEntries(), loop)

        assertEquals(listOf<ArchiveEvent>(ArchiveEvent.NavigateToDay(kotlinx.datetime.LocalDate.parse("2026-09-01"))), events)
        assertEquals(ArchiveLoad.Ready, day.state.value.load, "the result is shown at once, from the feed")
        assertEquals(listOf("2026-09-01"), repository.dayCalls, "then asked for, as every day view is")
        // Back: the list ViewModel is the same one, so its query and results are as they were.
        assertEquals("tea", list.state.value.query)
        assertEquals(ArchiveFilter.Search, list.state.value.filter)
        assertEquals(listOf("2026-09-01"), list.dates())
        assertEquals(1, repository.searches.size, "and nothing was asked again")
    }

    @Test
    fun theQueryNeverReachesAToString() = runTest {
        val list = searching()
        list.onAction(ArchiveAction.OnQueryChange("our secret word"))

        assertFalse("secret" in list.state.value.toString())
        assertFalse("secret" in ArchiveAction.OnQueryChange("our secret word").toString())
        type(list, "our secret word")
        assertFalse("secret" in feed.state.value.toString())
    }
}
