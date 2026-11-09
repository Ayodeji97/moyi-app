package com.moyi.app.domain.auth

import com.moyi.app.core.DataError
import com.moyi.app.core.EmptyResult
import com.moyi.app.core.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Registration, verification, password reset, sign-in and sign-out (doc 06 §3.1).
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

    /**
     * Creates the account and sends the verification email. Signs nobody in.
     *
     * [acceptedTermsVersion] is the Terms version the person was shown (`GET /meta/legal`),
     * or null when it could not be read. The server records its own current versions
     * whatever is sent (ADR-0056), so this is informational only.
     */
    suspend fun register(
        displayName: String,
        email: String,
        password: String,
        locale: String,
        acceptedTermsVersion: String?,
        over18: Boolean,
    ): EmptyResult<DataError.Network>

    /**
     * Confirms the address with the emailed link's token or the typed code ([EmailProof]).
     * A link that expired or was used is `410 VERIFICATION_TOKEN_EXPIRED`; a link that
     * matches nothing, and every refused code (wrong, expired, five wrong tries, or spent),
     * is one answer, `422 VERIFICATION_TOKEN_INVALID`, on purpose.
     */
    suspend fun verifyEmail(proof: EmailProof): EmptyResult<DataError.Network>

    /** Always succeeds on a reachable server, whether or not the address is known (doc 06 §3.1). */
    suspend fun resendVerification(email: String): EmptyResult<DataError.Network>

    /**
     * Asks for a password-reset link. Like [resendVerification], it succeeds
     * on a reachable server whether or not the address has an account (`202`
     * always, doc 06 §3.1), so a success says nothing about the address.
     */
    suspend fun requestPasswordReset(email: String): EmptyResult<DataError.Network>

    /**
     * Sets a new password from the reset email's link or typed code ([EmailProof]).
     * Signs nobody in: every session on every device is revoked, and the caller
     * signs in afterwards. Refusals: `422 PASSWORD_RESET_TOKEN_INVALID` (a link
     * that matches nothing, or any refused code), `410 PASSWORD_RESET_TOKEN_EXPIRED`
     * (a link expired or already used), or a `422` field violation on `password`,
     * which spends nothing.
     */
    suspend fun resetPassword(proof: EmailProof, password: String): EmptyResult<DataError.Network>

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
