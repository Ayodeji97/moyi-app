package com.moyi.app.domain.auth

import com.moyi.app.core.DataError
import com.moyi.app.core.EmptyResult
import com.moyi.app.core.Error
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
     * Says whether a typed reset code is right, before the new password is chosen
     * (`POST /auth/reset-password/check`, backend-contract-gaps.md row 39). A check, not a
     * grant: success (`204`) changes nothing, and the reset still sends the same code. Every
     * refused code (wrong, expired, burned after five wrong tries, spent, or no such account)
     * is one answer, `422 PASSWORD_RESET_TOKEN_INVALID`, and a wrong try spends one of the
     * code's five. Also `429` (ten typed codes a day per address) and `503 CODE_CHECK_UNAVAILABLE`.
     */
    suspend fun checkResetCode(email: String, code: String): EmptyResult<DataError.Network>

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
     * The same reset as [resetPassword], for the person signed in on this device (Account ›
     * Change password), who stays signed in here. The reset revokes every session and refresh
     * family of the account, this device's included, so the new password signs this device in
     * again at once ([login]'s request), and the new session replaces the old one with the
     * new refresh token stored before the new access token is used.
     *
     * The reset and the sign-in after it run under the session lock: no refresh can start in
     * between, so a request refused meanwhile waits and is retried with the new access token,
     * instead of presenting the revoked refresh token and ending the session. The member is
     * the same, so what this device keeps for them (drafts, the outbox, the cache) stays.
     *
     * - [PasswordChangeError.NotChanged]: the reset was refused or did not arrive; the
     *   password and this device's session are as they were.
     * - [PasswordChangeError.SignedOut]: the new password is saved, but this device could not
     *   sign in with it. The session here is ended ([SessionEndReason.PasswordChanged]) and
     *   [sessionEvents] says so: sign in again with the new password.
     * - [PasswordChangeError.OtherAccount]: [proof] was an emailed link, and the new password
     *   does not sign [email] in. The link was for another account: that account's password is
     *   saved, and this device's session, which that reset did not touch, is kept as it was.
     */
    suspend fun changePassword(proof: EmailProof, email: String, password: String): Result<User, PasswordChangeError>

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

/** Why [AuthRepository.changePassword] did not leave this device signed in with the new password. */
sealed interface PasswordChangeError : Error {
    /** The reset was refused, or never answered: the password and the session are unchanged. */
    data class NotChanged(val error: DataError.Network) : PasswordChangeError

    /** The new password is saved, every session is revoked, and this device could not sign in again. */
    data class SignedOut(val error: DataError) : PasswordChangeError

    /**
     * The proof was an emailed link for another account: that account's password is saved,
     * and the session on this device is unchanged. A link's token names no account, so this
     * can only be known after the reset.
     */
    data object OtherAccount : PasswordChangeError
}
