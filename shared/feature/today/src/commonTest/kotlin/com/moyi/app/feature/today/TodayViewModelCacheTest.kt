package com.moyi.app.feature.today

import app.cash.turbine.test
import com.moyi.app.core.DataError
import com.moyi.app.core.Result
import com.moyi.app.domain.today.BondDayStatus
import com.moyi.app.domain.today.CachedDay
import com.moyi.app.domain.today.EntryStatus
import com.moyi.app.domain.today.PartnerEntry
import com.moyi.app.feature.today.common.RepositoryDailyLoop
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

/**
 * Today's ViewModel over the real [RepositoryDailyLoop] and a cached day: what the
 * screen shows first, what it shows after the refresh, and the reveal.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModelCacheTest : MainDispatcherTest() {
    private val bonds = FakeBonds()
    private val entries = FakeTodayEntries()
    private val cache = FakeTodayCache()
    private val ledger = RevealLedger()
    private val loop = RepositoryDailyLoop(bonds, entries, cache, FixedClock(NOON_ON_THE_DAY))
    private val viewModel by lazy { TodayViewModel(loop, ledger, bonds) }

    private val waiting = today(BondDayStatus.PARTIAL, my = MY_ENTRY)
    private val revealed = today(
        BondDayStatus.REVEALED,
        my = MY_ENTRY.copy(status = EntryStatus.REVEALED),
        theirs = PartnerEntry.Revealed("entry-2", THEIRS),
    )

    private fun TestScope.collectState() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
    }

    @Test
    fun theCachedDayShowsAtOnceThenTheRefreshedOneReplacesIt() = runTest {
        cache.stored = CachedDay(ACTIVE, waiting)
        val answer = CompletableDeferred<Unit>()
        entries.todayGate = answer
        entries.todayResult = Result.Success(today(BondDayStatus.PARTIAL, my = MY_ENTRY.copy(text = "Edited on the other phone.")))
        collectState()

        viewModel.state.test {
            assertEquals(TodayLoad.Loading, awaitItem().load)
            viewModel.onAction(TodayAction.OnForeground)

            val cached = awaitItem()
            assertEquals(TodayLoad.Ready, cached.load)
            assertEquals(TodayStatus.Waiting, cached.status)
            assertEquals(MINE, cached.myEntry)

            answer.complete(Unit)
            val refreshed = awaitItem()
            assertEquals(TodayStatus.Waiting, refreshed.status)
            assertEquals("Edited on the other phone.", refreshed.myEntry)
            expectNoEvents()
        }
    }

    @Test
    fun theCachedDayStaysWhenTheRefreshFailsWithNoErrorOverIt() = runTest {
        cache.stored = CachedDay(ACTIVE, waiting)
        bonds.result = Result.Failure(DataError.Network.NoInternet)
        entries.todayResult = Result.Failure(DataError.Network.NoInternet)
        collectState()

        viewModel.onAction(TodayAction.OnForeground)

        val state = viewModel.state.value
        assertEquals(TodayLoad.Ready, state.load)
        assertEquals(TodayStatus.Waiting, state.status)
        assertEquals(MINE, state.myEntry)
        assertFalse(state.actionFailed)
    }

    @Test
    fun aCachedRevealedDayPlaysTheRevealOnceAndTheRefreshDoesNotReplayIt() = runTest {
        cache.stored = CachedDay(ACTIVE, revealed)
        entries.todayResult = Result.Success(revealed)
        collectState()

        viewModel.events.test {
            viewModel.onAction(TodayAction.OnForeground)
            assertEquals(TodayEvent.NavigateToReveal, awaitItem())
            viewModel.onAction(TodayAction.OnForeground)
            expectNoEvents()
        }
    }

    @Test
    fun aCachedWaitingDayThatTheServerSaysOpenedPlaysTheReveal() = runTest {
        // The first frame came from the cache, and the reveal must not be skipped because of it.
        cache.stored = CachedDay(ACTIVE, waiting)
        val answer = CompletableDeferred<Unit>()
        entries.todayGate = answer
        entries.todayResult = Result.Success(revealed)
        collectState()

        viewModel.events.test {
            viewModel.onAction(TodayAction.OnForeground)
            assertEquals(TodayStatus.Waiting, viewModel.state.value.status)
            expectNoEvents()

            answer.complete(Unit)
            assertEquals(TodayEvent.NavigateToReveal, awaitItem())
            assertEquals(THEIRS, viewModel.state.value.partnerEntry)
        }
    }
}
