package com.moyi.app.feature.today

import com.moyi.app.domain.archive.ArchivePage
import com.moyi.app.domain.bond.BondStatus
import com.moyi.app.domain.bond.BondType
import com.moyi.app.domain.bond.SharedSettingsSnapshot
import com.moyi.app.domain.bond.TimeOfDay
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
 * The search field's helper follows search by meaning (owner ruling 2026-11-17, 8): it says
 * search also finds by meaning only while the bond, as this device last read it, has it on.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ArchiveSearchByMeaningTest : MainDispatcherTest() {
    private val day = dayView(TodayStatus.Revealed)
    private val loop = FakeDailyLoop(DailyLoopState.Ready(day))
    private val repository = FakeArchiveRepository()
    private val feed = ArchiveFeed(loop, repository, FakeArchiveCache())
    private val settings = FakeSettingsNotices()

    private fun snapshot(on: Boolean) = SharedSettingsSnapshot(
        bondId = day.bondId,
        status = BondStatus.ACTIVE,
        name = "Us",
        type = BondType.COUPLE,
        anchorTimezone = "Europe/London",
        revealTime = TimeOfDay(21, 0),
        strictMode = false,
        semanticSearchEnabled = on,
        deletionScheduled = false,
    )

    private fun TestScope.searching(): ArchiveViewModel {
        repository.pages[null] = ArchivePage(listOf(revealedDay("2026-09-02")), null)
        return ArchiveViewModel(feed, settings).also { viewModel ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
            viewModel.onAction(ArchiveAction.OnFilterClick(ArchiveFilter.Search))
        }
    }

    @Test
    fun onForThisBondTheHelperSaysItAlsoFindsByMeaning() = runTest {
        settings.snapshots.value = mapOf(day.bondId to snapshot(on = true))

        assertTrue(searching().state.value.searchByMeaning)
    }

    @Test
    fun offTheHelperSaysWordsOnly() = runTest {
        settings.snapshots.value = mapOf(day.bondId to snapshot(on = false))

        assertFalse(searching().state.value.searchByMeaning)
    }

    @Test
    fun unknownTheHelperPromisesNoMoreThanWords() = runTest {
        settings.snapshots.value = mapOf("another-bond" to snapshot(on = true))

        assertFalse(searching().state.value.searchByMeaning, "another bond's setting is not this one's")
    }

    @Test
    fun turningItOffElsewhereChangesTheHelperInPlace() = runTest {
        settings.snapshots.value = mapOf(day.bondId to snapshot(on = true))
        val list = searching()

        settings.snapshots.value = mapOf(day.bondId to snapshot(on = false))

        assertFalse(list.state.value.searchByMeaning)
    }
}
