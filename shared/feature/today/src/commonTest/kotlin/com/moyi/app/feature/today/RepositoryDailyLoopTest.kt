package com.moyi.app.feature.today

import com.moyi.app.core.ApiErrorCode
import com.moyi.app.core.ApiProblem
import com.moyi.app.core.DataError
import com.moyi.app.core.EmptyResult
import com.moyi.app.core.Result
import com.moyi.app.domain.bond.Bond
import com.moyi.app.domain.bond.BondRepository
import com.moyi.app.domain.bond.BondStatus
import com.moyi.app.domain.bond.BondType
import com.moyi.app.domain.bond.Invite
import com.moyi.app.domain.bond.InvitePreview
import com.moyi.app.domain.today.BondDayStatus
import com.moyi.app.domain.today.EntryStatus
import com.moyi.app.domain.today.MyEntry
import com.moyi.app.domain.today.PartnerEntry
import com.moyi.app.domain.today.Today
import com.moyi.app.domain.today.TodayRepository
import com.moyi.app.feature.today.common.DailyLoopState
import com.moyi.app.feature.today.common.DeleteResult
import com.moyi.app.feature.today.common.RepositoryDailyLoop
import com.moyi.app.feature.today.common.TodayStatus
import com.moyi.app.feature.today.common.WriteResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

private fun api(status: Int, code: ApiErrorCode) = DataError.Network.Api(ApiProblem(status = status, code = code))

private val ACTIVE = Bond(
    id = "bond-1",
    name = "Amara and Tunde",
    type = BondType.COUPLE,
    status = BondStatus.ACTIVE,
    invite = null,
    partnerName = PARTNER,
)

private val MY_ENTRY = MyEntry(id = "entry-1", text = MINE, status = EntryStatus.SUBMITTED)

private fun today(status: BondDayStatus = BondDayStatus.OPEN, my: MyEntry? = null, theirs: PartnerEntry? = null) =
    Today(date = "2026-09-14", status = status, myEntry = my, partnerEntry = theirs, prompt = PROMPT)

private class Bonds : BondRepository {
    var result: Result<List<Bond>, DataError.Network> = Result.Success(listOf(ACTIVE))
    var calls = 0
    override suspend fun bonds(): Result<List<Bond>, DataError.Network> {
        calls++
        return result
    }
    override suspend fun createBond(name: String, type: BondType, anchorTimezone: String) = error("not used")
    override suspend fun createInvite(bondId: String): Result<Invite, DataError.Network> = error("not used")
    override suspend fun revokeInvite(bondId: String, inviteId: String): EmptyResult<DataError.Network> = error("not used")
    override suspend fun resolveInvite(code: String): Result<InvitePreview, DataError.Network> = error("not used")
    override suspend fun acceptInvite(code: String): Result<Bond, DataError.Network> = error("not used")
}

private class Entries : TodayRepository {
    var todayResult: Result<Today, DataError.Network> = Result.Success(today())
    var submitResult: Result<MyEntry, DataError.Network> = Result.Success(MY_ENTRY)
    var editResult: Result<MyEntry, DataError.Network> = Result.Success(MY_ENTRY)
    var deleteResult: EmptyResult<DataError.Network> = Result.Success(Unit)

    /** Holds the next `today` call until completed, then clears itself. */
    var todayGate: CompletableDeferred<Unit>? = null
    var deleteGate: CompletableDeferred<Unit>? = null
    val todayCalls = mutableListOf<String>()
    val deleted = mutableListOf<String>()

    override suspend fun today(bondId: String): Result<Today, DataError.Network> {
        todayCalls += bondId
        todayGate?.let { todayGate = null; it.await() }
        return todayResult
    }

    override suspend fun submitEntry(bondId: String, text: String, promptId: String?, idempotencyKey: String) = submitResult
    override suspend fun editEntry(entryId: String, text: String, idempotencyKey: String) = editResult

    override suspend fun deleteEntry(entryId: String): EmptyResult<DataError.Network> {
        deleted += entryId
        deleteGate?.await()
        return deleteResult
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class RepositoryDailyLoopTest {
    private val bonds = Bonds()
    private val entries = Entries()
    private val loop = RepositoryDailyLoop(bonds, entries)

    private val ready get() = assertIs<DailyLoopState.Ready>(loop.state.value).day

    @Test
    fun startsOnTheBareSurfaceThenLoadsTheFirstOpenBondsDay() = runTest {
        assertEquals(DailyLoopState.Loading, loop.state.value)
        bonds.result = Result.Success(listOf(ACTIVE.copy(id = "old", status = BondStatus.ARCHIVED), ACTIVE))
        loop.refresh()
        assertEquals("bond-1", ready.bondId)
        assertEquals(listOf("bond-1"), entries.todayCalls)
        assertEquals(TodayStatus.NotWritten, ready.status)
        assertEquals(PROMPT, ready.prompt)
    }

    @Test
    fun noOpenBondIsSaidAsSuch() = runTest {
        bonds.result = Result.Success(listOf(ACTIVE.copy(status = BondStatus.ARCHIVED)))
        loop.refresh()
        assertEquals(DailyLoopState.NoOpenBond, loop.state.value)
        assertEquals(emptyList(), entries.todayCalls)
    }

    @Test
    fun aFailedFirstLoadIsTheErrorAndAFailedRefreshKeepsTheDay() = runTest {
        bonds.result = Result.Failure(DataError.Network.NoInternet)
        loop.refresh()
        assertEquals(DailyLoopState.Failed, loop.state.value)

        bonds.result = Result.Success(listOf(ACTIVE))
        loop.refresh()
        assertEquals(TodayStatus.NotWritten, ready.status)

        entries.todayResult = Result.Failure(DataError.Network.Timeout)
        loop.refresh()
        assertEquals(TodayStatus.NotWritten, ready.status, "a refresh that failed must not blank the day")
    }

    @Test
    fun aDayThatCannotBeShownHonestlyIsAFailedLoad() = runTest {
        entries.todayResult = Result.Success(today().copy(date = "not a date"))
        loop.refresh()
        assertEquals(DailyLoopState.Failed, loop.state.value)
    }

    @Test
    fun aSendShowsWaitingAtOnceEvenIfTheRefreshAfterItFails() = runTest {
        loop.refresh()
        entries.todayResult = Result.Failure(DataError.Network.NoInternet)
        assertEquals(WriteResult.Saved, loop.submit(MINE, PROMPT.id, "key-1"))
        assertEquals(TodayStatus.Waiting, ready.status)
        assertEquals(MINE, ready.myEntry)
    }

    @Test
    fun aRefusedSendFailsAndAConflictCatchesTodayUp() = runTest {
        loop.refresh()
        val before = entries.todayCalls.size
        entries.submitResult = Result.Failure(DataError.Network.NoInternet)
        assertEquals(WriteResult.Failed, loop.submit(MINE, null, "key-1"))
        assertEquals(before, entries.todayCalls.size, "offline: nothing to catch up on")

        // Written from the other phone: Today shows what the server holds.
        entries.submitResult = Result.Failure(api(409, ApiErrorCode.ENTRY_ALREADY_EXISTS))
        entries.todayResult = Result.Success(today(BondDayStatus.PARTIAL, my = MY_ENTRY))
        assertEquals(WriteResult.Failed, loop.submit(MINE, null, "key-2"))
        assertEquals(TodayStatus.Waiting, ready.status)
    }

    @Test
    fun anEditBeforeAnythingIsWrittenSendsNothing() = runTest {
        loop.refresh()
        assertEquals(WriteResult.Failed, loop.edit("words", "key-1"))
    }

    @Test
    fun deletingBeforeTheRevealIsOptimisticAndAFailureRestoresTheEntry() = runTest {
        entries.todayResult = Result.Success(today(BondDayStatus.PARTIAL, my = MY_ENTRY))
        loop.refresh()
        val gate = CompletableDeferred<Unit>()
        entries.deleteGate = gate
        entries.deleteResult = Result.Failure(DataError.Network.NoInternet)

        val delete = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            assertEquals(DeleteResult.Failed, loop.deleteBeforeReveal())
        }
        assertEquals(TodayStatus.NotWritten, ready.status, "not written at once, before the server answers")

        gate.complete(Unit)
        delete.join()
        assertEquals(TodayStatus.Waiting, ready.status)
        assertEquals(MINE, ready.myEntry)
        assertEquals(listOf("entry-1"), entries.deleted)
    }

    @Test
    fun deletingAfterTheRevealShowsTheTombstoneOnlyOnceTheServerAgrees() = runTest {
        val theirs = PartnerEntry.Revealed("entry-2", THEIRS)
        entries.todayResult = Result.Success(today(BondDayStatus.REVEALED, my = MY_ENTRY, theirs = theirs))
        loop.refresh()
        val gate = CompletableDeferred<Unit>()
        entries.deleteGate = gate
        // The refresh after the delete fails, so what is shown is the loop's own tombstone.
        entries.todayResult = Result.Failure(DataError.Network.NoInternet)

        val delete = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            assertEquals(DeleteResult.Deleted, loop.deleteAfterReveal())
        }
        assertEquals(TodayStatus.Revealed, ready.status, "irreversible: nothing changes before the 204")

        gate.complete(Unit)
        delete.join()
        assertEquals(TodayStatus.Tombstone, ready.status)
        assertEquals(null, ready.myEntry)
        assertEquals(THEIRS, ready.partnerEntry, "deleting mine never touches theirs")
    }

    @Test
    fun aDeleteAlreadyGoneIsNotAFailure() = runTest {
        entries.todayResult = Result.Success(today(BondDayStatus.PARTIAL, my = MY_ENTRY))
        loop.refresh()
        entries.deleteResult = Result.Failure(api(404, ApiErrorCode.Unknown))
        entries.todayResult = Result.Success(today(BondDayStatus.OPEN))
        assertEquals(DeleteResult.Gone, loop.deleteBeforeReveal())
        assertEquals(TodayStatus.NotWritten, ready.status)
    }

    @Test
    fun aLoadStillInFlightAtSignOutNeverPublishesIntoTheNextSession() = runTest {
        loop.refresh()
        val gate = CompletableDeferred<Unit>()
        entries.todayGate = gate
        val inFlight = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { loop.refresh() }
        loop.clear()
        entries.todayResult = Result.Success(today(BondDayStatus.REVEALED, my = MY_ENTRY))
        gate.complete(Unit)
        inFlight.join()
        assertEquals(DailyLoopState.Loading, loop.state.value)
    }

    @Test
    fun aRefreshAfterTheFirstAsksForTheBondsAndTheDayTogether() = runTest {
        loop.refresh()
        loop.refresh()
        assertEquals(2, bonds.calls)
        assertEquals(listOf("bond-1", "bond-1"), entries.todayCalls)
    }
}
