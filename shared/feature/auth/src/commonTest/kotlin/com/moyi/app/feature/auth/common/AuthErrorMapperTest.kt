package com.moyi.app.feature.auth.common

import com.moyi.app.core.ApiErrorCode
import com.moyi.app.core.DataError
import com.moyi.app.designsystem.presentation.UiText
import com.moyi.app.feature.auth.apiError
import com.moyi.app.feature.auth.resources.Res
import com.moyi.app.feature.auth.resources.auth_email_error
import com.moyi.app.feature.auth.resources.auth_error_generic
import com.moyi.app.feature.auth.resources.auth_error_offline
import com.moyi.app.feature.auth.resources.auth_error_rate_limited
import com.moyi.app.feature.auth.resources.login_error_credentials
import com.moyi.app.feature.auth.resources.register_name_error_blank
import com.moyi.app.feature.auth.resources.register_name_error_long
import com.moyi.app.feature.auth.resources.register_password_error_breached
import com.moyi.app.feature.auth.resources.register_password_error_short
import com.moyi.app.feature.auth.resources.verify_token_error_invalid
import com.moyi.app.feature.auth.violation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthErrorMapperTest {
    private fun res(id: org.jetbrains.compose.resources.StringResource) = UiText.Resource(id)

    @Test
    fun noRouteAndTimeoutAreOfflineWithTryAgain() {
        for (error in listOf(DataError.Network.NoInternet, DataError.Network.Timeout)) {
            assertEquals(SubmitStatus.Failed(res(Res.string.auth_error_offline), true), AuthErrorMapper.formFailure(error))
        }
    }

    @Test
    fun anythingUnexplainedIsTheGenericMessage() {
        val generic = SubmitStatus.Failed(res(Res.string.auth_error_generic), true)
        for (error in listOf(
            DataError.Network.Server(502),
            DataError.Network.Serialization,
            DataError.Network.Unknown,
            DataError.Local.UNKNOWN,
            apiError(500, ApiErrorCode.INTERNAL_ERROR),
        )) {
            assertEquals(generic, AuthErrorMapper.formFailure(error), "for $error")
        }
    }

    @Test
    fun rateLimitedNeverOffersAnImmediateRetry() {
        assertEquals(
            SubmitStatus.Failed(res(Res.string.auth_error_rate_limited), false),
            AuthErrorMapper.formFailure(apiError(429, ApiErrorCode.RATE_LIMITED)),
        )
    }

    @Test
    fun loginReadsTheStatusNotTheCode() {
        val credentials = SubmitStatus.Failed(res(Res.string.login_error_credentials), false)
        assertEquals(credentials, AuthErrorMapper.loginFailure(apiError(401, ApiErrorCode.EMAIL_NOT_VERIFIED)))
        assertEquals(credentials, AuthErrorMapper.loginFailure(apiError(401, ApiErrorCode.Unknown)))
        assertEquals(
            SubmitStatus.Failed(res(Res.string.auth_error_offline), true),
            AuthErrorMapper.loginFailure(DataError.Network.NoInternet),
        )
    }

    @Test
    fun registerViolationsMapByFieldAndCode() {
        val error = apiError(
            422,
            ApiErrorCode.VALIDATION_FAILED,
            violation("displayName", "SIZE"),
            violation("email", "VALID_EMAIL"),
            violation("password", "NOT_BREACHED"),
            violation("password", "VALID_PASSWORD"),
            violation("locale", "PATTERN"),
        )
        assertEquals(
            mapOf(
                RegisterField.Name to res(Res.string.register_name_error_long),
                RegisterField.Email to res(Res.string.auth_email_error),
                // The first violation for a field wins.
                RegisterField.Password to res(Res.string.register_password_error_breached),
            ),
            AuthErrorMapper.registerFieldErrors(error),
        )
        assertEquals(
            mapOf(
                RegisterField.Name to res(Res.string.register_name_error_blank),
                RegisterField.Password to res(Res.string.register_password_error_short),
            ),
            AuthErrorMapper.registerFieldErrors(
                apiError(422, ApiErrorCode.VALIDATION_FAILED, violation("displayName", "NOT_BLANK"), violation("password", "VALID_PASSWORD")),
            ),
        )
    }

    @Test
    fun aViolationOnAFieldTheScreenDoesNotHaveIsNotAFieldError() {
        assertTrue(AuthErrorMapper.registerFieldErrors(apiError(422, ApiErrorCode.VALIDATION_FAILED, violation("locale", "PATTERN"))).isEmpty())
        assertTrue(AuthErrorMapper.registerFieldErrors(DataError.Network.NoInternet).isEmpty())
    }

    @Test
    fun verificationCodes() {
        assertEquals(res(Res.string.verify_token_error_invalid), AuthErrorMapper.verifyTokenError(apiError(422, ApiErrorCode.VERIFICATION_TOKEN_INVALID)))
        assertNull(AuthErrorMapper.verifyTokenError(apiError(410, ApiErrorCode.VERIFICATION_TOKEN_EXPIRED)))
        assertTrue(AuthErrorMapper.isVerificationExpired(apiError(410, ApiErrorCode.VERIFICATION_TOKEN_EXPIRED)))
        assertFalse(AuthErrorMapper.isVerificationExpired(apiError(422, ApiErrorCode.VERIFICATION_TOKEN_INVALID)))
    }
}
