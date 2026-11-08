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
import com.moyi.app.feature.today.archive.ArchiveDayViewModel
import com.moyi.app.feature.today.archive.ArchiveFeed
import com.moyi.app.feature.today.archive.ArchiveFilter
import com.moyi.app.feature.today.archive.ArchiveLoad
import com.moyi.app.feature.today.archive.ArchiveViewModel
import com.moyi.app.feature.today.archive.FavouriteTarget
import com.moyi.app.feature.today.archive.SEARCH_DEBOUNCE
import com.moyi.app.feature.today.common.DailyLoopState
import com.moyi.app.feature.today.common.TodayStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/**
 * The Archive over its cache (doc 13 §5): drawn from the cache at once, refreshed behind
 * it with the server winning, quiet offline, kept consistent by the day view, and never
 * holding words BR-1 would not let this person read.
 */
class ArchiveCacheThenNetworkTest : MainDispatcherTest() {
    private val loop = FakeDailyLoop(DailyLoopState.Ready(dayView(TodayStatus.Revealed)))
    private val repository = FakeArchiveRepository()
    private val entries = FakeEntries()
    private val cache = FakeArchiveCache()
    private val feed = ArchiveFeed(loop, repository, cache)

    private fun TestScope.list(): ArchiveViewModel = ArchiveViewModel(feed).also { viewModel ->
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
    }

    private fun dayView(date: String) = ArchiveDayViewModel(date, feed, repository, entries, loop)

    private fun ArchiveViewModel.dates(): List<String> = state.value.months.flatMap { it.days }.map { it.date.toString() }

    private fun ArchiveViewModel.mine(date: String): String? =
        state.value.months.flatMap { it.days }.first { it.date.toString() == date }.myEntry

    @Test
    fun theCachedDaysShowAtOnceAndTheServersAnswerReplacesThem() = runTest {
        cache.run = ArchivePage(listOf(revealedDay("2026-09-02", mine = "cached words"), revealedDay("2026-09-01")), null)
        repository.pages[null] = ArchivePage(listOf(revealedDay("2026-09-03"), revealedDay("2026-09-02"), revealedDay("2026-09-01")), null)
        val gate = CompletableDeferred<Unit>().also { repository.daysGate = it }

        val list = list()

        assertEquals(ArchiveLoad.Ready, list.state.value.load, "no waiting surface: the cache is drawn first")
        assertEquals(listOf("2026-09-02", "2026-09-01"), list.dates())
        assertEquals("cached words", list.mine("2026-09-02"))

        gate.complete(Unit)

        assertEquals(listOf("2026-09-03", "2026-09-02", "2026-09-01"), list.dates())
        assertEquals(MINE, list.mine("2026-09-02"), "the server wins")
        assertEquals(repository.pages[null], cache.run, "and what it answered is cached")
    }

    @Test
    fun offlineTheCachedDaysShowWithNoError() = runTest {
        cache.run = ArchivePage(listOf(revealedDay("2026-09-02"), revealedDay("2026-09-01")), null)
        repository.failNext = true

        val state = list().state.value

        assertEquals(ArchiveLoad.Ready, state.load)
        assertFalse(state.pageFailed)
        assertEquals(2, state.months.single().days.size)
        assertEquals(ArchivePage(listOf(revealedDay("2026-09-02"), revealedDay("2026-09-01")), null), cache.run, "nothing written")
    }

    @Test
    fun offlinePagingPastTheCacheFailsForTheNextPageOnlyAndKeepsTheDays() = runTest {
        cache.run = ArchivePage(listOf(revealedDay("2026-09-02"), revealedDay("2026-09-01")), "tail")
        repository.failNext = true
        val list = list()
        assertTrue(list.state.value.canLoadMore, "the cache's own cursor: more is there to ask for")

        list.onAction(ArchiveAction.OnLoadMore) // still offline: the fake has no "tail" page

        assertEquals(ArchiveLoad.Ready, list.state.value.load)
        assertTrue(list.state.value.pageFailed, "the failure at the end of the list")
        assertEquals(listOf("2026-09-02", "2026-09-01"), list.dates(), "never over the days above it")
        assertEquals(listOf(null, "tail"), repository.cursors)
    }

    @Test
    fun aRefreshRemovesWhatTheServerNoLongerHasIncludingAnEntryErasedBeforeItWasSeen() = runTest {
        val locked = ArchiveDay("2026-09-02", BondDayStatus.PENDING_REVEAL, MyEntry("m2", MINE, EntryStatus.SUBMITTED), PartnerEntry.Locked)
        cache.run = ArchivePage(listOf(locked, revealedDay("2026-09-01")), null)
        // On the server: their unseen entry was erased (#101: null), and 09-01 was deleted on the other phone.
        val erased = locked.copy(status = BondDayStatus.SOLO, partnerEntry = null)
        val deleted = revealedDay("2026-09-01").let { it.copy(myEntry = it.myEntry!!.copy(text = null, status = EntryStatus.DELETED), markedByMe = emptySet()) }
        repository.pages[null] = ArchivePage(listOf(erased, deleted), null)

        val list = list()

        assertEquals(ArchivePage(listOf(erased, deleted), null), cache.run)
        val days = list.state.value.months.flatMap { it.days }
        assertNull(days.first { it.date.toString() == "2026-09-02" }.partnerEntry)
        assertTrue(days.first { it.date.toString() == "2026-09-01" }.myTombstone)
    }

    @Test
    fun aFavouriteChangedOnTheOtherPhoneReachesTheCacheOnRefresh() = runTest {
        cache.run = ArchivePage(listOf(revealedDay("2026-09-01")), null)
        val marked = revealedDay("2026-09-01").copy(markedByMe = setOf("theirs-2026-09-01"))
        repository.pages[null] = ArchivePage(listOf(marked), null)

        val list = list()

        assertTrue(list.state.value.months.single().days.single().holdsMyFavourite)
        assertEquals(setOf("theirs-2026-09-01"), cache.run!!.days.single().markedByMe)
    }

    @Test
    fun theCachedOlderPagesAreAskedForAgainAsTheListReachesThemThenOlderDaysAreAdded() = runTest {
        // Cached: two pages. Since then, 09-03 was deleted on the server and 09-05 is new.
        cache.run = ArchivePage(listOf(revealedDay("2026-09-04"), revealedDay("2026-09-03"), revealedDay("2026-09-02"), revealedDay("2026-09-01")), "c2")
        repository.pages[null] = ArchivePage(listOf(revealedDay("2026-09-05"), revealedDay("2026-09-04")), "c1")
        repository.pages["c1"] = ArchivePage(listOf(revealedDay("2026-09-02"), revealedDay("2026-09-01")), "c2")
        repository.pages["c2"] = ArchivePage(listOf(revealedDay("2026-08-31")), null)
        val list = list()
        assertEquals(listOf("2026-09-05", "2026-09-04", "2026-09-03", "2026-09-02", "2026-09-01"), list.dates(), "the older cached days stay")

        list.onAction(ArchiveAction.OnLoadMore)
        assertEquals(listOf("2026-09-05", "2026-09-04", "2026-09-02", "2026-09-01"), list.dates(), "the page asked again: 09-03 is gone")

        list.onAction(ArchiveAction.OnLoadMore)
        assertEquals(listOf("2026-09-05", "2026-09-04", "2026-09-02", "2026-09-01", "2026-08-31"), list.dates())
        assertFalse(list.state.value.canLoadMore)
        assertEquals(listOf(null, "c1", "c2"), repository.cursors)
        assertEquals(list.dates(), cache.run!!.days.map { it.date }, "the cache followed every page")
        assertNull(cache.run!!.nextCursor)
    }

    @Test
    fun theFiltersUpdateCachedDaysButSearchResultsAreNeverStored() = runTest {
        cache.run = ArchivePage(listOf(revealedDay("2026-09-02"), revealedDay("2026-09-01")), null)
        repository.pages[null] = cache.run!!
        val marked = revealedDay("2026-09-01").copy(markedByMe = setOf("mine-2026-09-01"))
        repository.favouritePages[null] = ArchivePage(listOf(marked, revealedDay("2025-01-01").copy(markedByMe = setOf("mine-2025-01-01"))), null)
        repository.searchPages["tea" to null] = ArchivePage(listOf(revealedDay("2026-09-02", mine = "tea, from search")), null)
        val list = list()

        list.onAction(ArchiveAction.OnFilterClick(ArchiveFilter.Favourites))
        assertEquals(listOf(revealedDay("2026-09-02"), marked), cache.run!!.days, "a cached day is updated; an uncached one is not added")

        list.onAction(ArchiveAction.OnFilterClick(ArchiveFilter.Search))
        list.onAction(ArchiveAction.OnQueryChange("tea"))
        advanceTimeBy(SEARCH_DEBOUNCE + 1.milliseconds)
        assertEquals(listOf("2026-09-02"), list.dates())
        assertEquals(MINE, cache.run!!.days.first().myEntry?.text, "the search's answer is not in the cache")
        assertTrue(cache.putPages.none { (page, _) -> page.days.any { it.myEntry?.text == "tea, from search" } }, "nor was ever put")
        assertTrue(cache.updates.none { it.myEntry?.text == "tea, from search" })
    }

    @Test
    fun offlineFavouritesShowTheCachedDaysHoldingMyFavourite() = runTest {
        val marked = revealedDay("2026-09-01").copy(markedByMe = setOf("theirs-2026-09-01"))
        cache.run = ArchivePage(listOf(revealedDay("2026-09-02"), marked), null)
        repository.pages[null] = cache.run!!
        repository.favouritePages[null] = ArchivePage(listOf(marked), null)
        val list = list()
        repository.offline = true

        list.onAction(ArchiveAction.OnFilterClick(ArchiveFilter.Favourites))

        assertEquals(ArchiveLoad.Ready, list.state.value.load, "not the Generic error")
        assertEquals(ArchiveFilter.Favourites, list.state.value.filter)
        assertEquals(listOf("2026-09-01"), list.dates())
        assertEquals(listOf<Pair<String?, Boolean>>(null to false, null to true), repository.requests, "the server was asked first")

        repository.offline = false
        list.onAction(ArchiveAction.OnRetryClick)
        assertEquals(listOf("2026-09-01"), list.dates(), "back online: the server's answer, as before")
        assertEquals<Pair<String?, Boolean>>(null to true, repository.requests.last())
    }

    @Test
    fun offlineFavouritesWithNoneCachedAndMoreToAskForShowTheFailureAsBefore() = runTest {
        cache.run = ArchivePage(listOf(revealedDay("2026-09-02"), revealedDay("2026-09-01")), "tail")
        repository.pages[null] = cache.run!!
        val list = list()
        repository.offline = true

        list.onAction(ArchiveAction.OnFilterClick(ArchiveFilter.Favourites))

        assertEquals(ArchiveLoad.Failed, list.state.value.load, "the older favourites are not known: they are asked for, and fail")
        assertEquals("tail" to true, repository.requests.last(), "past the cache with its own cursor")
    }

    @Test
    fun offlineFavouritesWithNoneCachedAndNothingOlderAreTheEmptyFavourites() = runTest {
        cache.run = ArchivePage(listOf(revealedDay("2026-09-02")), null)
        repository.pages[null] = cache.run!!
        val list = list()
        repository.offline = true

        list.onAction(ArchiveAction.OnFilterClick(ArchiveFilter.Favourites))

        assertTrue(list.state.value.isEmpty, "the whole archive is cached and holds no favourite")
        assertEquals(ArchiveFilter.Favourites, list.state.value.filter)
    }

    @Test
    fun offlineAMonthJumpShowsTheCachedDaysFromThatMonthAndFailsOnlyForThePageAfterThem() = runTest {
        cache.run = ArchivePage(listOf(revealedDay("2026-09-02"), revealedDay("2026-08-20"), revealedDay("2026-08-03")), "tail")
        repository.pages[null] = cache.run!!
        val list = list()
        repository.offline = true

        list.onAction(ArchiveAction.OnMonthChosen(kotlinx.datetime.LocalDate(2026, 8, 1)))

        assertEquals(ArchiveLoad.Ready, list.state.value.load, "not the Generic error")
        assertEquals(listOf("2026-08-20", "2026-08-03"), list.dates(), "on or before the month's last day")
        assertEquals("2026-08-31", repository.untils.last(), "the server was asked first")

        list.onAction(ArchiveAction.OnLoadMore)
        assertTrue(list.state.value.pageFailed, "the page after the cache fails at the end of the list")
        assertEquals(listOf("2026-08-20", "2026-08-03"), list.dates(), "never over the days above it")
        assertEquals("tail", repository.cursors.last())
    }

    @Test
    fun offlineAMonthJumpPastTheCachedRunShowsTheFailureAsBefore() = runTest {
        val cachedRun = ArchivePage(listOf(revealedDay("2026-09-02"), revealedDay("2026-08-20")), "tail")
        cache.run = cachedRun
        repository.pages[null] = cachedRun
        repository.pages["tail"] = ArchivePage(listOf(revealedDay("2026-06-10")), null)
        val list = list()
        list.onAction(ArchiveAction.OnLoadMore) // June seen online, so the picker offers it
        cache.run = cachedRun // and then not kept (the cap)
        repository.offline = true

        list.onAction(ArchiveAction.OnMonthChosen(kotlinx.datetime.LocalDate(2026, 6, 1)))

        assertEquals(ArchiveLoad.Failed, list.state.value.load, "June is not cached: the existing failure")
        assertEquals("2026-06-30", repository.untils.last())
    }

    @Test
    fun offlineSearchStaysOnlineOnlyAndShowsTheGenericError() = runTest {
        cache.run = ArchivePage(listOf(revealedDay("2026-09-02", mine = "tea")), null)
        repository.pages[null] = cache.run!!
        val list = list()
        repository.offline = true

        list.onAction(ArchiveAction.OnFilterClick(ArchiveFilter.Search))
        list.onAction(ArchiveAction.OnQueryChange("tea"))
        advanceTimeBy(SEARCH_DEBOUNCE + 1.milliseconds)

        assertEquals(ArchiveLoad.Failed, list.state.value.load, "the cached day that says tea is not searched")
        assertEquals(listOf<Pair<String, String?>>("tea" to null), repository.searches)
    }

    @Test
    fun anEmptyArchiveIsCachedAndShowsItsEmptyStateOffline() = runTest {
        repository.pages[null] = ArchivePage(emptyList(), null)
        list()
        assertEquals(ArchivePage(emptyList(), null), cache.run, "the empty answer is kept")
        feed.clear()

        repository.offline = true
        val offline = list()

        assertEquals(ArchiveLoad.Ready, offline.state.value.load, "not the Generic error")
        assertTrue(offline.state.value.isEmpty, "the Archive's empty state")
    }

    @Test
    fun theDayViewDrawsTheCachedDayThenAsksAndOfflineSaysNothing() = runTest {
        cache.run = ArchivePage(listOf(revealedDay("2026-09-01")), null)
        repository.dayAnswer = Result.Failure(DataError.Network.NoInternet)

        val day = dayView("2026-09-01")

        assertEquals(ArchiveLoad.Ready, day.state.value.load)
        assertEquals(THEIRS, day.state.value.day?.partnerEntry)
        assertEquals(listOf("2026-09-01"), repository.dayCalls)
    }

    @Test
    fun theDayViewsAnswerReplacesTheCachedDayAndA404RemovesIt() = runTest {
        cache.run = ArchivePage(listOf(revealedDay("2026-09-02"), revealedDay("2026-09-01")), null)
        val marked = revealedDay("2026-09-02").copy(markedByMe = setOf("mine-2026-09-02"))
        repository.dayAnswer = Result.Success(marked)

        assertEquals(true, dayView("2026-09-02").state.value.day?.myMarkOnMine)
        assertEquals(marked, cache.run!!.days.first())

        repository.dayAnswer = Result.Failure(NOT_FOUND)
        val gone = dayView("2026-09-01")

        assertEquals(ArchiveLoad.Failed, gone.state.value.load)
        assertEquals(listOf("2026-09-02"), cache.run!!.days.map { it.date }, "the server wins: the day is gone here too")
    }

    @Test
    fun aMarkReachesTheCacheOnlyOnceTheServerConfirmsIt() = runTest {
        cache.run = ArchivePage(listOf(revealedDay("2026-09-01")), null)
        repository.pages[null] = cache.run!!
        list()
        val gate = CompletableDeferred<Unit>().also { repository.markGate = it }
        val day = dayView("2026-09-01")

        day.onAction(ArchiveDayAction.OnFavouriteClick(FavouriteTarget.Theirs))
        assertEquals(true, day.state.value.day?.myMarkOnTheirs, "optimistic on screen")
        assertTrue(cache.marks.isEmpty(), "not yet in the cache")

        gate.complete(Unit)
        assertEquals(listOf(Triple("2026-09-01", "theirs-2026-09-01", true)), cache.marks)
        assertEquals(setOf("theirs-2026-09-01"), cache.run!!.days.single().markedByMe)

        repository.markGate = null
        repository.markResult = Result.Failure(DataError.Network.NoInternet)
        day.onAction(ArchiveDayAction.OnFavouriteClick(FavouriteTarget.Theirs))
        assertEquals(true, day.state.value.day?.myMarkOnTheirs, "put back")
        assertEquals(1, cache.marks.size, "a refused mark never reaches the cache")
    }

    @Test
    fun aDeleteReachesTheCacheOnlyAfterTheServerConfirmsIt() = runTest {
        cache.run = ArchivePage(listOf(revealedDay("2026-09-01")), null)
        repository.pages[null] = cache.run!!
        list()
        entries.deleteResult = Result.Failure(DataError.Network.NoInternet)
        val day = dayView("2026-09-01")
        val updatesBefore = cache.updates.size

        day.onAction(ArchiveDayAction.OnDeleteClick)
        day.onAction(ArchiveDayAction.OnDeleteConfirm)
        assertEquals(MINE, cache.run!!.days.single().myEntry?.text, "a failed delete leaves the cache")
        assertEquals(updatesBefore, cache.updates.size)

        entries.deleteResult = Result.Success(Unit)
        day.onAction(ArchiveDayAction.OnDeleteClick)
        day.onAction(ArchiveDayAction.OnDeleteConfirm)
        val cached = cache.run!!.days.single().myEntry!!
        assertEquals(EntryStatus.DELETED, cached.status)
        assertNull(cached.text, "the tombstone, without the words")
    }

    @Test
    fun theirWordsAreNeverCachedOnADayThatMayNotShowThem() = runTest {
        // A server bug, say: their words on a day not yet revealed. The list would not show
        // them, so the cache does not keep them either (BR-1).
        val early = ArchiveDay("2026-09-01", BondDayStatus.PENDING_REVEAL, MyEntry("m1", MINE, EntryStatus.SUBMITTED), PartnerEntry.Submitted("t1", THEIRS))
        repository.pages[null] = ArchivePage(listOf(early), null)

        list()

        val kept = cache.run!!.days.single()
        assertEquals(PartnerEntry.Locked, kept.partnerEntry)
        assertEquals(MINE, kept.myEntry?.text, "my own words are mine to keep")
    }
}
