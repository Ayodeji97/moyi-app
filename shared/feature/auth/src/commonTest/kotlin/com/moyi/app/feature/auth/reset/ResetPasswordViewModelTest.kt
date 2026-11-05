package com.moyi.app.feature.auth.reset

import app.cash.turbine.test
import com.moyi.app.core.ApiErrorCode
import com.moyi.app.core.DataError
import com.moyi.app.core.Result
import com.moyi.app.designsystem.presentation.UiText
import com.moyi.app.feature.auth.FakeAuthRepository
import com.moyi.app.feature.auth.MainDispatcherTest
import com.moyi.app.feature.auth.apiError
import com.moyi.app.feature.auth.common.SubmitStatus
import com.moyi.app.feature.auth.resources.Res
import com.moyi.app.feature.auth.resources.auth_error_offline
import com.moyi.app.feature.auth.resources.register_password_error_breached
import com.moyi.app.feature.auth.resources.register_password_error_long
import com.moyi.app.feature.auth.resources.register_password_error_short
import com.moyi.app.feature.auth.resources.reset_token_error_expired
import com.moyi.app.feature.auth.resources.verify_token_error_blank
import com.moyi.app.feature.auth.resources.verify_token_error_invalid
import com.moyi.app.feature.auth.violation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResetPasswordViewModelTest : MainDispatcherTest() {
    private val repo = FakeAuthRepository()
    private val viewModel = ResetPasswordViewModel("amara@example.com", repo)

    private fun fill(token: String = "abc123", password: String = "correct horse") {
        viewModel.onAction(ResetPasswordAction.OnTokenChange(token))
        viewModel.onAction(ResetPasswordAction.OnPasswordChange(password))
    }

    private fun assertInputsKept(token: String = "abc123", password: String = "correct horse") {
        assertEquals(token, viewModel.state.value.token.value)
        assertEquals(password, viewModel.state.value.password.value)
    }

    @Test
    fun aBlankCodeIsAFieldErrorAndSendsNothing() = runTest {
        fill(token = "  ")
        viewModel.onAction(ResetPasswordAction.OnSubmit)
        assertTrue(repo.resetCalls.isEmpty())
        assertEquals(UiText.Resource(Res.string.verify_token_error_blank), viewModel.state.value.token.error)
        assertEquals(null, viewModel.state.value.password.error)
    }

    @Test
    fun aShortOrMissingPasswordIsAFieldErrorAndSendsNothing() = runTest {
        fill(password = "short")
        viewModel.onAction(ResetPasswordAction.OnSubmit)
        assertTrue(repo.resetCalls.isEmpty())
        assertEquals(UiText.Resource(Res.string.register_password_error_short), viewModel.state.value.password.error)

        viewModel.onAction(ResetPasswordAction.OnPasswordChange(""))
        viewModel.onAction(ResetPasswordAction.OnSubmit)
        assertTrue(repo.resetCalls.isEmpty())
        assertEquals(UiText.Resource(Res.string.register_password_error_short), viewModel.state.value.password.error)
    }

    @Test
    fun savedThenSignedInWithTheNewPasswordGoesHome() = runTest {
        fill(token = "https://getmoyi.app/reset-password?token=abc123\n")
        viewModel.events.test {
            viewModel.onAction(ResetPasswordAction.OnSubmit)
            assertEquals(ResetPasswordEvent.NavigateToHome, awaitItem())
        }
        assertEquals(listOf("abc123" to "correct horse"), repo.resetCalls)
        assertEquals(listOf("amara@example.com" to "correct horse"), repo.loginCalls)
        assertEquals(SubmitStatus.Idle, viewModel.state.value.submit)
    }

    @Test
    fun savedButTheSignInFailedGoesToSignInWithTheEmail() = runTest {
        repo.loginResult = Result.Failure(DataError.Network.NoInternet)
        fill()
        viewModel.events.test {
            viewModel.onAction(ResetPasswordAction.OnSubmit)
            assertEquals(ResetPasswordEvent.NavigateToLogin("amara@example.com"), awaitItem())
        }
        assertEquals(1, repo.loginCalls.size)
    }

    @Test
    fun anInvalidCodeIsAnErrorOnTheCodeField() = runTest {
        repo.resetResult = Result.Failure(apiError(422, ApiErrorCode.PASSWORD_RESET_TOKEN_INVALID))
        fill()
        viewModel.onAction(ResetPasswordAction.OnSubmit)
        val state = viewModel.state.value
        assertEquals(UiText.Resource(Res.string.verify_token_error_invalid), state.token.error)
        assertEquals(null, state.password.error)
        assertEquals(SubmitStatus.Idle, state.submit)
        assertTrue(repo.loginCalls.isEmpty())
        assertInputsKept()
    }

    @Test
    fun anExpiredLinkIsAnErrorOnTheCodeField() = runTest {
        repo.resetResult = Result.Failure(apiError(410, ApiErrorCode.PASSWORD_RESET_TOKEN_EXPIRED))
        fill()
        viewModel.onAction(ResetPasswordAction.OnSubmit)
        val state = viewModel.state.value
        assertEquals(UiText.Resource(Res.string.reset_token_error_expired), state.token.error)
        assertEquals(SubmitStatus.Idle, state.submit)
        assertTrue(repo.loginCalls.isEmpty())
        assertInputsKept()
    }

    @Test
    fun aBreachedPasswordIsAnErrorOnThePasswordField() = runTest {
        repo.resetResult = Result.Failure(apiError(422, ApiErrorCode.VALIDATION_FAILED, violation("password", "NOT_BREACHED")))
        fill()
        viewModel.onAction(ResetPasswordAction.OnSubmit)
        val state = viewModel.state.value
        assertEquals(UiText.Resource(Res.string.register_password_error_breached), state.password.error)
        assertEquals(null, state.token.error)
        assertEquals(SubmitStatus.Idle, state.submit)
        assertInputsKept()
    }

    @Test
    fun aRefusedPasswordShapeReadsAsRegisterDoes() = runTest {
        repo.resetResult = Result.Failure(apiError(422, ApiErrorCode.VALIDATION_FAILED, violation("password", "VALID_PASSWORD")))
        fill()
        viewModel.onAction(ResetPasswordAction.OnSubmit)
        // Eight or more was sent, so the server's VALID_PASSWORD means too long.
        assertEquals(UiText.Resource(Res.string.register_password_error_long), viewModel.state.value.password.error)
    }

    @Test
    fun aRefusedPasswordCanBeFixedAndSentAgainWithTheSameCode() = runTest {
        repo.resetResult = Result.Failure(apiError(422, ApiErrorCode.VALIDATION_FAILED, violation("password", "NOT_BREACHED")))
        fill()
        viewModel.onAction(ResetPasswordAction.OnSubmit)

        repo.resetResult = Result.Success(Unit)
        viewModel.onAction(ResetPasswordAction.OnPasswordChange("a better one"))
        assertEquals(null, viewModel.state.value.password.error)
        viewModel.events.test {
            viewModel.onAction(ResetPasswordAction.OnSubmit)
            assertEquals(ResetPasswordEvent.NavigateToHome, awaitItem())
        }
        assertEquals(listOf("abc123" to "correct horse", "abc123" to "a better one"), repo.resetCalls)
    }

    @Test
    fun offlineIsAnInlineErrorAndKeepsBothInputs() = runTest {
        repo.resetResult = Result.Failure(DataError.Network.NoInternet)
        fill()
        viewModel.onAction(ResetPasswordAction.OnSubmit)
        val state = viewModel.state.value
        assertEquals(SubmitStatus.Failed(UiText.Resource(Res.string.auth_error_offline), offerRetry = true), state.submit)
        assertEquals(null, state.token.error)
        assertEquals(null, state.password.error)
        assertInputsKept()
    }

    @Test
    fun aSecondTapWhileSavingSendsNothingMore() = runTest {
        val gate = CompletableDeferred<Unit>().also { repo.gate = it }
        fill()
        viewModel.onAction(ResetPasswordAction.OnSubmit)
        assertEquals(SubmitStatus.Sending, viewModel.state.value.submit)

        viewModel.onAction(ResetPasswordAction.OnSubmit)
        assertEquals(1, repo.resetCalls.size)

        gate.complete(Unit)
        assertEquals(1, repo.resetCalls.size)
        assertEquals(1, repo.loginCalls.size)
    }

    @Test
    fun backGoesBack() = runTest {
        viewModel.events.test {
            viewModel.onAction(ResetPasswordAction.OnBackClick)
            assertEquals(ResetPasswordEvent.NavigateBack, awaitItem())
        }
    }
}
