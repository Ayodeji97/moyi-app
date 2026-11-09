package com.moyi.app.feature.auth.common

import com.moyi.app.core.ApiErrorCode
import com.moyi.app.core.DataError
import com.moyi.app.core.FieldError
import com.moyi.app.designsystem.presentation.UiText
import com.moyi.app.domain.auth.EmailProof
import com.moyi.app.feature.auth.resources.Res
import com.moyi.app.feature.auth.resources.auth_email_error
import com.moyi.app.feature.auth.resources.auth_error_generic
import com.moyi.app.feature.auth.resources.auth_error_offline
import com.moyi.app.feature.auth.resources.auth_error_rate_limited
import com.moyi.app.feature.auth.resources.code_error_invalid
import com.moyi.app.feature.auth.resources.code_error_link_invalid
import com.moyi.app.feature.auth.resources.code_error_rate_limited
import com.moyi.app.feature.auth.resources.code_error_unavailable
import com.moyi.app.feature.auth.resources.login_error_credentials
import com.moyi.app.feature.auth.resources.login_error_rate_limited
import com.moyi.app.feature.auth.resources.register_name_error_blank
import com.moyi.app.feature.auth.resources.register_name_error_long
import com.moyi.app.feature.auth.resources.register_password_error_breached
import com.moyi.app.feature.auth.resources.register_password_error_long
import com.moyi.app.feature.auth.resources.register_password_error_short
import com.moyi.app.feature.auth.resources.reset_token_error_expired

/** The register request's fields, as the server names them in `errors[].field`. */
enum class RegisterField(val wireName: String) {
    Name("displayName"),
    Email("email"),
    Password("password"),
}

/**
 * The one place a [DataError] becomes words on an auth screen.
 *
 * It switches on the server's `code`, never on its English `detail`
 * (states.md §1c: "the breach case is recognised by a code, not by a
 * sentence"). Field-level problems go to [registerFieldErrors]; everything
 * else becomes a form-level [SubmitStatus.Failed].
 */
object AuthErrorMapper {

    /** A form-level failure on register, verify, resend, or either password-reset request. */
    fun formFailure(error: DataError): SubmitStatus.Failed = when {
        error.isOffline() -> SubmitStatus.Failed(UiText.Resource(Res.string.auth_error_offline), offerRetry = true)
        error.code() == ApiErrorCode.RATE_LIMITED ->
            SubmitStatus.Failed(UiText.Resource(Res.string.auth_error_rate_limited), offerRetry = false)
        else -> SubmitStatus.Failed(UiText.Resource(Res.string.auth_error_generic), offerRetry = true)
    }

    /**
     * A sign-in failure. **Every 401 is the same sentence**, whatever its code
     * (wrong password, unknown address, locked, suspended): telling them apart
     * would make this screen an oracle for which addresses have accounts
     * (states.md §1c, T-18). Only the status is read.
     */
    fun loginFailure(error: DataError): SubmitStatus.Failed {
        val api = (error as? DataError.Network.Api)?.problem
        return when {
            api?.status == 401 || api?.code == ApiErrorCode.INVALID_CREDENTIALS ->
                SubmitStatus.Failed(UiText.Resource(Res.string.login_error_credentials), offerRetry = false)
            api?.code == ApiErrorCode.RATE_LIMITED ->
                SubmitStatus.Failed(UiText.Resource(Res.string.login_error_rate_limited), offerRetry = false)
            else -> formFailure(error)
        }
    }

    /**
     * The register 422's `errors[]`, one message per field we show. The first
     * violation for a field wins. A violation on a field the screen does not
     * have (locale, acceptedTermsVersion) is not here; the caller falls back
     * to [formFailure] when this is empty.
     *
     * [submittedPassword] is the password that was sent. The server answers
     * VALID_PASSWORD both for too short and for too long, so its length
     * decides which sentence is true.
     */
    fun registerFieldErrors(error: DataError, submittedPassword: String): Map<RegisterField, UiText> {
        val problem = (error as? DataError.Network.Api)?.problem ?: return emptyMap()
        val out = linkedMapOf<RegisterField, UiText>()
        for (violation in problem.fieldErrors) {
            val field = RegisterField.entries.firstOrNull { it.wireName == violation.field } ?: continue
            if (field !in out) out[field] = registerFieldMessage(field, violation, submittedPassword)
        }
        return out
    }

    /**
     * A refused verify proof that belongs under the code field, or null.
     *
     * `422 VERIFICATION_TOKEN_INVALID` is one answer for a link that matches nothing and for
     * every refused code: wrong, expired (15 minutes), stopped after five wrong tries, or
     * spent. The server tells them apart for nobody, on purpose (it would say which
     * addresses have a live code), so [Res.string.code_error_invalid] covers them all.
     * A field violation on `token`, `code` or `email` reads the same: the app never sends
     * one, so it can only mean the proof was not accepted.
     */
    fun verifyProofError(error: DataError, proof: EmailProof): UiText? =
        proofError(error, proof, ApiErrorCode.VERIFICATION_TOKEN_INVALID)

    /** 410 VERIFICATION_TOKEN_EXPIRED: a link expired or already used (doc 06 §3.1). A code is never answered 410. */
    fun isVerificationExpired(error: DataError): Boolean = error.code() == ApiErrorCode.VERIFICATION_TOKEN_EXPIRED

    /**
     * The reset-password refusal that belongs under the code field, or null.
     *
     * - `410 PASSWORD_RESET_TOKEN_EXPIRED`: a link expired or already used. Matched on the
     *   code, since the contract once declared no 410 (backend-contract-gaps.md #15).
     * - `422 PASSWORD_RESET_TOKEN_INVALID`, or a field violation on the proof: as on verify.
     */
    fun resetProofError(error: DataError, proof: EmailProof): UiText? =
        if (error.code() == ApiErrorCode.PASSWORD_RESET_TOKEN_EXPIRED) {
            UiText.Resource(Res.string.reset_token_error_expired)
        } else {
            proofError(error, proof, ApiErrorCode.PASSWORD_RESET_TOKEN_INVALID)
        }

    /**
     * A refused proof that is not the field's problem, above the action. Two are a typed
     * code's own, and both point at the link in the same email, which neither limit touches:
     *
     * - `429`: ten codes a day per address (ADR-0023 amended 2026-11-08). No retry offered.
     * - `503 CODE_CHECK_UNAVAILABLE` (`Retry-After: 60`): nothing was checked or counted.
     *
     * Everything else, a link's 429 included, is [formFailure].
     */
    fun proofFormFailure(error: DataError, proof: EmailProof): SubmitStatus.Failed = when {
        proof is EmailProof.Code && error.code() == ApiErrorCode.RATE_LIMITED ->
            SubmitStatus.Failed(UiText.Resource(Res.string.code_error_rate_limited), offerRetry = false)
        proof is EmailProof.Code && error.code() == ApiErrorCode.CODE_CHECK_UNAVAILABLE ->
            SubmitStatus.Failed(UiText.Resource(Res.string.code_error_unavailable), offerRetry = true)
        else -> formFailure(error)
    }

    private fun proofError(error: DataError, proof: EmailProof, invalid: ApiErrorCode): UiText? {
        val problem = (error as? DataError.Network.Api)?.problem ?: return null
        val refused = problem.code == invalid || problem.fieldErrors.any { it.field in PROOF_FIELDS }
        if (!refused) return null
        return UiText.Resource(
            when (proof) {
                is EmailProof.Link -> Res.string.code_error_link_invalid
                is EmailProof.Code -> Res.string.code_error_invalid
            },
        )
    }

    /** The proof's fields, as the server names them in `errors[].field`. */
    private val PROOF_FIELDS = setOf("token", "code", "email")

    /**
     * A reset-password 422 on the new password, in register's words: the
     * server checks the same rules (VALID_PASSWORD, NOT_BREACHED) on the same
     * field name. Null when the password was not the problem.
     */
    fun resetPasswordError(error: DataError, submittedPassword: String): UiText? =
        registerFieldErrors(error, submittedPassword)[RegisterField.Password]

    private fun registerFieldMessage(field: RegisterField, violation: FieldError, submittedPassword: String): UiText = when (field) {
        RegisterField.Name -> UiText.Resource(
            if (violation.code == "SIZE") Res.string.register_name_error_long else Res.string.register_name_error_blank,
        )
        RegisterField.Email -> UiText.Resource(Res.string.auth_email_error)
        // NOT_BREACHED and VALID_PASSWORD are deliberately distinct codes so the
        // two get different copy (copy.md, the breached-password message).
        RegisterField.Password -> UiText.Resource(
            when {
                violation.code == "NOT_BREACHED" -> Res.string.register_password_error_breached
                submittedPassword.length < AuthValidation.PASSWORD_MIN_LENGTH -> Res.string.register_password_error_short
                else -> Res.string.register_password_error_long
            },
        )
    }

    private fun DataError.isOffline(): Boolean =
        this == DataError.Network.NoInternet || this == DataError.Network.Timeout

    private fun DataError.code(): ApiErrorCode? = (this as? DataError.Network.Api)?.problem?.code
}
