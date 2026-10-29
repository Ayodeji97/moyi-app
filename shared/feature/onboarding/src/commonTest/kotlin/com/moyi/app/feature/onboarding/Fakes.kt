package com.moyi.app.feature.onboarding

import com.moyi.app.core.ApiErrorCode
import com.moyi.app.core.ApiProblem
import com.moyi.app.core.DataError
import com.moyi.app.core.EmptyResult
import com.moyi.app.core.FieldError
import com.moyi.app.core.Result
import com.moyi.app.domain.auth.AccountRepository
import com.moyi.app.domain.auth.AuthRepository
import com.moyi.app.domain.auth.SessionEvent
import com.moyi.app.domain.auth.SessionState
import com.moyi.app.domain.auth.User
import com.moyi.app.domain.auth.UserStatus
import com.moyi.app.domain.bond.Bond
import com.moyi.app.domain.bond.BondRepository
import com.moyi.app.domain.bond.BondStatus
import com.moyi.app.domain.bond.BondType
import com.moyi.app.domain.bond.Invite
import com.moyi.app.domain.bond.InvitePreview
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

val INVITE = Invite(id = "inv-1", code = "K7Q2PD", link = "https://getmoyi.app/i/K7Q2PD")
val PREVIEW = InvitePreview(inviterName = "Amara", bondName = "Amara and Tunde", bondType = BondType.COUPLE)

fun bond(status: BondStatus = BondStatus.PENDING_MEMBER, invite: Invite? = INVITE) =
    Bond(id = "bond-1", name = "Amara and Tunde", type = BondType.COUPLE, status = status, invite = invite)

fun user(verified: Boolean = true) =
    User(id = "u-1", email = "tunde@example.com", displayName = "Tunde", emailVerified = verified, status = UserStatus.ACTIVE)

fun problem(status: Int, code: ApiErrorCode, vararg fields: String): DataError.Network.Api = DataError.Network.Api(
    ApiProblem(status = status, code = code, fieldErrors = fields.map { FieldError(it, "INVALID", "") }),
)

/**
 * An in-memory [BondRepository]: each call returns its `…Result` and records
 * what it was sent. Set [gate] to hold a call in flight, which is how the
 * Sending state is observed without sleeps.
 */
class FakeBondRepository : BondRepository {
    var bondsResult: Result<List<Bond>, DataError.Network> = Result.Success(emptyList())
    var createResult: Result<Bond, DataError.Network> = Result.Success(bond())
    var inviteResult: Result<Invite, DataError.Network> = Result.Success(INVITE)
    var resolveResult: Result<InvitePreview, DataError.Network> = Result.Success(PREVIEW)
    var acceptResult: Result<Bond, DataError.Network> = Result.Success(bond(BondStatus.ACTIVE, invite = null))
    var gate: CompletableDeferred<Unit>? = null

    data class CreateCall(val name: String, val type: BondType, val zone: String)

    val createCalls = mutableListOf<CreateCall>()
    val inviteCalls = mutableListOf<String>()
    val resolveCalls = mutableListOf<String>()
    val acceptCalls = mutableListOf<String>()
    var bondsCalls = 0

    override suspend fun bonds(): Result<List<Bond>, DataError.Network> {
        bondsCalls++
        gate?.await()
        return bondsResult
    }

    override suspend fun createBond(name: String, type: BondType, anchorTimezone: String): Result<Bond, DataError.Network> {
        createCalls += CreateCall(name, type, anchorTimezone)
        gate?.await()
        return createResult
    }

    override suspend fun createInvite(bondId: String): Result<Invite, DataError.Network> {
        inviteCalls += bondId
        return inviteResult
    }

    override suspend fun revokeInvite(bondId: String, inviteId: String): EmptyResult<DataError.Network> = Result.Success(Unit)

    override suspend fun resolveInvite(code: String): Result<InvitePreview, DataError.Network> {
        resolveCalls += code
        gate?.await()
        return resolveResult
    }

    override suspend fun acceptInvite(code: String): Result<Bond, DataError.Network> {
        acceptCalls += code
        gate?.await()
        return acceptResult
    }
}

class FakeAccountRepository : AccountRepository {
    var result: Result<User, DataError.Network> = Result.Success(user())
    var calls = 0

    override suspend fun currentUser(): Result<User, DataError.Network> {
        calls++
        return result
    }
}

/** Only what the gate uses: the session's email and resend. */
class FakeAuthRepository(signedIn: User? = user(verified = false)) : AuthRepository {
    override val session: StateFlow<SessionState> =
        MutableStateFlow(if (signedIn == null) SessionState.SignedOut else SessionState.SignedIn(signedIn))
    override val sessionEvents: Flow<SessionEvent> = emptyFlow()
    var resendResult: EmptyResult<DataError.Network> = Result.Success(Unit)
    val resendCalls = mutableListOf<String>()

    override suspend fun resendVerification(email: String): EmptyResult<DataError.Network> {
        resendCalls += email
        return resendResult
    }

    override suspend fun register(
        displayName: String,
        email: String,
        password: String,
        locale: String,
        acceptedTermsVersion: String,
        over18: Boolean,
    ): EmptyResult<DataError.Network> = error("not used")

    override suspend fun verifyEmail(token: String): EmptyResult<DataError.Network> = error("not used")
    override suspend fun login(email: String, password: String): Result<User, DataError> = error("not used")
    override suspend fun logout() = error("not used")
    override suspend fun restoreSession(): SessionState = session.value
}
