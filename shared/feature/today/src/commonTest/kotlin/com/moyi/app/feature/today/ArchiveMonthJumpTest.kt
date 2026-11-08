package com.moyi.app.feature.today

import com.moyi.app.domain.archive.ArchivePage
import com.moyi.app.feature.today.archive.ArchiveAction
import com.moyi.app.feature.today.archive.ArchiveFeed
import com.moyi.app.feature.today.archive.ArchiveFilter
import com.moyi.app.feature.today.archive.ArchiveViewModel
import com.moyi.app.feature.today.common.DailyLoopState
import com.moyi.app.feature.today.common.TodayStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
 * The month jump (states.md §6, route 1; `GET /bonds/{bondId}/days?until=`) over fakes: the
 * row names the month in view, a chosen month sends `until` (with `favourites=true` in
 * Favourites), paging continues from it with the cursor, the row is absent in Search, and the
 * picker offers no month after the current Bond-day (14 September 2026 here).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ArchiveMonthJumpTest : MainDispatcherTest() {
    private val loop = FakeDailyLoop(DailyLoopState.Ready(dayView(TodayStatus.Revealed)))
    private val repository = FakeArchiveRepository()
    private val feed = ArchiveFeed(loop, repository)

    private fun TestScope.list(): ArchiveViewModel = ArchiveViewModel(feed).also { viewModel ->
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
    }

    private fun ArchiveViewModel.dates(): List<String> = state.value.months.flatMap { it.days }.map { it.date.toString() }

    /** Three months loaded: September, August and June 2026. */
    private fun TestScope.loaded(): ArchiveViewModel {
        repository.pages[null] = ArchivePage(listOf(revealedDay("2026-09-02"), revealedDay("2026-08-20"), revealedDay("2026-06-03")), "C1")
        return list()
    }

    @Test
    fun theRowNamesTheFirstMonthAndOffersTheMonthsFromTheBondDayBackToTheOldestLoaded() = runTest {
        val list = loaded()

        val state = list.state.value
        assertTrue(state.showMonthRow)
        assertEquals(LocalDate(2026, 9, 1), state.visibleMonth)
        assertFalse(state.jumped)
        assertFalse(state.showMonthPicker)
        list.onAction(ArchiveAction.OnJumpClick)
        assertTrue(list.state.value.showMonthPicker)
        assertEquals(
            listOf(LocalDate(2026, 9, 1), LocalDate(2026, 8, 1), LocalDate(2026, 7, 1), LocalDate(2026, 6, 1)),
            list.state.value.pickerMonths,
        )
    }

    @Test
    fun theVisibleMonthFollowsTheTopmostGroup() = runTest {
        val list = loaded()

        list.onAction(ArchiveAction.OnVisibleMonthChange(LocalDate(2026, 8, 20)))
        assertEquals(LocalDate(2026, 8, 1), list.state.value.visibleMonth)
        list.onAction(ArchiveAction.OnVisibleMonthChange(LocalDate(2026, 6, 3)))
        assertEquals(LocalDate(2026, 6, 1), list.state.value.visibleMonth)
    }

    @Test
    fun choosingAMonthSendsUntilItsLastDayAndScrollsToTheTop() = runTest {
        val list = loaded()
        repository.untilPages["2026-08-31" to null] = ArchivePage(listOf(revealedDay("2026-08-20"), revealedDay("2026-08-02")), null)

        list.onAction(ArchiveAction.OnJumpClick)
        list.onAction(ArchiveAction.OnMonthChosen(LocalDate(2026, 8, 1)))

        assertEquals("2026-08-31", repository.untils.last())
        assertEquals(null to false, repository.requests.last(), "no cursor, no favourites")
        val state = list.state.value
        assertFalse(state.showMonthPicker)
        assertEquals(listOf("2026-08-20", "2026-08-02"), list.dates())
        assertEquals(LocalDate(2026, 8, 1), state.visibleMonth)
        assertTrue(state.jumped, "a way back to the newest days")
        assertTrue(state.scrollToTop)
        list.onAction(ArchiveAction.OnScrolledToTop)
        assertFalse(list.state.value.scrollToTop, "scrolled once, not again on the way back from a day view")
    }

    @Test
    fun aMonthWithNoDaysStartsAtTheNearestEarlierDay() = runTest {
        val list = loaded()
        // July holds nothing: the server starts at the nearest earlier day, in June.
        repository.untilPages["2026-07-31" to null] = ArchivePage(listOf(revealedDay("2026-06-03")), null)

        list.onAction(ArchiveAction.OnMonthChosen(LocalDate(2026, 7, 1)))

        assertEquals("2026-07-31", repository.untils.last())
        assertEquals(LocalDate(2026, 6, 1), list.state.value.visibleMonth, "the row says what is in view, not what was asked")
    }

    @Test
    fun inFavouritesAJumpSendsUntilAndFavouritesTrue() = runTest {
        val list = loaded()
        repository.favouritePages[null] = ArchivePage(listOf(revealedDay("2026-08-20").copy(markedByMe = setOf("theirs-2026-08-20"))), "F1")
        list.onAction(ArchiveAction.OnFilterClick(ArchiveFilter.Favourites))
        repository.untilPages["2026-08-31" to null] = ArchivePage(listOf(revealedDay("2026-08-20").copy(markedByMe = setOf("theirs-2026-08-20"))), null)

        list.onAction(ArchiveAction.OnMonthChosen(LocalDate(2026, 8, 1)))

        assertEquals("2026-08-31", repository.untils.last())
        assertEquals(null to true, repository.requests.last(), "the filter is kept")
        assertEquals(ArchiveFilter.Favourites, list.state.value.filter)
        assertTrue(list.state.value.showMonthRow)
    }

    @Test
    fun pagingContinuesFromTheJumpWithTheCursorAndTheSameUntil() = runTest {
        val list = loaded()
        repository.untilPages["2026-08-31" to null] = ArchivePage(listOf(revealedDay("2026-08-20")), "J1")
        repository.untilPages["2026-08-31" to "J1"] = ArchivePage(listOf(revealedDay("2026-06-03")), null)

        list.onAction(ArchiveAction.OnMonthChosen(LocalDate(2026, 8, 1)))
        list.onAction(ArchiveAction.OnLoadMore)

        assertEquals("J1", repository.cursors.last())
        assertEquals("2026-08-31", repository.untils.last(), "with a cursor, both apply")
        assertEquals(listOf("2026-08-20", "2026-06-03"), list.dates())
        assertFalse(list.state.value.canLoadMore)
    }

    @Test
    fun backToTheNewestDaysDropsUntil() = runTest {
        val list = loaded()
        repository.untilPages["2026-08-31" to null] = ArchivePage(listOf(revealedDay("2026-08-20")), null)
        list.onAction(ArchiveAction.OnMonthChosen(LocalDate(2026, 8, 1)))

        list.onAction(ArchiveAction.OnNewestClick)

        assertNull(repository.untils.last())
        assertFalse(list.state.value.jumped)
        assertEquals(listOf("2026-09-02", "2026-08-20", "2026-06-03"), list.dates())
    }

    @Test
    fun choosingTheBondDaysOwnMonthIsTheNewestList() = runTest {
        val list = loaded()

        list.onAction(ArchiveAction.OnMonthChosen(LocalDate(2026, 9, 1)))

        assertNull(repository.untils.last())
        assertFalse(list.state.value.jumped)
    }

    @Test
    fun noMonthAfterTheCurrentBondDayAndNoneBeforeTheOldestLoaded() = runTest {
        val list = loaded()
        val asked = repository.untils.size

        list.onAction(ArchiveAction.OnJumpClick)
        assertEquals(LocalDate(2026, 9, 1), list.state.value.pickerMonths.first(), "the Bond-day's month is the newest offered")
        list.onAction(ArchiveAction.OnMonthChosen(LocalDate(2026, 10, 1)))
        list.onAction(ArchiveAction.OnMonthChosen(LocalDate(2026, 5, 1)))

        assertEquals(asked, repository.untils.size, "neither was asked for")
        assertFalse(list.state.value.jumped)
    }

    @Test
    fun theRowIsAbsentInSearchAndAJumpIsLeftThere() = runTest {
        val list = loaded()
        repository.untilPages["2026-08-31" to null] = ArchivePage(listOf(revealedDay("2026-08-20")), null)
        list.onAction(ArchiveAction.OnMonthChosen(LocalDate(2026, 8, 1)))

        list.onAction(ArchiveAction.OnFilterClick(ArchiveFilter.Search))

        val state = list.state.value
        assertFalse(state.showMonthRow, "the field takes the row's slot")
        assertFalse(state.jumped)
        assertNull(state.visibleMonth)
        list.onAction(ArchiveAction.OnJumpClick)
        assertFalse(list.state.value.showMonthPicker)
        // Search's list is the newest, unfiltered.
        assertNull(repository.untils.last())
    }

    @Test
    fun theRowIsAbsentOverAnEmptyArchive() = runTest {
        repository.pages[null] = ArchivePage(emptyList(), null)
        val list = list()

        assertFalse(list.state.value.showMonthRow)
    }

    @Test
    fun tryAgainAfterAFailedJumpAsksForTheSameMonth() = runTest {
        val list = loaded()
        repository.failNext = true
        list.onAction(ArchiveAction.OnMonthChosen(LocalDate(2026, 8, 1)))
        repository.untilPages["2026-08-31" to null] = ArchivePage(listOf(revealedDay("2026-08-20")), null)

        list.onAction(ArchiveAction.OnRetryClick)

        assertEquals(listOf("2026-08-31", "2026-08-31"), repository.untils.takeLast(2))
        assertEquals(listOf("2026-08-20"), list.dates())
    }
}
