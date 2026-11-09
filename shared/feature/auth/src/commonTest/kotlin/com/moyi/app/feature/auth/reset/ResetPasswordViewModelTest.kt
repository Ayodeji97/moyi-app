package com.moyi.app.feature.auth.reset

import app.cash.turbine.test
import com.moyi.app.core.ApiErrorCode
import com.moyi.app.core.DataError
import com.moyi.app.core.Result
import com.moyi.app.designsystem.presentation.UiText
import com.moyi.app.domain.auth.EmailProof
import com.moyi.app.feature.auth.FakeAuthRepository
import com.moyi.app.feature.auth.MainDispatcherTest
import com.moyi.app.feature.auth.apiError
import com.moyi.app.feature.auth.common.SubmitStatus
import com.moyi.app.feature.auth.resources.Res
import com.moyi.app.feature.auth.resources.auth_error_offline
import com.moyi.app.feature.auth.resources.code_error_blank
import com.moyi.app.feature.auth.resources.code_error_invalid
import com.moyi.app.feature.auth.resources.code_error_link_invalid
import com.moyi.app.feature.auth.resources.code_error_no_address
import com.moyi.app.feature.auth.resources.code_error_rate_limited
import com.moyi.app.feature.auth.resources.code_error_short
import com.moyi.app.feature.auth.resources.register_password_error_breached
import com.moyi.app.feature.auth.resources.register_password_error_long
import com.moyi.app.feature.auth.resources.register_password_error_short
import com.moyi.app.feature.auth.resources.reset_token_error_expired
import com.moyi.app.feature.auth.violation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ResetPasswordViewModelTest : MainDispatcherTest() {
    private val repo = FakeAuthRepository()
    private val viewModel = ResetPasswordViewModel("amara@example.com", repo)

    private val typedCode = EmailProof.Code("amara@example.com", "123456")

    private fun fill(code: String = "123456", password: String = "correct horse") {
        viewModel.onAction(ResetPasswordAction.OnCodeChange(code))
        viewModel.onAction(ResetPasswordAction.OnPasswordChange(password))
    }

    private fun assertInputsKept(code: String = "123456", password: String = "correct horse") {
        assertEquals(code, viewModel.state.value.code.value)
        assertEquals(password, viewModel.state.value.password.value)
    }

    // ----- The code field -----

    @Test
    fun theFieldKeepsOnlyDigitsAndTheSixthSendsNothing() = runTest {
        viewModel.onAction(ResetPasswordAction.OnCodeChange("12 34-56 7"))
        assertEquals("123456", viewModel.state.value.code.value)
        // The server checks a reset code only with the new password: nothing goes yet.
        assertTrue(repo.resetCalls.isEmpty())
    }

    @Test
    fun aBlankCodeIsAFieldErrorAndSendsNothing() = runTest {
        fill(code = "  ")
        viewModel.onAction(ResetPasswordAction.OnSubmit)
        assertTrue(repo.resetCalls.isEmpty())
        assertEquals(UiText.Resource(Res.string.code_error_blank), viewModel.state.value.code.error)
        assertNull(viewModel.state.value.password.error)
    }

    @Test
    fun tooFewDigitsIsAFieldErrorAndSendsNothing() = runTest {
        fill(code = "1234")
        viewModel.onAction(ResetPasswordAction.OnSubmit)
        assertTrue(repo.resetCalls.isEmpty())
        assertEquals(UiText.Resource(Res.string.code_error_short), viewModel.state.value.code.error)
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

    // ----- Saving -----

    @Test
    fun theCodeGoesWithTheAddressThenSignedInGoesHome() = runTest {
        fill()
        viewModel.events.test {
            viewModel.onAction(ResetPasswordAction.OnSubmit)
            assertEquals(ResetPasswordEvent.NavigateToHome, awaitItem())
        }
        assertEquals(listOf<Pair<EmailProof, String>>(typedCode to "correct horse"), repo.resetCalls)
        assertEquals(listOf("amara@example.com" to "correct horse"), repo.loginCalls)
        assertEquals(SubmitStatus.Idle, viewModel.state.value.submit)
    }

    @Test
    fun aPastedLinkIsTheProofAndIsNeverShown() = runTest {
        fill(code = "https://getmoyi.app/reset-password?token=abc123\n")
        assertEquals("", viewModel.state.value.code.value)
        assertEquals("abc123", viewModel.state.value.linkToken)
        viewModel.onAction(ResetPasswordAction.OnSubmit)
        assertEquals(listOf<Pair<EmailProof, String>>(EmailProof.Link("abc123") to "correct horse"), repo.resetCalls)
    }

    @Test
    fun typingADigitAfterALinkMakesTheCodeTheProof() = runTest {
        fill(code = "https://getmoyi.app/reset-password?token=abc123")
        viewModel.onAction(ResetPasswordAction.OnCodeChange("123456"))
        assertNull(viewModel.state.value.linkToken)
        viewModel.onAction(ResetPasswordAction.OnSubmit)
        assertEquals(typedCode, repo.resetCalls.single().first)
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

    // ----- Refusals -----

    @Test
    fun aRefusedCodeIsAnErrorOnTheCodeFieldAndIsNotSentAgainUnchanged() = runTest {
        repo.resetResult = Result.Failure(apiError(422, ApiErrorCode.PASSWORD_RESET_TOKEN_INVALID))
        fill()
        viewModel.onAction(ResetPasswordAction.OnSubmit)
        val state = viewModel.state.value
        assertEquals(UiText.Resource(Res.string.code_error_invalid), state.code.error)
        assertNull(state.password.error)
        assertEquals(SubmitStatus.Idle, state.submit)
        assertTrue(repo.loginCalls.isEmpty())
        assertInputsKept()

        // The same six digits again would spend another of the five tries.
        viewModel.onAction(ResetPasswordAction.OnSubmit)
        assertEquals(1, repo.resetCalls.size)

        viewModel.onAction(ResetPasswordAction.OnCodeChange("12345"))
        viewModel.onAction(ResetPasswordAction.OnCodeChange("123457"))
        viewModel.onAction(ResetPasswordAction.OnSubmit)
        assertEquals(2, repo.resetCalls.size)
    }

    @Test
    fun tooManyCodesTodayIsAboveTheActionWithNoRetry() = runTest {
        repo.resetResult = Result.Failure(apiError(429, ApiErrorCode.RATE_LIMITED))
        fill()
        viewModel.onAction(ResetPasswordAction.OnSubmit)
        assertEquals(SubmitStatus.Failed(UiText.Resource(Res.string.code_error_rate_limited), offerRetry = false), viewModel.state.value.submit)
        assertInputsKept()
    }

    @Test
    fun anExpiredLinkIsAnErrorOnTheCodeFieldAndTheFieldTakesACodeNext() = runTest {
        repo.resetResult = Result.Failure(apiError(410, ApiErrorCode.PASSWORD_RESET_TOKEN_EXPIRED))
        fill(code = "https://getmoyi.app/reset-password?token=old")
        viewModel.onAction(ResetPasswordAction.OnSubmit)
        val state = viewModel.state.value
        assertEquals(UiText.Resource(Res.string.reset_token_error_expired), state.code.error)
        assertNull(state.linkToken)
        assertEquals(SubmitStatus.Idle, state.submit)
        assertTrue(repo.loginCalls.isEmpty())
    }

    @Test
    fun aLinkThatMatchesNothingSaysSo() = runTest {
        repo.resetResult = Result.Failure(apiError(422, ApiErrorCode.PASSWORD_RESET_TOKEN_INVALID))
        fill(code = "https://getmoyi.app/reset-password?token=nope")
        viewModel.onAction(ResetPasswordAction.OnSubmit)
        assertEquals(UiText.Resource(Res.string.code_error_link_invalid), viewModel.state.value.code.error)
    }

    @Test
    fun aBreachedPasswordIsAnErrorOnThePasswordField() = runTest {
        repo.resetResult = Result.Failure(apiError(422, ApiErrorCode.VALIDATION_FAILED, violation("password", "NOT_BREACHED")))
        fill()
        viewModel.onAction(ResetPasswordAction.OnSubmit)
        val state = viewModel.state.value
        assertEquals(UiText.Resource(Res.string.register_password_error_breached), state.password.error)
        assertNull(state.code.error)
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
        assertNull(viewModel.state.value.password.error)
        viewModel.events.test {
            viewModel.onAction(ResetPasswordAction.OnSubmit)
            assertEquals(ResetPasswordEvent.NavigateToHome, awaitItem())
        }
        assertEquals(listOf<Pair<EmailProof, String>>(typedCode to "correct horse", typedCode to "a better one"), repo.resetCalls)
    }

    @Test
    fun offlineIsAnInlineErrorAndKeepsBothInputs() = runTest {
        repo.resetResult = Result.Failure(DataError.Network.NoInternet)
        fill()
        viewModel.onAction(ResetPasswordAction.OnSubmit)
        val state = viewModel.state.value
        assertEquals(SubmitStatus.Failed(UiText.Resource(Res.string.auth_error_offline), offerRetry = true), state.submit)
        assertNull(state.code.error)
        assertNull(state.password.error)
        assertInputsKept()
    }

    @Test
    fun aSecondTapWhileSavingSendsNothingMoreAndTheCodeHolds() = runTest {
        val gate = CompletableDeferred<Unit>().also { repo.gate = it }
        fill()
        viewModel.onAction(ResetPasswordAction.OnSubmit)
        assertEquals(SubmitStatus.Sending, viewModel.state.value.submit)

        viewModel.onAction(ResetPasswordAction.OnSubmit)
        viewModel.onAction(ResetPasswordAction.OnCodeChange("1"))
        assertEquals(1, repo.resetCalls.size)
        assertEquals("123456", viewModel.state.value.code.value)

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

    // ----- Opened by the emailed link -----

    @Test
    fun aLinkThatOpenedTheAppIsTheProofAndSendsNothingUntilSubmit() = runTest {
        val opened = ResetPasswordViewModel("amara@example.com", repo, linkToken = "fromLink")
        assertEquals("fromLink", opened.state.value.linkToken)
        assertEquals("", opened.state.value.code.value)
        assertTrue(repo.resetCalls.isEmpty())

        opened.onAction(ResetPasswordAction.OnPasswordChange("correct horse"))
        opened.events.test {
            opened.onAction(ResetPasswordAction.OnSubmit)
            assertEquals(ResetPasswordEvent.NavigateToHome, awaitItem())
        }
        assertEquals(listOf<Pair<EmailProof, String>>(EmailProof.Link("fromLink") to "correct horse"), repo.resetCalls)
        assertEquals(listOf("amara@example.com" to "correct horse"), repo.loginCalls)
    }

    @Test
    fun withNoAddressTheSavedPasswordGoesToSignInWithoutTryingToSignIn() = runTest {
        val opened = ResetPasswordViewModel("", repo, linkToken = "fromLink")
        opened.onAction(ResetPasswordAction.OnPasswordChange("correct horse"))
        opened.events.test {
            opened.onAction(ResetPasswordAction.OnSubmit)
            assertEquals(ResetPasswordEvent.NavigateToLogin(""), awaitItem())
        }
        assertTrue(repo.loginCalls.isEmpty())
    }

    @Test
    fun withNoAddressATypedCodeAsksForTheLink() = runTest {
        val opened = ResetPasswordViewModel("", repo)
        opened.onAction(ResetPasswordAction.OnCodeChange("123456"))
        opened.onAction(ResetPasswordAction.OnPasswordChange("correct horse"))
        opened.onAction(ResetPasswordAction.OnSubmit)
        assertEquals(UiText.Resource(Res.string.code_error_no_address), opened.state.value.code.error)
        assertTrue(repo.resetCalls.isEmpty())
    }

    @Test
    fun aSpentLinkShowsTheExpiredFieldError() = runTest {
        repo.resetResult = Result.Failure(apiError(410, ApiErrorCode.PASSWORD_RESET_TOKEN_EXPIRED))
        val opened = ResetPasswordViewModel("", repo, linkToken = "spent")
        opened.onAction(ResetPasswordAction.OnPasswordChange("correct horse"))
        opened.onAction(ResetPasswordAction.OnSubmit)
        assertEquals(UiText.Resource(Res.string.reset_token_error_expired), opened.state.value.code.error)
        assertEquals(SubmitStatus.Idle, opened.state.value.submit)
    }
}
