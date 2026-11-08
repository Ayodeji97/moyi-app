package com.moyi.app.feature.today

import com.moyi.app.core.DataError
import com.moyi.app.core.Result
import com.moyi.app.domain.bond.BondStatus
import com.moyi.app.domain.today.BondDayStatus
import com.moyi.app.domain.today.CachedDay
import com.moyi.app.domain.today.EntryStatus
import com.moyi.app.domain.today.MyEntry
import com.moyi.app.domain.today.PartnerEntry
import com.moyi.app.feature.today.common.DailyLoopState
import com.moyi.app.feature.today.common.DeleteResult
import com.moyi.app.feature.today.common.PartnerVisible
import com.moyi.app.feature.today.common.RepositoryDailyLoop
import com.moyi.app.feature.today.common.TodayStatus
import com.moyi.app.feature.today.common.words
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Instant

/**
 * [RepositoryDailyLoop] over a [FakeTodayCache]: the cached day first, the server's
 * after it, and only what may be kept is kept (doc 13 §5).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TodayCacheThenNetworkTest {
    private val bonds = FakeBonds()
    private val entries = FakeTodayEntries()
    private val cache = FakeTodayCache()
    private val clock = FixedClock(NOON_ON_THE_DAY)
    private val loop = RepositoryDailyLoop(bonds, entries, cache, clock)

    private val ready get() = assertIs<DailyLoopState.Ready>(loop.state.value).day

    private val waitingDay = today(BondDayStatus.PARTIAL, my = MY_ENTRY, theirs = null)

    // ---- Cache, then network ----

    @Test
    fun aCachedDayIsDrawnAtOnceThenTheServersReplacesIt() = runTest {
        cache.stored = CachedDay(ACTIVE, today(BondDayStatus.OPEN))
        val answer = CompletableDeferred<Unit>()
        entries.todayGate = answer
        entries.todayResult = Result.Success(waitingDay)

        launch(UnconfinedTestDispatcher(testScheduler)) { loop.refresh() }

        assertEquals(TodayStatus.NotWritten, ready.status, "the cached day, before any answer")
        // Both requests are already out, side by side: the cache gave the bond id.
        assertEquals(1, bonds.calls)
        assertEquals(listOf("bond-1"), entries.todayCalls)

        answer.complete(Unit)
        assertEquals(TodayStatus.Waiting, ready.status)
        assertEquals(CachedDay(ACTIVE, waitingDay), cache.stored, "every successful fetch replaces the cached day")
    }

    @Test
    fun aFailedRefreshKeepsTheCachedDayAndShowsNoError() = runTest {
        cache.stored = CachedDay(ACTIVE, waitingDay)
        bonds.result = Result.Failure(DataError.Network.NoInternet)
        entries.todayResult = Result.Failure(DataError.Network.NoInternet)

        loop.refresh()

        assertEquals(TodayStatus.Waiting, ready.status)
        assertEquals(MINE, ready.myEntry)
        assertEquals(emptyList(), cache.writes, "a cached day is never written back as if the server had said it")
    }

    @Test
    fun withNoCacheTheFirstLoadIsAsBefore() = runTest {
        bonds.result = Result.Failure(DataError.Network.NoInternet)
        loop.refresh()
        assertEquals(DailyLoopState.Failed, loop.state.value)
        assertEquals(emptyList(), cache.writes)

        bonds.result = Result.Success(listOf(ACTIVE))
        loop.refresh()
        assertEquals(TodayStatus.NotWritten, ready.status)
        assertEquals(CachedDay(ACTIVE, today()), cache.stored)
    }

    @Test
    fun theCacheIsReadOnlyOnTheFirstLoadAfterAClear() = runTest {
        loop.refresh()
        cache.stored = CachedDay(ACTIVE, waitingDay)
        val answer = CompletableDeferred<Unit>()
        entries.todayGate = answer
        launch(UnconfinedTestDispatcher(testScheduler)) { loop.refresh() }
        assertEquals(TodayStatus.NotWritten, ready.status, "a later refresh refreshes behind what is shown, not behind the cache")
        answer.complete(Unit)
    }

    // ---- Never yesterday's day as today ----

    @Test
    fun aCachedDayFromAnEarlierBondDayIsNotShown() = runTest {
        cache.stored = CachedDay(ACTIVE, waitingDay.copy(date = "2026-09-13"))
        val answer = CompletableDeferred<Unit>()
        entries.todayGate = answer

        launch(UnconfinedTestDispatcher(testScheduler)) { loop.refresh() }

        assertEquals(DailyLoopState.Loading, loop.state.value, "yesterday's day must never be drawn as today")
        // It still names the bond: the day is already being asked for, beside the bonds.
        assertEquals(listOf("bond-1"), entries.todayCalls)
        answer.complete(Unit)
        assertEquals(listOf("bond-1"), entries.todayCalls, "no second, chained request for the day")
        assertEquals(TodayStatus.NotWritten, ready.status)
    }

    @Test
    fun theBondDayIsTheDateInTheBondsOwnZoneNotThePhones() = runTest {
        // 23:30 UTC on the 13th is already 00:30 on the 14th in Lagos.
        clock.instant = Instant.parse("2026-09-13T23:30:00Z")
        val gate = { CompletableDeferred<Unit>().also { entries.todayGate = it } }

        cache.stored = CachedDay(ACTIVE, waitingDay.copy(date = "2026-09-13"))
        var answer = gate()
        launch(UnconfinedTestDispatcher(testScheduler)) { loop.refresh() }
        assertEquals(DailyLoopState.Loading, loop.state.value, "the 13th is over in Lagos")
        answer.complete(Unit)

        loop.clear()
        cache.stored = CachedDay(ACTIVE, waitingDay)
        answer = gate()
        launch(UnconfinedTestDispatcher(testScheduler)) { loop.refresh() }
        assertEquals(TodayStatus.Waiting, ready.status, "the 14th has begun in Lagos")
        answer.complete(Unit)
    }

    @Test
    fun aBondWithNoKnownZoneShowsNoCachedDay() = runTest {
        for (zone in listOf(null, "Not/AZone")) {
            loop.clear()
            cache.stored = CachedDay(ACTIVE.copy(anchorTimezone = zone), waitingDay)
            val answer = CompletableDeferred<Unit>()
            entries.todayGate = answer
            launch(UnconfinedTestDispatcher(testScheduler)) { loop.refresh() }
            assertEquals(DailyLoopState.Loading, loop.state.value, "zone $zone")
            answer.complete(Unit)
        }
    }

    // ---- Only what may be kept ----

    @Test
    fun thePartnersWordsAreCachedOnlyInTheLayoutsAllowedToShowThem() = runTest {
        val revealed = PartnerEntry.Revealed("entry-2", THEIRS)
        val submitted = PartnerEntry.Submitted("entry-2", THEIRS)
        val mineRevealed = MY_ENTRY.copy(status = EntryStatus.REVEALED)
        val tombstone = MyEntry("entry-1", null, EntryStatus.DELETED)
        // Every status, with words the server should never send in most of them.
        val days = listOf(
            today(BondDayStatus.REVEALED, my = mineRevealed, theirs = revealed) to TodayStatus.Revealed,
            today(BondDayStatus.REVEALED, my = tombstone, theirs = revealed) to TodayStatus.Tombstone,
            today(BondDayStatus.SOLO, my = null, theirs = submitted) to TodayStatus.SoloTheyWrote,
            today(BondDayStatus.SOLO, my = MY_ENTRY, theirs = submitted) to TodayStatus.SoloYouWrote,
            today(BondDayStatus.OPEN, my = null, theirs = submitted) to TodayStatus.NotWritten,
            today(BondDayStatus.PARTIAL, my = null, theirs = revealed) to TodayStatus.NotWritten,
            today(BondDayStatus.PARTIAL, my = MY_ENTRY, theirs = submitted) to TodayStatus.Waiting,
            today(BondDayStatus.PENDING_REVEAL, my = MY_ENTRY, theirs = revealed) to TodayStatus.Waiting,
            today(BondDayStatus.SUSPENDED, my = MY_ENTRY, theirs = revealed) to TodayStatus.Waiting,
            today(BondDayStatus.EMPTY, my = null, theirs = revealed) to TodayStatus.NotWritten,
            today(BondDayStatus.FROZEN, my = MY_ENTRY, theirs = submitted) to TodayStatus.SoloYouWrote,
        )
        for ((day, layout) in days) {
            entries.todayResult = Result.Success(day)
            loop.refresh()
            assertEquals(layout, ready.status, "$day")
            val kept = cache.writes.last().today
            if (layout in PartnerVisible) {
                assertEquals(THEIRS, kept.partnerEntry.words(), "$layout may keep the partner's words")
            } else {
                assertNull(kept.partnerEntry.words(), "$layout must never keep the partner's words")
                assertEquals(PartnerEntry.Locked, kept.partnerEntry)
            }
            // Read back, the kept day draws the same layout as the server's did.
            loop.clear()
            val answer = CompletableDeferred<Unit>()
            entries.todayGate = answer
            launch(UnconfinedTestDispatcher(testScheduler)) { loop.refresh() }
            assertEquals(layout, ready.status, "cached $layout")
            if (layout !in PartnerVisible) assertNull(ready.partnerEntry)
            answer.complete(Unit)
        }
    }

    @Test
    fun theInviteIsNotCached() = runTest {
        bonds.result = Result.Success(listOf(ACTIVE.copy(invite = com.moyi.app.domain.bond.Invite("i1", "K7Q2PD", "https://getmoyi.app/i/K7Q2PD"))))
        loop.refresh()
        assertNull(cache.stored?.bond?.invite)
    }

    // ---- Writes ----

    @Test
    fun anAcceptedSendIsCachedEvenIfTheRefreshAfterItFails() = runTest {
        loop.refresh()
        entries.todayResult = Result.Failure(DataError.Network.Timeout)
        loop.submit(MINE, promptId = null, idempotencyKey = "k1")
        assertEquals(MY_ENTRY, cache.stored?.today?.myEntry)
    }

    @Test
    fun anOptimisticDeleteIsCachedOnlyOnceTheServerAgrees() = runTest {
        entries.todayResult = Result.Success(waitingDay)
        loop.refresh()
        val release = CompletableDeferred<Unit>()
        entries.deleteGate = release
        entries.deleteResult = Result.Failure(DataError.Network.NoInternet)

        val delete = async(UnconfinedTestDispatcher(testScheduler)) { loop.deleteBeforeReveal() }
        assertEquals(TodayStatus.NotWritten, ready.status)
        assertEquals(MY_ENTRY, cache.stored?.today?.myEntry, "not the cache's until the server agrees")
        release.complete(Unit)

        assertEquals(DeleteResult.Failed, delete.await())
        assertEquals(MY_ENTRY, cache.stored?.today?.myEntry)
    }

    @Test
    fun noOpenBondForgetsTheCachedDay() = runTest {
        cache.stored = CachedDay(ACTIVE, waitingDay)
        bonds.result = Result.Success(listOf(ACTIVE.copy(status = BondStatus.ARCHIVED)))
        loop.refresh()
        assertEquals(DailyLoopState.NoOpenBond, loop.state.value)
        assertNull(cache.stored)
    }

    @Test
    fun aClearWhileTheCacheIsReadKeepsItsDayOutOfTheNextSession() = runTest {
        cache.stored = CachedDay(ACTIVE, waitingDay)
        val slowCache = object : com.moyi.app.domain.today.TodayCache by cache {
            val gate = CompletableDeferred<Unit>()
            override suspend fun read(): CachedDay? {
                gate.await()
                return cache.read()
            }
        }
        val slowLoop = RepositoryDailyLoop(bonds, entries, slowCache, clock)
        bonds.result = Result.Failure(DataError.Network.NoInternet)
        launch(UnconfinedTestDispatcher(testScheduler)) { slowLoop.refresh() }

        slowLoop.clear() // sign-out
        slowCache.gate.complete(Unit)

        assertIs<DailyLoopState.Loading>(slowLoop.state.value)
    }
}

