package com.moyi.app.feature.auth

import com.moyi.app.core.DataError
import com.moyi.app.core.EmptyResult
import com.moyi.app.core.Result
import com.moyi.app.domain.auth.AuthRepository
import com.moyi.app.domain.auth.SessionEndReason
import com.moyi.app.domain.auth.SessionEvent
import com.moyi.app.domain.auth.SessionState
import com.moyi.app.domain.auth.User
import com.moyi.app.domain.auth.UserStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * An in-memory [AuthRepository]. Each call returns the matching `…Result`,
 * and records what it was sent. Set [gate] to hold a call in flight until the
 * test completes it, which is how the Sending state is observed without sleeps.
 */
class FakeAuthRepository : AuthRepository {
    private val sessionState = MutableStateFlow<SessionState>(SessionState.Unknown)
    private val events = Channel<SessionEvent>(Channel.BUFFERED)
    override val session: StateFlow<SessionState> = sessionState
    override val sessionEvents: Flow<SessionEvent> = events.receiveAsFlow()

    var registerResult: EmptyResult<DataError.Network> = Result.Success(Unit)
    var verifyResult: EmptyResult<DataError.Network> = Result.Success(Unit)
    var resendResult: EmptyResult<DataError.Network> = Result.Success(Unit)
    var loginResult: Result<User, DataError> = Result.Success(USER)

    var gate: CompletableDeferred<Unit>? = null

    /** Hold only verify or only resend, so the two can overlap in a test; each falls back to [gate]. */
    var verifyGate: CompletableDeferred<Unit>? = null
    var resendGate: CompletableDeferred<Unit>? = null

    data class RegisterCall(
        val displayName: String,
        val email: String,
        val password: String,
        val locale: String,
        val acceptedTermsVersion: String,
        val over18: Boolean,
    )

    val registerCalls = mutableListOf<RegisterCall>()
    val verifyCalls = mutableListOf<String>()
    val resendCalls = mutableListOf<String>()
    val loginCalls = mutableListOf<Pair<String, String>>()
    var logoutCalls = 0

    override suspend fun register(
        displayName: String,
        email: String,
        password: String,
        locale: String,
        acceptedTermsVersion: String,
        over18: Boolean,
    ): EmptyResult<DataError.Network> {
        registerCalls += RegisterCall(displayName, email, password, locale, acceptedTermsVersion, over18)
        gate?.await()
        return registerResult
    }

    override suspend fun verifyEmail(token: String): EmptyResult<DataError.Network> {
        verifyCalls += token
        (verifyGate ?: gate)?.await()
        return verifyResult
    }

    override suspend fun resendVerification(email: String): EmptyResult<DataError.Network> {
        resendCalls += email
        (resendGate ?: gate)?.await()
        return resendResult
    }

    override suspend fun login(email: String, password: String): Result<User, DataError> {
        loginCalls += email to password
        gate?.await()
        val result = loginResult
        if (result is Result.Success) sessionState.value = SessionState.SignedIn(result.data)
        return result
    }

    override suspend fun logout() {
        logoutCalls++
        sessionState.value = SessionState.SignedOut
        events.send(SessionEvent.Ended(SessionEndReason.SignedOut))
    }

    override suspend fun restoreSession(): SessionState {
        if (sessionState.value == SessionState.Unknown) sessionState.value = SessionState.SignedOut
        return sessionState.value
    }

    fun signIn(user: User = USER) {
        sessionState.value = SessionState.SignedIn(user)
    }

    companion object {
        val USER = User(
            id = "u1",
            email = "amara@example.com",
            displayName = "Amara",
            emailVerified = true,
            status = UserStatus.ACTIVE,
        )
    }
}
