package com.moyi.app.feature.bond

import com.moyi.app.domain.today.RefusedEntry
import com.moyi.app.domain.today.QueuedEntry
import com.moyi.app.domain.today.OutboxSender
import com.moyi.app.domain.today.OutboxRefusal
import com.moyi.app.domain.today.OutboxPass
import com.moyi.app.domain.today.EntryOutbox
import com.moyi.app.core.ApiErrorCode
import com.moyi.app.core.ApiProblem
import com.moyi.app.core.DataError
import com.moyi.app.core.EmptyResult
import com.moyi.app.core.Result
import com.moyi.app.domain.auth.AuthRepository
import com.moyi.app.domain.auth.EmailProof
import com.moyi.app.domain.auth.SessionEvent
import com.moyi.app.domain.auth.SessionState
import com.moyi.app.domain.auth.User
import com.moyi.app.domain.bond.Bond
import com.moyi.app.domain.bond.BondRepository
import com.moyi.app.domain.bond.BondSettings
import com.moyi.app.domain.bond.BondSettingsChange
import com.moyi.app.domain.bond.BondSettingsRepository
import com.moyi.app.domain.bond.BondStatus
import com.moyi.app.domain.bond.BondType
import com.moyi.app.domain.bond.Invite
import com.moyi.app.domain.bond.InvitePreview
import com.moyi.app.domain.bond.MemberSettings
import com.moyi.app.domain.bond.PendingDeletionRequest
import com.moyi.app.domain.bond.PendingTimezoneChange
import com.moyi.app.domain.bond.TimeOfDay
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.time.Instant

val settingsV1 = BondSettings(
    bondId = "b1",
    name = "Amara and Tunde",
    type = BondType.COUPLE,
    status = BondStatus.ACTIVE,
    anchorTimezone = "Africa/Lagos",
    revealTime = null,
    strictMode = false,
    version = "\"1\"",
)

fun apiError(status: Int, code: ApiErrorCode) = Result.Failure(DataError.Network.Api(ApiProblem(status, code)))

/**
 * A scripted [BondSettingsRepository]. Each call answers from its queue, or by
 * default as a server holding [server] would, and is recorded. [gate], when set,
 * holds an update until it is completed, so a test can look at Saving.
 */
class FakeBondSettingsRepository(var server: BondSettings = settingsV1) : BondSettingsRepository {
    val reads = ArrayDeque<Result<BondSettings, DataError.Network>>()
    val updateAnswers = ArrayDeque<Result<BondSettings, DataError.Network>>()
    val leaveAnswers = ArrayDeque<EmptyResult<DataError.Network>>()
    val updates = mutableListOf<Triple<String, String, BondSettingsChange>>()
    var readCount = 0
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun settings(bondId: String): Result<BondSettings, DataError.Network> {
        readCount++
        return reads.removeFirstOrNull() ?: Result.Success(server)
    }

    override suspend fun update(bondId: String, version: String, change: BondSettingsChange): Result<BondSettings, DataError.Network> {
        updates += Triple(bondId, version, change)
        gate?.await()
        updateAnswers.removeFirstOrNull()?.let { return it }
        val next = (server.version.trim('"').toInt() + 1)
        server = server.copy(
            name = change.name ?: server.name,
            type = change.type ?: server.type,
            revealTime = change.revealTime ?: server.revealTime,
            strictMode = change.strictMode ?: server.strictMode,
            version = "\"$next\"",
        )
        return Result.Success(server)
    }

    /** Each leave's withdraw choice, in order. */
    val leaveWithdraws = mutableListOf<Boolean>()
    val leaves: Int get() = leaveWithdraws.size

    override suspend fun leave(bondId: String, withdrawEntries: Boolean): EmptyResult<DataError.Network> {
        leaveWithdraws += withdrawEntries
        return leaveAnswers.removeFirstOrNull() ?: Result.Success(Unit)
    }

    val blockAnswers = ArrayDeque<EmptyResult<DataError.Network>>()
    /** Each block's withdraw choice, in order. */
    val blockWithdraws = mutableListOf<Boolean>()

    override suspend fun block(bondId: String, withdrawEntries: Boolean): EmptyResult<DataError.Network> {
        blockWithdraws += withdrawEntries
        gate?.await()
        return blockAnswers.removeFirstOrNull() ?: Result.Success(Unit)
    }

    val deletionAnswers = ArrayDeque<Result<BondSettings, DataError.Network>>()
    val deletionCancelAnswers = ArrayDeque<EmptyResult<DataError.Network>>()
    var deletionRequests = 0
    var deletionCancels = 0

    /** As the server: the first ask waits for the other member; the other member's ask starts the cooling-off. */
    override suspend fun requestDeletion(bondId: String): Result<BondSettings, DataError.Network> {
        deletionRequests++
        gate?.await()
        deletionAnswers.removeFirstOrNull()?.let { return it }
        server = if (server.pendingDeletion?.requestedByMe == false) {
            server.copy(status = BondStatus.PENDING_DELETION, pendingDeletion = null, deletionScheduledFor = coolingOffEnds, version = bump())
        } else {
            server.copy(pendingDeletion = PendingDeletionRequest(requestedByMe = true, expiresAt = askLapses), version = bump())
        }
        return Result.Success(server)
    }

    override suspend fun cancelDeletion(bondId: String): EmptyResult<DataError.Network> {
        deletionCancels++
        deletionCancelAnswers.removeFirstOrNull()?.let { return it }
        server = server.copy(status = BondStatus.ACTIVE, pendingDeletion = null, deletionScheduledFor = null, version = bump())
        return Result.Success(Unit)
    }

    val proposeAnswers = ArrayDeque<Result<BondSettings, DataError.Network>>()
    val confirmAnswers = ArrayDeque<Result<BondSettings, DataError.Network>>()
    val cancelAnswers = ArrayDeque<EmptyResult<DataError.Network>>()
    val proposals = mutableListOf<String>()
    val confirmations = mutableListOf<String>()
    var cancels = 0

    /** Proposes as a server would: waits for the other member, unless nobody has joined (then it applies). */
    override suspend fun proposeTimezone(bondId: String, timezone: String): Result<BondSettings, DataError.Network> {
        proposals += timezone
        gate?.await()
        proposeAnswers.removeFirstOrNull()?.let { return it }
        server = if (server.waitingForPartner) {
            server.copy(anchorTimezone = timezone, version = bump())
        } else {
            server.copy(pendingTimezone = PendingTimezoneChange("p-new", timezone, proposedByMe = true), version = bump())
        }
        return Result.Success(server)
    }

    override suspend fun confirmTimezone(bondId: String, proposalId: String): Result<BondSettings, DataError.Network> {
        confirmations += proposalId
        gate?.await()
        confirmAnswers.removeFirstOrNull()?.let { return it }
        val pending = server.pendingTimezone ?: return apiError(404, ApiErrorCode.NOT_FOUND)
        server = server.copy(anchorTimezone = pending.timezone, pendingTimezone = null, version = bump())
        return Result.Success(server)
    }

    override suspend fun cancelTimezoneChange(bondId: String): EmptyResult<DataError.Network> {
        cancels++
        cancelAnswers.removeFirstOrNull()?.let { return it }
        server = server.copy(pendingTimezone = null)
        return Result.Success(Unit)
    }

    private fun bump(): String = "\"${server.version.trim('"').toInt() + 1}\""

    /** The member's own settings as the server holds them; [memberReads] and [memberWrites] answer first when queued. */
    var member = MemberSettings(TimeOfDay(20, 0), "Africa/Lagos", nicknameForOther = "Tee")
    val memberReads = ArrayDeque<Result<MemberSettings, DataError.Network>>()
    val memberWrites = ArrayDeque<Result<MemberSettings, DataError.Network>>()
    val memberReplaced = mutableListOf<MemberSettings>()

    override suspend fun memberSettings(bondId: String): Result<MemberSettings, DataError.Network> =
        memberReads.removeFirstOrNull() ?: Result.Success(member)

    override suspend fun replaceMemberSettings(bondId: String, settings: MemberSettings): Result<MemberSettings, DataError.Network> {
        memberReplaced += settings
        gate?.await()
        memberWrites.removeFirstOrNull()?.let { return it }
        member = settings
        return Result.Success(settings)
    }
}

val askLapses: Instant = Instant.parse("2026-11-13T10:00:00Z")
val coolingOffEnds: Instant = Instant.parse("2026-12-06T10:00:00Z")

val liveInvite = Invite("i1", "K7Q2PD", "https://getmoyi.app/i/K7Q2PD")
val newInvite = Invite("i2", "M4XR9T", "https://getmoyi.app/i/M4XR9T")

/** The invite calls of [BondRepository], over the settings fake's [settings] server. */
class FakeBondRepository(private val settings: FakeBondSettingsRepository) : BondRepository {
    val createAnswers = ArrayDeque<Result<Invite, DataError.Network>>()
    val revokeAnswers = ArrayDeque<EmptyResult<DataError.Network>>()
    val revoked = mutableListOf<String>()
    var created = 0

    override suspend fun createInvite(bondId: String): Result<Invite, DataError.Network> {
        created++
        createAnswers.removeFirstOrNull()?.let { return it }
        settings.server = settings.server.copy(invite = newInvite)
        return Result.Success(newInvite)
    }

    override suspend fun revokeInvite(bondId: String, inviteId: String): EmptyResult<DataError.Network> {
        revoked += inviteId
        revokeAnswers.removeFirstOrNull()?.let { return it }
        settings.server = settings.server.copy(invite = null)
        return Result.Success(Unit)
    }

    override suspend fun bonds(): Result<List<Bond>, DataError.Network> = error("not used")
    override suspend fun createBond(name: String, type: BondType, anchorTimezone: String): Result<Bond, DataError.Network> = error("not used")
    override suspend fun resolveInvite(code: String): Result<InvitePreview, DataError.Network> = error("not used")
    override suspend fun acceptInvite(code: String): Result<Bond, DataError.Network> = error("not used")
}

class FakeAuthRepository : AuthRepository {
    var logouts = 0
    override val session: StateFlow<SessionState> = MutableStateFlow(SessionState.Unknown)
    override val sessionEvents: Flow<SessionEvent> = emptyFlow()

    override suspend fun register(
        displayName: String,
        email: String,
        password: String,
        locale: String,
        acceptedTermsVersion: String?,
        over18: Boolean,
    ): EmptyResult<DataError.Network> = Result.Success(Unit)

    override suspend fun verifyEmail(proof: EmailProof): EmptyResult<DataError.Network> = Result.Success(Unit)

    override suspend fun resendVerification(email: String): EmptyResult<DataError.Network> = Result.Success(Unit)

    override suspend fun login(email: String, password: String): Result<User, DataError> = Result.Failure(DataError.Network.Unknown)

    override suspend fun requestPasswordReset(email: String): EmptyResult<DataError.Network> = error("not used")

    override suspend fun resetPassword(proof: EmailProof, password: String): EmptyResult<DataError.Network> = error("not used")

    override suspend fun logout() {
        logouts++
    }

    override suspend fun restoreSession(): SessionState = session.value
}

/**
 * The outbox for sign-out: [queue] waits until [sendQueued] runs, which sends all of it
 * when [online] and nothing otherwise.
 */
class FakeOutbox : EntryOutbox, OutboxSender {
    val queue = mutableListOf<QueuedEntry>()
    var online = false
    var passes = 0

    override suspend fun enqueue(entry: QueuedEntry) {
        queue += entry
    }
    override suspend fun queued(): List<QueuedEntry> = queue.toList()
    override suspend fun queuedFor(bondId: String): QueuedEntry? = queue.firstOrNull { it.bondId == bondId }
    override suspend fun remove(idempotencyKey: String) {
        queue.removeAll { it.idempotencyKey == idempotencyKey }
    }
    override suspend fun refuse(entry: QueuedEntry, reason: OutboxRefusal) {
        queue.remove(entry)
    }
    override suspend fun refusalFor(bondId: String): RefusedEntry? = null
    override suspend fun clearRefusal(bondId: String) = Unit

    override val changes: Flow<Unit> = emptyFlow()
    override fun start() = Unit
    override fun retrySoon() = Unit
    override suspend fun sendQueued(): OutboxPass {
        passes++
        if (online) queue.clear()
        return if (queue.isEmpty()) OutboxPass.Empty else OutboxPass.Waiting
    }
}
