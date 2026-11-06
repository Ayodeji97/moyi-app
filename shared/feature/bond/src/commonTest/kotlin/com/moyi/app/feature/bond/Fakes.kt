package com.moyi.app.feature.bond

import com.moyi.app.core.ApiErrorCode
import com.moyi.app.core.ApiProblem
import com.moyi.app.core.DataError
import com.moyi.app.core.EmptyResult
import com.moyi.app.core.Result
import com.moyi.app.domain.auth.AuthRepository
import com.moyi.app.domain.auth.SessionEvent
import com.moyi.app.domain.auth.SessionState
import com.moyi.app.domain.auth.User
import com.moyi.app.domain.bond.BondSettings
import com.moyi.app.domain.bond.BondSettingsChange
import com.moyi.app.domain.bond.BondSettingsRepository
import com.moyi.app.domain.bond.BondStatus
import com.moyi.app.domain.bond.BondType
import com.moyi.app.domain.bond.MemberSettings
import com.moyi.app.domain.bond.TimeOfDay
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

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
    var leaves = 0
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

    override suspend fun leave(bondId: String): EmptyResult<DataError.Network> {
        leaves++
        return leaveAnswers.removeFirstOrNull() ?: Result.Success(Unit)
    }

    override suspend fun memberSettings(bondId: String): Result<MemberSettings, DataError.Network> =
        Result.Success(MemberSettings(TimeOfDay(20, 0), "Africa/Lagos"))

    override suspend fun replaceMemberSettings(bondId: String, settings: MemberSettings): Result<MemberSettings, DataError.Network> =
        Result.Success(settings)
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
        acceptedTermsVersion: String,
        over18: Boolean,
    ): EmptyResult<DataError.Network> = Result.Success(Unit)

    override suspend fun verifyEmail(token: String): EmptyResult<DataError.Network> = Result.Success(Unit)

    override suspend fun resendVerification(email: String): EmptyResult<DataError.Network> = Result.Success(Unit)

    override suspend fun login(email: String, password: String): Result<User, DataError> = Result.Failure(DataError.Network.Unknown)

    override suspend fun logout() {
        logouts++
    }

    override suspend fun restoreSession(): SessionState = session.value
}
