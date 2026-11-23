package com.moyi.app.feature.today

import com.moyi.app.domain.archive.ArchivePage
import com.moyi.app.domain.bond.InMemorySearchByMeaningStatus
import com.moyi.app.domain.bond.SearchByMeaning
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
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The search field's helper follows `semanticSearchActive` (owner ruling 2026-11-17, 8; ADR-0070):
 * it says search also finds by meaning only while the server asks by meaning now, not merely while
 * the bond's setting is on.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ArchiveSearchByMeaningTest : MainDispatcherTest() {
    private val day = dayView(TodayStatus.Revealed)
    private val loop = FakeDailyLoop(DailyLoopState.Ready(day))
    private val repository = FakeArchiveRepository()
    private val feed = ArchiveFeed(loop, repository, FakeArchiveCache(), FakeRememberRepository())
    private val status = InMemorySearchByMeaningStatus()

    private fun TestScope.searching(): ArchiveViewModel {
        repository.pages[null] = ArchivePage(listOf(revealedDay("2026-09-02")), null)
        return ArchiveViewModel(feed, status).also { viewModel ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
            viewModel.onAction(ArchiveAction.OnFilterClick(ArchiveFilter.Search))
        }
    }

    @Test
    fun activeTheHelperSaysItAlsoFindsByMeaning() = runTest {
        status.record(day.bondId, SearchByMeaning(enabled = true, active = true))

        assertTrue(searching().state.value.searchByMeaning)
    }

    @Test
    fun onButNotActiveTheHelperSaysWordsOnly() = runTest {
        // Production today: the setting is on, but no provider is configured.
        status.record(day.bondId, SearchByMeaning(enabled = true, active = false))

        assertFalse(searching().state.value.searchByMeaning)
    }

    @Test
    fun offTheHelperSaysWordsOnly() = runTest {
        status.record(day.bondId, SearchByMeaning(enabled = false, active = false))

        assertFalse(searching().state.value.searchByMeaning)
    }

    @Test
    fun unknownTheHelperPromisesNoMoreThanWords() = runTest {
        status.record("another-bond", SearchByMeaning(enabled = true, active = true))

        assertFalse(searching().state.value.searchByMeaning, "another bond's status is not this one's")
    }

    @Test
    fun aLaterReadChangesTheHelperInPlace() = runTest {
        status.record(day.bondId, SearchByMeaning(enabled = true, active = true))
        val list = searching()

        status.record(day.bondId, SearchByMeaning(enabled = false, active = false))

        assertFalse(list.state.value.searchByMeaning)
    }
}
