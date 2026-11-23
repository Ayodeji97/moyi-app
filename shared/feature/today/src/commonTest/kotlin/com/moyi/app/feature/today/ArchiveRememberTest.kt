package com.moyi.app.feature.today

import com.moyi.app.core.DataError
import com.moyi.app.domain.archive.ArchiveDay
import com.moyi.app.domain.archive.ArchivePage
import com.moyi.app.domain.archive.REMEMBER_MAX_LENGTH
import com.moyi.app.domain.archive.RememberError
import com.moyi.app.domain.bond.InMemorySearchByMeaningStatus
import com.moyi.app.domain.today.BondDayStatus
import com.moyi.app.domain.today.EntryStatus
import com.moyi.app.domain.today.MyEntry
import com.moyi.app.domain.today.PartnerEntry
import com.moyi.app.feature.today.archive.ArchiveAction
import com.moyi.app.feature.today.archive.ArchiveDayViewModel
import com.moyi.app.feature.today.archive.ArchiveFeed
import com.moyi.app.feature.today.archive.ArchiveFilter
import com.moyi.app.feature.today.archive.ArchiveLoad
import com.moyi.app.feature.today.archive.ArchiveViewModel
import com.moyi.app.feature.today.archive.RememberFailure
import com.moyi.app.feature.today.common.DailyLoopState
import com.moyi.app.feature.today.common.TodayStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * "Remember when" (FR-204, the Archive's fourth chip; owner rulings 2026-11-18) over fakes:
 * the chip's gate, an explicit Ask with one question in flight, a flat answer in the
 * server's order, the `429` hold, the generic failure, a chip cancelling the question, a
 * stale answer dropped, the answer kept in memory only, and the question kept out of logs.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ArchiveRememberTest : MainDispatcherTest() {
    private val loop = FakeDailyLoop(DailyLoopState.Ready(dayView(TodayStatus.Revealed)))
    private val repository = FakeArchiveRepository()
    private val cache = FakeArchiveCache()
    private val rememberer = FakeRememberRepository()
    private val feed = ArchiveFeed(loop, repository, cache, rememberer)

    private fun TestScope.list(rememberWhen: Boolean = true): ArchiveViewModel = ArchiveViewModel(feed, InMemorySearchByMeaningStatus(), rememberWhen).also { viewModel ->
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
    }

    private fun TestScope.remembering(): ArchiveViewModel {
        repository.pages[null] = ArchivePage(listOf(revealedDay("2026-09-02"), revealedDay("2026-09-01")), null)
        return list().also { it.onAction(ArchiveAction.OnFilterClick(ArchiveFilter.Remember)) }
    }

    private fun ArchiveViewModel.ask(question: String) {
        onAction(ArchiveAction.OnQuestionChange(question))
        onAction(ArchiveAction.OnAskClick)
    }

    private fun ArchiveViewModel.resultDates(): List<String>? = state.value.results?.map { it.day.date.toString() }

    // ---- The chip ----

    @Test
    fun theChipIsOfferedOnlyOverAnArchiveWithDaysAndOnlyWhereTheBuildAllowsIt() = runTest {
        repository.pages[null] = ArchivePage(emptyList(), null)
        assertFalse(list().state.value.showRemember, "hidden while the archive is empty")

        feed.clear()
        repository.pages[null] = ArchivePage(listOf(revealedDay("2026-09-02")), null)
        assertTrue(list().state.value.showRemember)

        feed.clear()
        val release = list(rememberWhen = false)
        assertFalse(release.state.value.showRemember, "hidden in release builds")
        release.onAction(ArchiveAction.OnFilterClick(ArchiveFilter.Remember))
        assertEquals(ArchiveFilter.AllDays, release.state.value.filter, "the chip cannot be chosen where it is hidden")
    }

    @Test
    fun choosingTheChipShowsAnEmptyFocusedQuestionAndNothingBelowIt() = runTest {
        val list = remembering()

        val state = list.state.value
        assertEquals(ArchiveFilter.Remember, state.filter)
        assertEquals(ArchiveLoad.Ready, state.load)
        assertTrue(state.showFilters, "the chips stay: they are the way back")
        assertTrue(state.focusQuestion)
        assertFalse(state.showMonthRow, "the field takes the month row's slot")
        assertFalse(state.canAsk, "nothing to ask yet")
        assertNull(state.results)
        assertTrue(state.months.isEmpty(), "the list is not drawn under an unanswered question")

        list.onAction(ArchiveAction.OnQuestionFocused)
        assertFalse(list.state.value.focusQuestion, "focused once")
    }

    // ---- Asking ----

    @Test
    fun typingSendsNothingAndAskSendsTheTrimmedQuestionOnce() = runTest {
        val list = remembering()

        list.onAction(ArchiveAction.OnQuestionChange("  when I was unwell "))
        advanceTimeBy(10.seconds)
        runCurrent()
        assertEquals(emptyList(), rememberer.questions, "never as-you-type")
        assertTrue(list.state.value.canAsk)

        list.onAction(ArchiveAction.OnAskClick)
        assertEquals(listOf("when I was unwell"), rememberer.questions)
    }

    @Test
    fun aQuestionOfOnlySpacesCannotBeAsked() = runTest {
        val list = remembering()
        list.ask("   ")
        assertFalse(list.state.value.canAsk)
        assertEquals(emptyList(), rememberer.questions)
    }

    @Test
    fun oneQuestionInFlight() = runTest {
        val list = remembering()
        rememberer.gates["soup"] = CompletableDeferred()

        list.ask("soup")
        assertTrue(list.state.value.asking)
        assertFalse(list.state.value.canAsk)
        assertNull(list.state.value.results, "the list area waits while asking")

        list.onAction(ArchiveAction.OnAskClick)
        list.ask("tea")
        assertEquals(listOf("soup"), rememberer.questions, "nothing more is sent while one is on its way")
        assertEquals("tea", list.state.value.question, "the field still takes typing")

        rememberer.gates.getValue("soup").complete(Unit)
        runCurrent()
        assertFalse(list.state.value.asking)
        assertTrue(list.state.value.canAsk)
    }

    @Test
    fun theQuestionIsCappedAtTheSameLengthAsSearch() = runTest {
        val list = remembering()
        list.onAction(ArchiveAction.OnQuestionChange("a".repeat(REMEMBER_MAX_LENGTH + 20)))
        assertEquals(REMEMBER_MAX_LENGTH, list.state.value.question.length)
    }

    // ---- The answer ----

    @Test
    fun theAnswerIsOneFlatListInTheServersOrderAndTakesFocus() = runTest {
        val list = remembering()
        // Relevance, not date: September 1st first, an older year in the middle.
        rememberer.answers["unwell"] = listOf(revealedDay("2026-09-01"), revealedDay("2025-03-20"), revealedDay("2026-09-02"))

        list.ask("unwell")

        val state = list.state.value
        assertEquals(listOf("2026-09-01", "2025-03-20", "2026-09-02"), list.resultDates())
        assertTrue(state.months.isEmpty(), "no month groups")
        assertEquals(listOf(false, true, false), state.results?.map { it.withYear }, "the year only where it is not this year")
        assertTrue(state.focusResults)

        list.onAction(ArchiveAction.OnResultsFocused)
        assertFalse(list.state.value.focusResults, "focus moves once")
    }

    @Test
    fun nothingCloseIsAnEmptyAnswerNotAFailure() = runTest {
        val list = remembering()
        list.ask("Accra")
        assertEquals(emptyList(), list.resultDates())
        assertNull(list.state.value.rememberFailure)
        // A day-less answer never asks for "another page", and never says the archive is empty.
        assertEquals(listOf<Pair<String?, Boolean>>(null to false), repository.requests)
        assertTrue(list.state.value.showRemember)
        assertTrue(list.state.value.showSearch)
    }

    @Test
    fun todayBeforeItOpensShowsOnlyMyWords() = runTest {
        val list = remembering()
        val today = ArchiveDay("2026-09-14", BondDayStatus.PENDING_REVEAL, MyEntry("e1", "Slept well.", EntryStatus.SUBMITTED), PartnerEntry.Locked)
        rememberer.answers["slept"] = listOf(today)

        list.ask("slept")

        val day = list.state.value.results.orEmpty().single().day
        assertEquals("Slept well.", day.myEntry)
        assertNull(day.partnerEntry)
    }

    @Test
    fun theAnswerIsNeverWrittenToTheCacheAndTheDayViewFindsItInMemory() = runTest {
        val list = remembering()
        val putsBefore = cache.putPages.size
        val older = revealedDay("2025-03-20", mine = "Pepper soup.")
        rememberer.answers["soup"] = listOf(older)

        list.ask("soup")

        assertEquals(putsBefore, cache.putPages.size)
        assertTrue(cache.updates.none { it.date == "2025-03-20" })
        assertNull(cache.day("b1", "2025-03-20"), "not on the device's disk")
        // Opened from the answer, offline: drawn from memory, since the cache has no such day.
        repository.dayAnswer = com.moyi.app.core.Result.Failure(DataError.Network.NoInternet)
        val dayView = ArchiveDayViewModel("2025-03-20", feed, repository, FakeEntries(), loop)
        assertEquals("Pepper soup.", dayView.state.value.day?.myEntry)
    }

    @Test
    fun theAnswerAndTheQuestionStayThroughTheDayView() = runTest {
        val list = remembering()
        rememberer.answers["soup"] = listOf(revealedDay("2026-09-01"))
        list.ask("soup")

        list.onAction(ArchiveAction.OnDayClick(kotlinx.datetime.LocalDate(2026, 9, 1)))

        assertEquals("soup", list.state.value.question)
        assertEquals(listOf("2026-09-01"), list.resultDates())
        assertEquals(listOf("soup"), rememberer.questions, "not asked again")
    }

    // ---- Failures ----

    @Test
    fun aRateLimitHoldsAskUntilRetryAfterAndKeepsTheLastAnswer() = runTest {
        val list = remembering()
        rememberer.answers["soup"] = listOf(revealedDay("2026-09-01"))
        list.ask("soup")

        rememberer.failNext = RememberError.RateLimited(retryAfterSeconds = 90)
        list.ask("picnic")

        assertEquals(RememberFailure.RateLimited, list.state.value.rememberFailure)
        assertFalse(list.state.value.canAsk, "held")
        assertEquals(listOf("2026-09-01"), list.resultDates(), "nothing found disappears")
        list.onAction(ArchiveAction.OnAskClick)
        assertEquals(listOf("soup", "picnic"), rememberer.questions, "a held Ask sends nothing")

        advanceTimeBy(89.seconds)
        runCurrent()
        assertFalse(list.state.value.canAsk)

        advanceTimeBy(2.seconds)
        runCurrent()
        assertTrue(list.state.value.canAsk)
        assertNull(list.state.value.rememberFailure, "the line goes with the wait")
    }

    @Test
    fun theHoldOutlivesAChipChange() = runTest {
        val list = remembering()
        rememberer.failNext = RememberError.RateLimited(retryAfterSeconds = 60)
        list.ask("picnic")

        list.onAction(ArchiveAction.OnFilterClick(ArchiveFilter.AllDays))
        list.onAction(ArchiveAction.OnFilterClick(ArchiveFilter.Remember))
        list.onAction(ArchiveAction.OnQuestionChange("picnic"))

        assertFalse(list.state.value.canAsk, "a chip cannot skip the wait")
    }

    @Test
    fun anyOtherFailureIsTheGenericLineAndTryAgainAsksAgain() = runTest {
        val list = remembering()
        rememberer.failNext = RememberError.Failed(DataError.Network.NoInternet)

        list.ask("soup")

        assertEquals(RememberFailure.Failed, list.state.value.rememberFailure)
        assertNull(list.state.value.results, "no cache to fall back to")
        assertTrue(list.state.value.canAsk)

        rememberer.answers["soup"] = listOf(revealedDay("2026-09-01"))
        list.onAction(ArchiveAction.OnAskClick)
        assertNull(list.state.value.rememberFailure)
        assertEquals(listOf("2026-09-01"), list.resultDates())
    }

    // ---- Leaving, and stale answers ----

    @Test
    fun aChipChosenWhileAskingCancelsTheQuestionAndItsAnswerNeverShows() = runTest {
        val list = remembering()
        rememberer.gates["soup"] = CompletableDeferred()
        rememberer.answers["soup"] = listOf(revealedDay("2026-09-01"))
        list.ask("soup")

        list.onAction(ArchiveAction.OnFilterClick(ArchiveFilter.AllDays))

        assertEquals(listOf("soup"), rememberer.cancelled)
        assertEquals(ArchiveFilter.AllDays, list.state.value.filter)
        assertEquals(listOf("2026-09-02", "2026-09-01"), list.state.value.months.flatMap { it.days }.map { it.date.toString() })
        assertEquals("", list.state.value.question, "the question is forgotten with the chip")
    }

    @Test
    fun anAnswerThatArrivesAfterTheListMovedOnIsDropped() = runTest {
        // The feed directly: an answer that was not cancelled in time (another screen's load won).
        repository.pages[null] = ArchivePage(listOf(revealedDay("2026-09-02")), null)
        rememberer.gates["soup"] = CompletableDeferred()
        rememberer.answers["soup"] = listOf(revealedDay("2026-09-01"))
        val asked = backgroundScope.launch { assertNull(feed.remember("soup"), "a stale answer is reported as dropped") }
        runCurrent()

        feed.loadFirst()
        rememberer.gates.getValue("soup").complete(Unit)
        runCurrent()
        asked.join()

        assertNull(feed.state.value.remember)
        assertEquals(listOf("2026-09-02"), feed.state.value.days.map { it.date })
    }

    @Test
    fun leavingTheArchiveForgetsTheAnswer() = runTest {
        val list = remembering()
        rememberer.answers["soup"] = listOf(revealedDay("2026-09-01"))
        list.ask("soup")
        assertNotNull(feed.state.value.remember)

        feed.clear()

        assertNull(feed.state.value.remember)
        assertTrue(feed.state.value.days.isEmpty())
    }

    @Test
    fun theSearchChipFromRememberShowsTheUnfilteredListAgain() = runTest {
        val list = remembering()
        rememberer.answers["soup"] = listOf(revealedDay("2026-09-01"))
        list.ask("soup")

        list.onAction(ArchiveAction.OnFilterClick(ArchiveFilter.Search))
        runCurrent()

        assertEquals(ArchiveFilter.Search, list.state.value.filter)
        assertNull(list.state.value.results)
        assertEquals(listOf("2026-09-02", "2026-09-01"), list.state.value.months.flatMap { it.days }.map { it.date.toString() })
    }

    // ---- Privacy ----

    @Test
    fun theQuestionNeverReachesAToString() = runTest {
        val list = remembering()
        rememberer.answers["my secret question"] = listOf(revealedDay("2026-09-01"))
        list.ask("my secret question")

        assertFalse("secret" in list.state.value.toString())
        assertFalse("secret" in feed.state.value.toString())
        assertFalse("secret" in ArchiveAction.OnQuestionChange("my secret question").toString())
    }
}
