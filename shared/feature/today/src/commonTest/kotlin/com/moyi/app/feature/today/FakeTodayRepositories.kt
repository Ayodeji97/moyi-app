package com.moyi.app.feature.today

import com.moyi.app.domain.today.Reaction
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
import com.moyi.app.domain.today.CachedDay
import com.moyi.app.domain.today.EntryOutbox
import com.moyi.app.domain.today.EntryStatus
import com.moyi.app.domain.today.MyEntry
import com.moyi.app.domain.today.OutboxPass
import com.moyi.app.domain.today.OutboxRefusal
import com.moyi.app.domain.today.OutboxSender
import com.moyi.app.domain.today.PartnerEntry
import com.moyi.app.domain.today.QueuedEntry
import com.moyi.app.domain.today.RefusedEntry
import com.moyi.app.domain.today.Today
import com.moyi.app.domain.today.TodayCache
import com.moyi.app.domain.today.TodayRepository
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

internal fun api(status: Int, code: ApiErrorCode) = DataError.Network.Api(ApiProblem(status = status, code = code))

internal val ACTIVE = Bond(
    id = "bond-1",
    name = "Amara and Tunde",
    type = BondType.COUPLE,
    status = BondStatus.ACTIVE,
    invite = null,
    partnerName = PARTNER,
    anchorTimezone = "Africa/Lagos",
)

internal val MY_ENTRY = MyEntry(id = "entry-1", text = MINE, status = EntryStatus.SUBMITTED)

internal fun today(status: BondDayStatus = BondDayStatus.OPEN, my: MyEntry? = null, theirs: PartnerEntry? = null) =
    Today(date = "2026-09-14", status = status, myEntry = my, partnerEntry = theirs, prompt = PROMPT)

internal class FakeBonds : BondRepository {
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

internal class FakeTodayEntries : TodayRepository {
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

    data class Submitted(val bondId: String, val key: String, val intendedAt: Instant?, val imageMediaId: String? = null, val voiceMediaId: String? = null)
    val submitted = mutableListOf<Submitted>()

    override suspend fun submitEntry(bondId: String, text: String, promptId: String?, idempotencyKey: String, intendedAt: Instant?, imageMediaId: String?, voiceMediaId: String?): Result<MyEntry, DataError.Network> {
        submitted += Submitted(bondId, idempotencyKey, intendedAt, imageMediaId, voiceMediaId)
        return submitResult
    }
    data class Edited(val entryId: String, val key: String, val imageMediaId: String?, val voiceMediaId: String?)
    val edited = mutableListOf<Edited>()

    override suspend fun editEntry(entryId: String, text: String, idempotencyKey: String, imageMediaId: String?, voiceMediaId: String?): Result<MyEntry, DataError.Network> {
        edited += Edited(entryId, idempotencyKey, imageMediaId, voiceMediaId)
        return editResult
    }

    override suspend fun deleteEntry(entryId: String): EmptyResult<DataError.Network> {
        deleted += entryId
        deleteGate?.await()
        return deleteResult
    }

    var reactResult: EmptyResult<DataError.Network> = Result.Success(Unit)
    var reactGate: CompletableDeferred<Unit>? = null
    val reactions = mutableListOf<Pair<String, Reaction?>>()

    override suspend fun setReaction(entryId: String, reaction: Reaction?): EmptyResult<DataError.Network> {
        reactions += entryId to reaction
        reactGate?.await()
        return reactResult
    }
}

/** A [TodayCache] in memory: [stored] is what a real one would hold, [writes] every write in order. */
internal class FakeTodayCache(var stored: CachedDay? = null) : TodayCache {
    val writes = mutableListOf<CachedDay>()
    var clears = 0

    override suspend fun read(): CachedDay? = stored

    override suspend fun write(day: CachedDay) {
        writes += day
        stored = day
    }

    override suspend fun clear() {
        clears++
        stored = null
    }
}

/**
 * The outbox and its sender in memory: [queue] is what the table would hold, [refusals]
 * the refusal markers. The sender only counts: the real one is tested in the data layer.
 */
internal class FakeOutbox : EntryOutbox, OutboxSender {
    val queue = mutableListOf<QueuedEntry>()
    val refusals = mutableMapOf<String, RefusedEntry>()
    var retries = 0
    var failEnqueue = false
    val changeFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 4)

    override suspend fun enqueue(entry: QueuedEntry) {
        if (failEnqueue) error("disk full")
        queue += entry
    }
    override suspend fun queued(): List<QueuedEntry> = queue.toList()
    override suspend fun queuedFor(bondId: String): QueuedEntry? = queue.firstOrNull { it.bondId == bondId }
    override suspend fun remove(idempotencyKey: String) {
        queue.removeAll { it.idempotencyKey == idempotencyKey }
    }
    override suspend fun refuse(entry: QueuedEntry, reason: OutboxRefusal) {
        queue.remove(entry)
        refusals[entry.bondId] = RefusedEntry(entry.bondId, reason, entry.dayDate)
    }
    override suspend fun refusalFor(bondId: String): RefusedEntry? = refusals[bondId]
    override suspend fun clearRefusal(bondId: String) {
        refusals.remove(bondId)
    }

    override val changes: Flow<Unit> = changeFlow
    override fun start() = Unit
    override fun retrySoon() {
        retries++
    }
    override suspend fun sendQueued(): OutboxPass = if (queue.isEmpty()) OutboxPass.Empty else OutboxPass.Waiting
}

/** A clock stopped at [instant]. */
internal class FixedClock(var instant: Instant) : Clock {
    override fun now(): Instant = instant
}

/** 2026-09-14, 12:00 in Lagos: the Bond-day of every [today] here. */
internal val NOON_ON_THE_DAY: Instant = Instant.parse("2026-09-14T11:00:00Z")
