package com.moyi.app.domain.auth

import com.moyi.app.core.DataError
import com.moyi.app.core.EmptyResult
import com.moyi.app.core.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Registration, verification, sign-in and sign-out (doc 06 §3.1).
 *
 * Failures are [DataError]s. A refusal carries the server's code in
 * [DataError.Network.Api]: a wrong password is `401 INVALID_CREDENTIALS`, an
 * expired verification link `410 VERIFICATION_TOKEN_EXPIRED`, and so on. The
 * caller switches on the code, never on prose.
 */
interface AuthRepository {
    /** Current session; [SessionState.Unknown] until [restoreSession] has run. */
    val session: StateFlow<SessionState>

    /** One-off session events, each delivered once (see [SessionEvent]). */
    val sessionEvents: Flow<SessionEvent>

    /** Creates the account and sends the verification email. Signs nobody in. */
    suspend fun register(
        displayName: String,
        email: String,
        password: String,
        locale: String,
        acceptedTermsVersion: String,
        over18: Boolean,
    ): EmptyResult<DataError.Network>

    suspend fun verifyEmail(token: String): EmptyResult<DataError.Network>

    /** Always succeeds on a reachable server, whether or not the address is known (doc 06 §3.1). */
    suspend fun resendVerification(email: String): EmptyResult<DataError.Network>

    /**
     * Signs in and keeps the session on this device. [DataError.Local] if the
     * session could not be stored, which leaves the person signed out.
     */
    suspend fun login(email: String, password: String): Result<User, DataError>

    /**
     * Signs out on this device. Always ends the local session, even when the
     * server cannot be reached: the person asked to be signed out here.
     */
    suspend fun logout()

    /**
     * Cold start: resumes a stored session if there is one. Returns
     * [SessionState.Unknown] when the secure store cannot be read yet (a
     * locked Keychain after a reboot); that is not signed out. Safe to call
     * again: once decided it returns the state without work, so the app shell
     * calls it again when the app returns to the foreground while Unknown.
     */
    suspend fun restoreSession(): SessionState
}
