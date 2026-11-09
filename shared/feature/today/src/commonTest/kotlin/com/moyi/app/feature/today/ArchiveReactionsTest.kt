package com.moyi.app.feature.today

import com.moyi.app.core.ApiErrorCode
import com.moyi.app.core.DataError
import com.moyi.app.core.Result
import com.moyi.app.domain.archive.ArchivePage
import com.moyi.app.domain.bond.BondStatus
import com.moyi.app.domain.today.BondDayStatus
import com.moyi.app.domain.today.PartnerEntry
import com.moyi.app.domain.today.Reaction
import com.moyi.app.feature.today.archive.ArchiveDayAction
import com.moyi.app.feature.today.archive.ArchiveDayViewModel
import com.moyi.app.feature.today.archive.ArchiveFeed
import com.moyi.app.feature.today.common.DailyLoopState
import com.moyi.app.feature.today.common.TodayStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Reactions in the Archive day view (FR-048, ADR-0037) over fakes: react, change, take back,
 * a failure put back and said, the cache kept only on the server's word, the list following,
 * and the set offered only on the partner's revealed entry.
 */
class ArchiveReactionsTest : MainDispatcherTest() {
    private val loop = FakeDailyLoop(DailyLoopState.Ready(dayView(TodayStatus.Revealed)))
    private val repository = FakeArchiveRepository()
    private val entries = FakeEntries()
    private val cache = FakeArchiveCache()
    private val feed = ArchiveFeed(loop, repository, cache)

    private fun dayView(date: String) = ArchiveDayViewModel(date, feed, repository, entries, loop)

    private fun reacted(date: String, mine: Reaction? = null, theirs: Reaction? = null) = revealedDay(date).let { day ->
        day.copy(
            myEntry = day.myEntry?.copy(reaction = theirs),
            partnerEntry = (day.partnerEntry as PartnerEntry.Revealed).copy(reaction = mine),
        )
    }

    private suspend fun cachedReaction(date: String): Reaction? = (cache.day("bond-1", date)?.partnerEntry as? PartnerEntry.Revealed)?.reaction

    private suspend fun seed(vararg days: com.moyi.app.domain.archive.ArchiveDay) {
        val page = ArchivePage(days.toList(), null)
        repository.pages[null] = page
        cache.putPage("bond-1", page, after = null)
    }

    @Test
    fun theSetIsOfferedUnderTheirRevealedEntryAndTheirReactionShowsUnderMine() = runTest {
        seed(reacted("2026-09-02", mine = Reaction.HEART, theirs = Reaction.ME_TOO))
        val day = dayView("2026-09-02").state.value.day
        assertEquals(true, day?.canReact)
        assertEquals(Reaction.HEART, day?.myReaction)
        assertEquals(Reaction.ME_TOO, day?.partnerReaction)
    }

    @Test
    fun noSetOnASoloDayOrTheirTombstone() = runTest {
        val solo = revealedDay("2026-09-03").copy(status = BondDayStatus.SOLO, myEntry = null, partnerEntry = PartnerEntry.Submitted("theirs-2026-09-03", THEIRS))
        val tombstone = revealedDay("2026-09-02").copy(partnerEntry = PartnerEntry.Deleted("theirs-2026-09-02"))
        seed(solo, tombstone)
        for (date in listOf("2026-09-03", "2026-09-02")) {
            val view = dayView(date)
            assertEquals(false, view.state.value.day?.canReact, date)
            view.onAction(ArchiveDayAction.OnReactionClick(Reaction.HEART))
        }
        assertTrue(entries.reactions.isEmpty())
    }

    @Test
    fun aReactionIsShownAtOnceAndCachedOnlyOnTheServersWord() = runTest {
        seed(revealedDay("2026-09-02"))
        feed.loadFirst()
        val view = dayView("2026-09-02")
        val gate = CompletableDeferred<Unit>().also { entries.reactGate = it }

        view.onAction(ArchiveDayAction.OnReactionClick(Reaction.THANK_YOU))
        assertEquals(Reaction.THANK_YOU, view.state.value.day?.myReaction, "optimistic")
        assertEquals(Reaction.THANK_YOU, (feed.find("2026-09-02")?.partnerEntry as PartnerEntry.Revealed).reaction, "the list follows")
        assertNull(cachedReaction("2026-09-02"), "not cached yet")

        gate.complete(Unit)
        assertEquals(listOf<Pair<String, Reaction?>>("theirs-2026-09-02" to Reaction.THANK_YOU), entries.reactions)
        assertEquals(Reaction.THANK_YOU, cachedReaction("2026-09-02"))
        assertFalse(view.state.value.reactionFailed)
    }

    @Test
    fun anotherReplacesItAndTheSameTakesItBack() = runTest {
        seed(reacted("2026-09-02", mine = Reaction.HEART))
        val view = dayView("2026-09-02")

        view.onAction(ArchiveDayAction.OnReactionClick(Reaction.MOVED))
        assertEquals(Reaction.MOVED, view.state.value.day?.myReaction)
        assertEquals(Reaction.MOVED, cachedReaction("2026-09-02"))

        view.onAction(ArchiveDayAction.OnReactionClick(Reaction.MOVED))
        assertNull(view.state.value.day?.myReaction)
        assertNull(cachedReaction("2026-09-02"))
        assertEquals(listOf<Pair<String, Reaction?>>("theirs-2026-09-02" to Reaction.MOVED, "theirs-2026-09-02" to null), entries.reactions)
    }

    @Test
    fun offlineTheOldReactionIsBackAndTheLineSaysSo() = runTest {
        seed(reacted("2026-09-02", mine = Reaction.SMILE))
        feed.loadFirst()
        val view = dayView("2026-09-02")
        entries.reactResult = Result.Failure(DataError.Network.NoInternet)

        view.onAction(ArchiveDayAction.OnReactionClick(Reaction.HEART))

        assertEquals(Reaction.SMILE, view.state.value.day?.myReaction)
        assertEquals(Reaction.SMILE, (feed.find("2026-09-02")?.partnerEntry as PartnerEntry.Revealed).reaction)
        assertEquals(Reaction.SMILE, cachedReaction("2026-09-02"), "the cache never saw the failed one")
        assertTrue(view.state.value.reactionFailed)
        assertTrue(cache.reactions.isEmpty())
    }

    @Test
    fun aTapWhileOneIsOnItsWayIsIgnoredAndARefreshMeanwhileKeepsTheTap() = runTest {
        seed(revealedDay("2026-09-02"))
        val view = dayView("2026-09-02")
        val gate = CompletableDeferred<Unit>().also { entries.reactGate = it }

        view.onAction(ArchiveDayAction.OnReactionClick(Reaction.HEART))
        view.onAction(ArchiveDayAction.OnReactionClick(Reaction.SMILE))
        // A refresh answering meanwhile (the server has not applied it yet) keeps what was tapped.
        view.onAction(ArchiveDayAction.OnRetryClick)
        assertEquals(Reaction.HEART, view.state.value.day?.myReaction)

        gate.complete(Unit)
        assertEquals(listOf<Pair<String, Reaction?>>("theirs-2026-09-02" to Reaction.HEART), entries.reactions)
    }

    @Test
    fun aRefusalThatMeansTheDayMovedReadsItAgain() = runTest {
        seed(revealedDay("2026-09-02"))
        val view = dayView("2026-09-02")
        val calls = repository.dayCalls.size
        entries.reactResult = Result.Failure(api(409, ApiErrorCode.BOND_ARCHIVED))

        view.onAction(ArchiveDayAction.OnReactionClick(Reaction.HEART))

        assertEquals(calls + 1, repository.dayCalls.size)
        assertTrue(view.state.value.reactionFailed)
    }

    @Test
    fun reactingOnTheDayTodayShowsRefreshesToday() = runTest {
        // FakeDailyLoop's day is 2026-09-14.
        seed(revealedDay("2026-09-14"))
        val view = dayView("2026-09-14")
        val before = loop.refreshes
        view.onAction(ArchiveDayAction.OnReactionClick(Reaction.HEART))
        assertEquals(before + 1, loop.refreshes)
    }

    // ---- Owner ruling 5 (2026-11-09): no set on a bond that takes no writes ----

    private fun onBond(status: BondStatus?) {
        loop.show(dayView(TodayStatus.Revealed).copy(bondStatus = status))
    }

    @Test
    fun anEndedBondOrOneInItsCoolingOffHidesTheSetAndKeepsTheirReactionOnMine() = runTest {
        for (status in listOf(BondStatus.ARCHIVED, BondStatus.PENDING_DELETION, BondStatus.DELETED)) {
            onBond(status)
            seed(reacted("2026-09-02", mine = Reaction.HEART, theirs = Reaction.SMILE))
            val view = dayView("2026-09-02")
            val day = view.state.value.day
            assertEquals(false, day?.canReact, "$status")
            // Reading what the partner left is not a write: it stays under my entry.
            assertEquals(Reaction.SMILE, day?.partnerReaction, "$status")
            view.onAction(ArchiveDayAction.OnReactionClick(Reaction.MOVED))
        }
        assertTrue(entries.reactions.isEmpty(), "no call is ever made where the server would refuse it")
    }

    @Test
    fun theListsBondStatusDecidesAndALiveBondKeepsTheSet() = runTest {
        onBond(BondStatus.ARCHIVED)
        seed(revealedDay("2026-09-02"))
        feed.loadFirst()
        assertEquals(BondStatus.ARCHIVED, feed.state.value.bondStatus)
        assertEquals(false, dayView("2026-09-02").state.value.day?.canReact)

        onBond(BondStatus.ACTIVE)
        feed.loadFirst()
        assertEquals(true, dayView("2026-09-02").state.value.day?.canReact)
    }

    @Test
    fun anUnknownStatusKeepsTheSetAsBefore() = runTest {
        onBond(null)
        seed(revealedDay("2026-09-02"))
        assertEquals(true, dayView("2026-09-02").state.value.day?.canReact)
    }
}
