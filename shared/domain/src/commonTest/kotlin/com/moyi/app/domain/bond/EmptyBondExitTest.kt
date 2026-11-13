package com.moyi.app.domain.bond

import com.moyi.app.core.ApiErrorCode
import com.moyi.app.core.ApiProblem
import com.moyi.app.core.DataError
import com.moyi.app.core.EmptyResult
import com.moyi.app.core.Result
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [EmptyBondExitImpl]: after a join, the empty bond is left only while it is still empty and the
 * joined bond is still open; a leave that gets no answer is kept and tried again.
 */
class EmptyBondExitTest {
    private val bonds = ListedBonds()
    private val leaver = Leaver()
    private val store = MemoryStore()
    private val exit = EmptyBondExitImpl(bonds, leaver, store)

    private val empty = bond("empty", BondStatus.PENDING_MEMBER, members = 1)
    private val joined = bond("joined", BondStatus.ACTIVE, members = 2)

    @Test
    fun theEmptyBondIsLeftOnceTheJoinSucceededKeepingItsEntries() = runTest {
        bonds.listed = listOf(joined, empty)

        exit.leaveAfterJoining(emptyBondId = "empty", joinedBondId = "joined")

        assertEquals(listOf("empty" to false), leaver.calls, "left once, withdrawEntries false: the entries are kept")
        assertTrue(store.rows.isEmpty(), "done, so nothing is left to retry")
    }

    @Test
    fun anActiveBondIsNeverLeft() = runTest {
        // Someone joined the "empty" bond between the confirm screen and now.
        bonds.listed = listOf(joined, empty.copy(status = BondStatus.ACTIVE, activeMembers = 2))

        exit.leaveAfterJoining("empty", "joined")

        assertTrue(leaver.calls.isEmpty())
        assertTrue(store.rows.isEmpty(), "forgotten, not retried")
    }

    @Test
    fun aWaitingBondWithSomeoneElseInItOrAnUnknownCountIsNotLeft() = runTest {
        bonds.listed = listOf(joined, empty.copy(activeMembers = 2))
        exit.leaveAfterJoining("empty", "joined")
        bonds.listed = listOf(joined, empty.copy(activeMembers = null))
        exit.leaveAfterJoining("empty", "joined")

        assertTrue(leaver.calls.isEmpty())
    }

    @Test
    fun theJoinedBondIsNeverLeftEvenIfAskedTo() = runTest {
        bonds.listed = listOf(empty)
        exit.leaveAfterJoining("empty", "empty")
        assertTrue(leaver.calls.isEmpty())
    }

    @Test
    fun nothingIsLeftWhenTheJoinedBondIsNoLongerOpen() = runTest {
        // Leaving would leave the person with no bond at all: the empty one stays.
        bonds.listed = listOf(joined.copy(status = BondStatus.ARCHIVED), empty)
        exit.leaveAfterJoining("empty", "joined")
        assertTrue(leaver.calls.isEmpty())
        assertTrue(store.rows.isEmpty())
    }

    @Test
    fun aLeaveWithNoAnswerIsKeptAndTriedAgainNextTime() = runTest {
        bonds.listed = listOf(joined, empty)
        leaver.answer = Result.Failure(DataError.Network.NoInternet)

        exit.leaveAfterJoining("empty", "joined")

        assertEquals(listOf(PendingLeave("empty", "joined")), store.rows, "kept on the device")
        leaver.answer = Result.Success(Unit)

        exit.settle()

        assertEquals(2, leaver.calls.size)
        assertTrue(store.rows.isEmpty())
    }

    @Test
    fun aKeptLeaveIsSkippedIfTheBondBecameActiveMeanwhile() = runTest {
        bonds.listed = listOf(joined, empty)
        leaver.answer = Result.Failure(DataError.Network.Server(503))
        exit.leaveAfterJoining("empty", "joined")
        assertEquals(1, store.rows.size)

        // Next start: the partner joined the "empty" bond after all.
        bonds.listed = listOf(joined, empty.copy(status = BondStatus.ACTIVE, activeMembers = 2))
        exit.settle()

        assertEquals(1, leaver.calls.size, "not tried a second time")
        assertTrue(store.rows.isEmpty(), "and forgotten")
    }

    @Test
    fun aKeptLeaveWaitsWhileTheBondsCannotBeRead() = runTest {
        store.rows += PendingLeave("empty", "joined")
        bonds.answer = Result.Failure(DataError.Network.Timeout)

        exit.settle()

        assertTrue(leaver.calls.isEmpty(), "never left without reading the bonds first")
        assertEquals(1, store.rows.size)
    }

    @Test
    fun aRefusalThatSaysTheBondHasGoneIsForgotten() = runTest {
        bonds.listed = listOf(joined, empty)
        leaver.answer = Result.Failure(DataError.Network.Api(ApiProblem(409, ApiErrorCode.BOND_ARCHIVED)))
        exit.leaveAfterJoining("empty", "joined")
        assertTrue(store.rows.isEmpty())
    }

    @Test
    fun aNotFoundSaysTheBondHasGoneAndIsForgotten() = runTest {
        bonds.listed = listOf(joined, empty)
        leaver.answer = Result.Failure(DataError.Network.Api(ApiProblem(404, ApiErrorCode.Unknown)))
        exit.leaveAfterJoining("empty", "joined")
        assertTrue(store.rows.isEmpty())
    }

    @Test
    fun anAnswerThatCouldNotBeReadIsKeptAndCheckedAgainBeforeTheNextTry() = runTest {
        // The leave may have gone through: the next pass reads the bonds first and finds it ended.
        bonds.listed = listOf(joined, empty)
        leaver.answer = Result.Failure(DataError.Network.Unknown)
        exit.leaveAfterJoining("empty", "joined")
        assertEquals(1, store.rows.size)

        bonds.listed = listOf(joined, empty.copy(status = BondStatus.ARCHIVED, activeMembers = 0))
        exit.settle()

        assertEquals(1, leaver.calls.size, "an ended bond is not left twice")
        assertTrue(store.rows.isEmpty())
    }

    @Test
    fun aRetryFromTodayAndTheLeaveFromJoinSendItOnce() = runTest {
        // Today's settle is mid-leave (a row kept for the same bond) when Join's own pass
        // arrives: the mutex makes Join's pass wait, then find the row already done.
        bonds.listed = listOf(joined, empty)
        store.rows += PendingLeave("empty", "joined")
        val gate = CompletableDeferred<Unit>()
        leaver.gate = gate
        val retry = launch { exit.settle() }
        testScheduler.runCurrent()
        val join = launch { exit.leaveAfterJoining("empty", "joined") }
        testScheduler.runCurrent()
        // The leave answered: from now on the server lists the bond as ended.
        bonds.listed = listOf(joined, empty.copy(status = BondStatus.ARCHIVED, activeMembers = 0))
        gate.complete(Unit)
        retry.join()
        join.join()

        assertEquals(listOf("empty" to false), leaver.calls, "one leave, not two")
        assertTrue(store.rows.isEmpty())
    }

    @Test
    fun aRateLimitIsKept() = runTest {
        bonds.listed = listOf(joined, empty)
        leaver.answer = Result.Failure(DataError.Network.Api(ApiProblem(429, ApiErrorCode.RATE_LIMITED)))
        exit.leaveAfterJoining("empty", "joined")
        assertEquals(1, store.rows.size)
    }

    @Test
    fun aDeviceThatCannotKeepItStillTriesOnce() = runTest {
        bonds.listed = listOf(joined, empty)
        store.broken = true
        exit.leaveAfterJoining("empty", "joined")
        assertEquals(listOf("empty" to false), leaver.calls)
    }

    @Test
    fun withNothingKeptSettleAsksNothing() = runTest {
        exit.settle()
        assertEquals(0, bonds.reads, "no round trip on an ordinary Today load")
    }

    @Test
    fun todayPrefersABondWithSomeoneInItOverANewerEmptyOne() {
        // Newest first, as GET /bonds lists them.
        assertEquals("joined", listOf(empty, joined).bondForToday()?.id)
        assertEquals("empty", listOf(empty, joined.copy(status = BondStatus.ARCHIVED)).bondForToday()?.id)
        assertEquals(null, listOf(joined.copy(status = BondStatus.ARCHIVED)).bondForToday())
    }

    @Test
    fun theEmptyBondToEndIsTodaysBondOnlyWhileNobodyHasJoinedIt() {
        assertEquals(empty, listOf(empty).emptyBondToEnd())
        // Today shows the bond someone has joined, so a join from a link there ends nothing.
        assertEquals(null, listOf(empty, joined).emptyBondToEnd())
        assertEquals(null, listOf(joined).emptyBondToEnd())
        assertEquals(null, listOf(empty.copy(activeMembers = null)).emptyBondToEnd())
        assertEquals(null, listOf(empty.copy(status = BondStatus.ARCHIVED)).emptyBondToEnd())
        assertEquals(null, emptyList<Bond>().emptyBondToEnd())
    }

    @Test
    fun aLostAcceptIsFoundOnlyAsTheOneNewBondOfTheInvite() {
        val preview = InvitePreview(inviterName = "Amara", bondName = "Bond joined", bondType = BondType.COUPLE)
        val made = joined.copy(partnerName = "Amara")
        assertEquals(made, listOf(made, empty).joinedByLostAccept(preview, before = setOf("empty"), leavingBondId = "empty"))
        assertEquals(null, listOf(made, empty).joinedByLostAccept(preview, before = setOf("empty", "joined"), leavingBondId = "empty"))
        assertEquals(null, listOf(made.copy(type = BondType.FAMILY)).joinedByLostAccept(preview, emptySet(), "empty"))
        assertEquals(null, listOf(made.copy(partnerName = "Tunde")).joinedByLostAccept(preview, emptySet(), "empty"))
        assertEquals(null, listOf(made.copy(status = BondStatus.ARCHIVED)).joinedByLostAccept(preview, emptySet(), "empty"))
        assertEquals(null, listOf(made.copy(id = "empty")).joinedByLostAccept(preview, emptySet(), "empty"))
    }

    private fun bond(id: String, status: BondStatus, members: Int) =
        Bond(id = id, name = "Bond $id", type = BondType.COUPLE, status = status, invite = null, activeMembers = members)

    private class ListedBonds : BondRepository {
        var listed: List<Bond> = emptyList()
        var answer: Result<List<Bond>, DataError.Network>? = null
        var reads = 0

        override suspend fun bonds(): Result<List<Bond>, DataError.Network> {
            reads++
            return answer ?: Result.Success(listed)
        }

        override suspend fun createBond(name: String, type: BondType, anchorTimezone: String) = error("not used")
        override suspend fun createInvite(bondId: String) = error("not used")
        override suspend fun revokeInvite(bondId: String, inviteId: String) = error("not used")
        override suspend fun resolveInvite(code: String) = error("not used")
        override suspend fun acceptInvite(code: String) = error("not used")
    }

    private class Leaver : BondSettingsRepository {
        val calls = mutableListOf<Pair<String, Boolean>>()
        var answer: EmptyResult<DataError.Network> = Result.Success(Unit)

        /** When set, each leave waits for it: a leave still on its way to the server. */
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun leave(bondId: String, withdrawEntries: Boolean): EmptyResult<DataError.Network> {
            calls += bondId to withdrawEntries
            gate?.await()
            return answer
        }

        override suspend fun settings(bondId: String) = error("not used")
        override suspend fun update(bondId: String, version: String, change: BondSettingsChange) = error("not used")
        override suspend fun block(bondId: String, withdrawEntries: Boolean) = error("not used")
        override suspend fun requestDeletion(bondId: String) = error("not used")
        override suspend fun cancelDeletion(bondId: String) = error("not used")
        override suspend fun proposeTimezone(bondId: String, timezone: String) = error("not used")
        override suspend fun confirmTimezone(bondId: String, proposalId: String) = error("not used")
        override suspend fun cancelTimezoneChange(bondId: String) = error("not used")
        override suspend fun memberSettings(bondId: String) = error("not used")
        override suspend fun replaceMemberSettings(bondId: String, settings: MemberSettings) = error("not used")
    }

    private class MemoryStore : PendingLeaveStore {
        val rows = mutableListOf<PendingLeave>()
        var broken = false

        override suspend fun all(): List<PendingLeave> = if (broken) error("disk") else rows.toList()

        override suspend fun add(leave: PendingLeave) {
            if (broken) error("disk")
            rows.removeAll { it.bondId == leave.bondId }
            rows += leave
        }

        override suspend fun remove(bondId: String) {
            if (broken) error("disk")
            rows.removeAll { it.bondId == bondId }
        }
    }
}
