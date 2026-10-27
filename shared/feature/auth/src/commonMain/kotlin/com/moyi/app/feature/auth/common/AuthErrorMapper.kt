package com.moyi.app.feature.auth.common

import com.moyi.app.core.ApiErrorCode
import com.moyi.app.core.DataError
import com.moyi.app.core.FieldError
import com.moyi.app.designsystem.presentation.UiText
import com.moyi.app.feature.auth.resources.Res
import com.moyi.app.feature.auth.resources.auth_email_error
import com.moyi.app.feature.auth.resources.auth_error_generic
import com.moyi.app.feature.auth.resources.auth_error_offline
import com.moyi.app.feature.auth.resources.auth_error_rate_limited
import com.moyi.app.feature.auth.resources.login_error_credentials
import com.moyi.app.feature.auth.resources.login_error_rate_limited
import com.moyi.app.feature.auth.resources.register_name_error_blank
import com.moyi.app.feature.auth.resources.register_name_error_long
import com.moyi.app.feature.auth.resources.register_password_error_breached
import com.moyi.app.feature.auth.resources.register_password_error_short
import com.moyi.app.feature.auth.resources.verify_token_error_invalid

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

    /** A form-level failure on register, verify or resend. */
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
     */
    fun registerFieldErrors(error: DataError): Map<RegisterField, UiText> {
        val problem = (error as? DataError.Network.Api)?.problem ?: return emptyMap()
        val out = linkedMapOf<RegisterField, UiText>()
        for (violation in problem.fieldErrors) {
            val field = RegisterField.entries.firstOrNull { it.wireName == violation.field } ?: continue
            if (field !in out) out[field] = registerFieldMessage(field, violation)
        }
        return out
    }

    /** 422 VERIFICATION_TOKEN_INVALID: the pasted code is wrong. Null for anything else. */
    fun verifyTokenError(error: DataError): UiText? =
        if (error.code() == ApiErrorCode.VERIFICATION_TOKEN_INVALID) {
            UiText.Resource(Res.string.verify_token_error_invalid)
        } else {
            null
        }

    /** 410 VERIFICATION_TOKEN_EXPIRED: expired or already used (doc 06 §3.1). */
    fun isVerificationExpired(error: DataError): Boolean = error.code() == ApiErrorCode.VERIFICATION_TOKEN_EXPIRED

    private fun registerFieldMessage(field: RegisterField, violation: FieldError): UiText = when (field) {
        RegisterField.Name -> UiText.Resource(
            if (violation.code == "SIZE") Res.string.register_name_error_long else Res.string.register_name_error_blank,
        )
        RegisterField.Email -> UiText.Resource(Res.string.auth_email_error)
        // NOT_BREACHED and VALID_PASSWORD are deliberately distinct codes so the
        // two get different copy (copy.md, the breached-password message).
        RegisterField.Password -> UiText.Resource(
            if (violation.code == "NOT_BREACHED") {
                Res.string.register_password_error_breached
            } else {
                Res.string.register_password_error_short
            },
        )
    }

    private fun DataError.isOffline(): Boolean =
        this == DataError.Network.NoInternet || this == DataError.Network.Timeout

    private fun DataError.code(): ApiErrorCode? = (this as? DataError.Network.Api)?.problem?.code
}
