package com.moyi.app.feature.today

import com.moyi.app.core.ApiErrorCode
import com.moyi.app.core.DataError
import com.moyi.app.core.Result
import com.moyi.app.domain.today.BondDayStatus
import com.moyi.app.domain.today.EntryStatus
import com.moyi.app.domain.today.PartnerEntry
import com.moyi.app.domain.today.Reaction
import com.moyi.app.feature.today.common.DailyLoopState
import com.moyi.app.feature.today.common.ReactResult
import com.moyi.app.feature.today.common.RepositoryDailyLoop
import com.moyi.app.feature.today.common.TodayStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Reactions on Today, through the real loop over fakes (FR-048, ADR-0037): who may react,
 * the optimistic change and its revert, and the caches (Today's and the Archive's) written
 * only once the server has taken it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReactionsDailyLoopTest {
    private val bonds = FakeBonds()
    private val entries = FakeTodayEntries()
    private val cache = FakeTodayCache()
    private val outbox = FakeOutbox()
    private val archive = FakeArchiveCache()
    private val loop = RepositoryDailyLoop(bonds, entries, cache, outbox, outbox, FixedClock(NOON_ON_THE_DAY), archive)

    private val ready get() = assertIs<DailyLoopState.Ready>(loop.state.value).day
    private val mine = MY_ENTRY.copy(status = EntryStatus.REVEALED)

    private fun revealed(myReaction: Reaction? = null, theirReaction: Reaction? = null) = today(
        BondDayStatus.REVEALED,
        my = mine.copy(reaction = theirReaction),
        theirs = PartnerEntry.Revealed("entry-2", THEIRS, reaction = myReaction),
    )

    private fun cachedReaction(): Reaction? = (cache.stored?.today?.partnerEntry as? PartnerEntry.Revealed)?.reaction

    // ---- Who may react ----

    @Test
    fun aRevealedDayOffersTheSetAndShowsBothReactions() = runTest {
        entries.todayResult = Result.Success(revealed(myReaction = Reaction.SMILE, theirReaction = Reaction.MOVED))
        loop.refresh()
        assertTrue(ready.canReact)
        assertEquals(Reaction.SMILE, ready.myReaction)
        assertEquals(Reaction.MOVED, ready.partnerReaction)
    }

    @Test
    fun myTombstoneStillLetsMeReactToTheirsButCarriesNoReaction() = runTest {
        entries.todayResult = Result.Success(
            today(BondDayStatus.REVEALED, my = mine.copy(text = null, status = EntryStatus.DELETED), theirs = PartnerEntry.Revealed("entry-2", THEIRS)),
        )
        loop.refresh()
        assertEquals(TodayStatus.Tombstone, ready.status)
        assertTrue(ready.canReact)
        assertNull(ready.partnerReaction)
    }

    @Test
    fun beforeTheRevealOnASoloDayOrOnTheirTombstoneThereIsNothingToReactTo() = runTest {
        for (day in listOf(
            today(BondDayStatus.PENDING_REVEAL, my = MY_ENTRY, theirs = PartnerEntry.Locked),
            today(BondDayStatus.SOLO, theirs = PartnerEntry.Submitted("entry-2", THEIRS)),
            today(BondDayStatus.REVEALED, my = mine, theirs = PartnerEntry.Deleted("entry-2")),
        )) {
            entries.todayResult = Result.Success(day)
            loop.refresh()
            assertFalse(ready.canReact, "$day")
            assertEquals(ReactResult.Failed, loop.react(Reaction.HEART))
        }
        assertTrue(entries.reactions.isEmpty(), "no call is ever made")
    }

    // ---- React, change, take back ----

    @Test
    fun aReactionShowsAtOnceAndIsCachedOnlyOnceTheServerHasIt() = runTest {
        entries.todayResult = Result.Success(revealed())
        loop.refresh()
        val gate = CompletableDeferred<Unit>().also { entries.reactGate = it }

        val react = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            assertEquals(ReactResult.Done, loop.react(Reaction.HEART))
        }
        assertEquals(Reaction.HEART, ready.myReaction, "optimistic: shown before the 204")
        assertNull(cachedReaction(), "never cached before the server has it")
        assertTrue(archive.reactions.isEmpty())

        gate.complete(Unit)
        react.join()
        assertEquals(listOf<Pair<String, Reaction?>>("entry-2" to Reaction.HEART), entries.reactions)
        assertEquals(Reaction.HEART, cachedReaction())
        assertEquals(listOf<Triple<String, String, Reaction?>>(Triple("2026-09-14", "entry-2", Reaction.HEART)), archive.reactions, "the Archive's copy of the day follows")
    }

    @Test
    fun anotherReplacesItAndTheSameOneIsTakenBack() = runTest {
        entries.todayResult = Result.Success(revealed(myReaction = Reaction.HEART))
        loop.refresh()

        assertEquals(ReactResult.Done, loop.react(Reaction.ME_TOO))
        assertEquals(Reaction.ME_TOO, ready.myReaction)
        assertEquals(ReactResult.Done, loop.react(null))
        assertNull(ready.myReaction)
        assertNull(cachedReaction())
        assertEquals(listOf<Pair<String, Reaction?>>("entry-2" to Reaction.ME_TOO, "entry-2" to null), entries.reactions)
    }

    @Test
    fun whatIsAlreadySoIsNotSentAgain() = runTest {
        entries.todayResult = Result.Success(revealed(myReaction = Reaction.HEART))
        loop.refresh()
        assertEquals(ReactResult.Done, loop.react(Reaction.HEART))
        assertTrue(entries.reactions.isEmpty())
    }

    // ---- Failure ----

    @Test
    fun offlineTheOldReactionIsBackAndNothingIsCached() = runTest {
        entries.todayResult = Result.Success(revealed(myReaction = Reaction.SMILE))
        loop.refresh()
        val writes = cache.writes.size
        entries.reactResult = Result.Failure(DataError.Network.NoInternet)

        assertEquals(ReactResult.Failed, loop.react(Reaction.HEART))
        assertEquals(Reaction.SMILE, ready.myReaction)
        assertEquals(Reaction.SMILE, cachedReaction())
        assertEquals(writes, cache.writes.size, "nothing written")
        assertTrue(archive.reactions.isEmpty())
    }

    @Test
    fun aRefusalThatMeansTheDayMovedReadsItAgain() = runTest {
        entries.todayResult = Result.Success(revealed())
        loop.refresh()
        val calls = entries.todayCalls.size
        entries.reactResult = Result.Failure(api(409, ApiErrorCode.ENTRY_IMMUTABLE))
        // The server's day now: they deleted theirs.
        entries.todayResult = Result.Success(today(BondDayStatus.REVEALED, my = mine, theirs = PartnerEntry.Deleted("entry-2")))

        assertEquals(ReactResult.Failed, loop.react(Reaction.HEART))
        assertEquals(calls + 1, entries.todayCalls.size)
        assertFalse(ready.canReact)
    }
}
