package com.moyi.app.feature.today

import com.moyi.app.core.DataError
import com.moyi.app.domain.archive.ArchivePage
import com.moyi.app.domain.archive.RememberError
import com.moyi.app.domain.bond.InMemorySearchByMeaningStatus
import com.moyi.app.domain.bond.SearchByMeaning
import com.moyi.app.feature.today.archive.ArchiveAction
import com.moyi.app.feature.today.archive.ArchiveEvent
import com.moyi.app.feature.today.archive.ArchiveFeed
import com.moyi.app.feature.today.archive.ArchiveFilter
import com.moyi.app.feature.today.archive.ArchiveViewModel
import com.moyi.app.feature.today.archive.MeaningNote
import com.moyi.app.feature.today.common.DailyLoopState
import com.moyi.app.feature.today.common.TodayStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Remember when's line about how it finds (owner ruling 2026-11-18, PR 4): the approved off line
 * while the bond's setting is off, its own line while the setting is on but the server cannot
 * search by meaning (`semanticSearchActive` false), none while it can or before that is known.
 * The line holds through every state of a question, and the off line opens Bond settings.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ArchiveRememberMeaningTest : MainDispatcherTest() {
    private val day = dayView(TodayStatus.Revealed)
    private val loop = FakeDailyLoop(DailyLoopState.Ready(day))
    private val repository = FakeArchiveRepository()
    private val rememberer = FakeRememberRepository()
    private val feed = ArchiveFeed(loop, repository, FakeArchiveCache(), rememberer)
    private val status = InMemorySearchByMeaningStatus()

    private fun TestScope.remembering(): ArchiveViewModel {
        repository.pages[null] = ArchivePage(listOf(revealedDay("2026-09-02")), null)
        return ArchiveViewModel(feed, status, rememberWhen = true).also { viewModel ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
            viewModel.onAction(ArchiveAction.OnFilterClick(ArchiveFilter.Remember))
        }
    }

    private fun ArchiveViewModel.ask(question: String) {
        onAction(ArchiveAction.OnQuestionChange(question))
        onAction(ArchiveAction.OnAskClick)
    }

    @Test
    fun offTheOffLineHoldsThroughEveryStateOfAQuestion() = runTest {
        status.record(day.bondId, SearchByMeaning(enabled = false, active = false))
        val list = remembering()
        assertEquals(MeaningNote.Off, list.state.value.meaningNote, "before a question")

        val gate = CompletableDeferred<Unit>().also { rememberer.gates["soup"] = it }
        list.ask("soup")
        assertEquals(true, list.state.value.asking)
        assertEquals(MeaningNote.Off, list.state.value.meaningNote, "asking")
        gate.complete(Unit)
        assertEquals(emptyList(), list.state.value.results)
        assertEquals(MeaningNote.Off, list.state.value.meaningNote, "nothing close")

        rememberer.failNext = RememberError.RateLimited(retryAfterSeconds = 30)
        list.ask("rice")
        assertEquals(MeaningNote.Off, list.state.value.meaningNote, "a 429")
    }

    @Test
    fun aFailedQuestionKeepsTheLine() = runTest {
        status.record(day.bondId, SearchByMeaning(enabled = false, active = false))
        val list = remembering()
        rememberer.failNext = RememberError.Failed(DataError.Network.NoInternet)
        list.ask("soup")
        assertEquals(MeaningNote.Off, list.state.value.meaningNote)
    }

    @Test
    fun onButNotActiveHasItsOwnLineNotTheOffOne() = runTest {
        // Production today: the couple has it on, the server has no provider.
        status.record(day.bondId, SearchByMeaning(enabled = true, active = false))
        assertEquals(MeaningNote.Unavailable, remembering().state.value.meaningNote)
    }

    @Test
    fun activeOrUnknownThereIsNoLine() = runTest {
        val list = remembering()
        assertNull(list.state.value.meaningNote, "not known yet: no claim either way")

        status.record(day.bondId, SearchByMeaning(enabled = true, active = true))
        assertNull(list.state.value.meaningNote)
    }

    @Test
    fun theOffLineOpensSearchByMeaningInBondSettings() = runTest {
        status.record(day.bondId, SearchByMeaning(enabled = false, active = false))
        val list = remembering()

        list.onAction(ArchiveAction.OnSearchSettingsClick)

        assertEquals(ArchiveEvent.NavigateToSearchSettings(day.bondId), list.events.first())
    }
}
