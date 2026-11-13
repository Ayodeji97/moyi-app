package com.moyi.app.feature.today

import app.cash.turbine.test
import com.moyi.app.core.Result
import com.moyi.app.domain.bond.BondStatus
import com.moyi.app.domain.bond.EmptyBondExit
import com.moyi.app.domain.today.BondDayStatus
import com.moyi.app.feature.today.common.DailyLoopState
import com.moyi.app.feature.today.common.RepositoryDailyLoop
import com.moyi.app.feature.today.common.RevealLedger
import com.moyi.app.feature.today.common.TodayStatus
import com.moyi.app.feature.today.today.TodayAction
import com.moyi.app.feature.today.today.TodayEvent
import com.moyi.app.feature.today.today.TodayLoad
import com.moyi.app.feature.today.today.TodayViewModel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Joining with a code from a bond nobody has joined, as Today sees it (owner ruling 2026-11-13):
 * which bond it shows when there are two, the leave it retries, and where "I have an invite
 * code" is offered.
 */
class JoinFromEmptyBondTodayTest : MainDispatcherTest() {
    private val bonds = FakeBonds()
    private val entries = FakeTodayEntries()
    private val exit = CountingExit()
    private val loop = RepositoryDailyLoop(
        bonds, entries, FakeTodayCache(), FakeOutbox(), FakeOutbox(), FixedClock(NOON_ON_THE_DAY), FakeArchiveCache(), emptyBonds = exit,
    )

    /** The person's own bond, created after the one they joined, still waiting for its second member. */
    private val empty = ACTIVE.copy(id = "empty", name = "Esther's space", status = BondStatus.PENDING_MEMBER, partnerName = null, activeMembers = 1)
    private val joined = ACTIVE.copy(id = "joined", activeMembers = 2)

    private val ready get() = assertIs<DailyLoopState.Ready>(loop.state.value).day

    @Test
    fun todayShowsTheBondWithSomeoneInItOverANewerEmptyOne() = runTest {
        // Newest first, as GET /bonds lists them: the empty bond whose leave did not go through.
        bonds.result = Result.Success(listOf(empty, joined))
        loop.refresh()
        assertEquals("joined", ready.bondId)
        assertEquals(listOf("joined"), entries.todayCalls)
    }

    @Test
    fun withOnlyTheEmptyBondTodayStillShowsIt() = runTest {
        bonds.result = Result.Success(listOf(empty, joined.copy(status = BondStatus.ARCHIVED)))
        loop.refresh()
        assertEquals("empty", ready.bondId)
    }

    @Test
    fun everyRefreshFirstFinishesAKeptLeave() = runTest {
        bonds.result = Result.Success(listOf(joined))
        loop.refresh()
        loop.refresh()
        assertEquals(2, exit.settles)
    }

    @Test
    fun aRetryThatThrowsNeverFailsToday() = runTest {
        exit.throws = true
        bonds.result = Result.Success(listOf(joined))
        loop.refresh()
        assertEquals("joined", ready.bondId)
    }

    @Test
    fun beforeWritingInAnEmptyBondTheLinkIsOfferedAndOpensTheJoinFlowFromIt() = runTest {
        bonds.result = Result.Success(listOf(empty))
        val viewModel = TodayViewModel(loop, RevealLedger { "me" }, bonds, FakeOutbox(), FakeNotificationAskMemory())
        viewModel.state.test {
            viewModel.onAction(TodayAction.OnForeground)
            val shown = expectMostRecentItem()
            assertEquals(TodayLoad.Ready, shown.load)
            assertEquals(TodayStatus.NotWritten, shown.status)
            assertTrue(shown.canJoinWithCode)
        }
        viewModel.events.test {
            viewModel.onAction(TodayAction.OnHaveCodeClick)
            assertEquals(TodayEvent.NavigateToJoin(bondId = "empty", bondName = "Esther's space"), awaitItem())
        }
    }

    @Test
    fun afterWritingInAnEmptyBondTheLinkIsStillOffered() = runTest {
        bonds.result = Result.Success(listOf(empty))
        entries.todayResult = Result.Success(today(BondDayStatus.OPEN, my = MY_ENTRY))
        val viewModel = TodayViewModel(loop, RevealLedger { "me" }, bonds, FakeOutbox(), FakeNotificationAskMemory())
        viewModel.state.test {
            viewModel.onAction(TodayAction.OnForeground)
            val shown = expectMostRecentItem()
            assertEquals(TodayStatus.PartnerNotJoined, shown.status)
            assertTrue(shown.canJoinWithCode)
        }
    }

    @Test
    fun onceSomeoneHasJoinedTheLinkIsGoneAndATapDoesNothing() = runTest {
        bonds.result = Result.Success(listOf(joined))
        val viewModel = TodayViewModel(loop, RevealLedger { "me" }, bonds, FakeOutbox(), FakeNotificationAskMemory())
        viewModel.state.test {
            viewModel.onAction(TodayAction.OnForeground)
            val shown = expectMostRecentItem()
            assertEquals(TodayLoad.Ready, shown.load)
            assertFalse(shown.canJoinWithCode)
        }
        viewModel.events.test {
            viewModel.onAction(TodayAction.OnHaveCodeClick)
            expectNoEvents()
        }
    }

    private class CountingExit : EmptyBondExit {
        var settles = 0
        var throws = false

        override suspend fun leaveAfterJoining(emptyBondId: String, joinedBondId: String) = Unit

        override suspend fun settle() {
            settles++
            if (throws) error("disk")
        }
    }
}
