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
import com.moyi.app.feature.auth.resources.code_error_unavailable
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

    /** Step one: type the code (the sixth digit checks it, right by default). */
    private fun typeCode(code: String = "123456", vm: ResetPasswordViewModel = viewModel) {
        vm.onAction(ResetPasswordAction.OnCodeChange(code))
    }

    /** Both steps: the code is checked, then the password typed. */
    private fun fill(code: String = "123456", password: String = "correct horse") {
        typeCode(code)
        viewModel.onAction(ResetPasswordAction.OnPasswordChange(password))
    }

    private fun assertInputsKept(code: String = "123456", password: String = "correct horse") {
        assertEquals(code, viewModel.state.value.code.value)
        assertEquals(password, viewModel.state.value.password.value)
    }

    // ----- Step one: the code -----

    @Test
    fun theScreenOpensOnTheCodeStep() = runTest {
        assertEquals(ResetStep.Code, viewModel.state.value.step)
        assertTrue(repo.checkResetCalls.isEmpty())
    }

    @Test
    fun theFieldKeepsOnlyDigitsAndTheSixthChecksTheCode() = runTest {
        viewModel.onAction(ResetPasswordAction.OnCodeChange("12 34-5"))
        assertEquals("12345", viewModel.state.value.code.value)
        assertTrue(repo.checkResetCalls.isEmpty())

        viewModel.onAction(ResetPasswordAction.OnCodeChange("12 34-56 7"))
        assertEquals("123456", viewModel.state.value.code.value)
        assertEquals(listOf("amara@example.com" to "123456"), repo.checkResetCalls)
        // Nothing is reset by a check.
        assertTrue(repo.resetCalls.isEmpty())
    }

    @Test
    fun aRightCodeOpensThePasswordStep() = runTest {
        typeCode()
        val state = viewModel.state.value
        assertEquals(ResetStep.Password, state.step)
        assertEquals("123456", state.code.value)
        assertNull(state.code.error)
        assertEquals(SubmitStatus.Idle, state.submit)
        assertTrue(repo.resetCalls.isEmpty())
    }

    @Test
    fun theCheckButtonChecksAFullCodeToo() = runTest {
        // A code the check could not reach (offline): the button sends it again.
        repo.checkResetResult = Result.Failure(DataError.Network.NoInternet)
        typeCode()
        assertEquals(ResetStep.Code, viewModel.state.value.step)

        repo.checkResetResult = Result.Success(Unit)
        viewModel.onAction(ResetPasswordAction.OnSubmit)
        assertEquals(ResetStep.Password, viewModel.state.value.step)
        assertEquals(2, repo.checkResetCalls.size)
    }

    @Test
    fun aWrongCodeIsAnErrorOnTheCodeFieldAndTheScreenStaysOnTheCode() = runTest {
        repo.checkResetResult = Result.Failure(apiError(422, ApiErrorCode.PASSWORD_RESET_TOKEN_INVALID))
        typeCode()
        val state = viewModel.state.value
        assertEquals(UiText.Resource(Res.string.code_error_invalid), state.code.error)
        assertEquals(ResetStep.Code, state.step)
        assertEquals(SubmitStatus.Idle, state.submit)
        assertEquals("123456", state.code.value)

        // The same six digits again would spend another of the five tries.
        viewModel.onAction(ResetPasswordAction.OnSubmit)
        assertEquals(1, repo.checkResetCalls.size)

        // A changed code is checked, and a right one clears the error and opens the password.
        repo.checkResetResult = Result.Success(Unit)
        viewModel.onAction(ResetPasswordAction.OnCodeChange("12345"))
        assertNull(viewModel.state.value.code.error)
        viewModel.onAction(ResetPasswordAction.OnCodeChange("123457"))
        assertEquals(2, repo.checkResetCalls.size)
        assertEquals(ResetStep.Password, viewModel.state.value.step)
    }

    @Test
    fun aBlankCodeIsAFieldErrorAndSendsNothing() = runTest {
        viewModel.onAction(ResetPasswordAction.OnSubmit)
        assertTrue(repo.checkResetCalls.isEmpty())
        assertEquals(UiText.Resource(Res.string.code_error_blank), viewModel.state.value.code.error)
        assertEquals(ResetStep.Code, viewModel.state.value.step)
    }

    @Test
    fun tooFewDigitsIsAFieldErrorAndSendsNothing() = runTest {
        viewModel.onAction(ResetPasswordAction.OnCodeChange("1234"))
        viewModel.onAction(ResetPasswordAction.OnSubmit)
        assertTrue(repo.checkResetCalls.isEmpty())
        assertEquals(UiText.Resource(Res.string.code_error_short), viewModel.state.value.code.error)
    }

    @Test
    fun tooManyCodesTodayIsAboveTheActionWithNoRetry() = runTest {
        repo.checkResetResult = Result.Failure(apiError(429, ApiErrorCode.RATE_LIMITED))
        typeCode()
        val state = viewModel.state.value
        assertEquals(SubmitStatus.Failed(UiText.Resource(Res.string.code_error_rate_limited), offerRetry = false), state.submit)
        assertNull(state.code.error)
        assertEquals(ResetStep.Code, state.step)
    }

    @Test
    fun theCheckBeingUnavailableOffersTryAgainAndKeepsTheCode() = runTest {
        repo.checkResetResult = Result.Failure(apiError(503, ApiErrorCode.CODE_CHECK_UNAVAILABLE))
        typeCode()
        assertEquals(SubmitStatus.Failed(UiText.Resource(Res.string.code_error_unavailable), offerRetry = true), viewModel.state.value.submit)
        assertEquals("123456", viewModel.state.value.code.value)
    }

    @Test
    fun offlineOnTheCheckIsAnInlineErrorAndKeepsTheCode() = runTest {
        repo.checkResetResult = Result.Failure(DataError.Network.NoInternet)
        typeCode()
        val state = viewModel.state.value
        assertEquals(SubmitStatus.Failed(UiText.Resource(Res.string.auth_error_offline), offerRetry = true), state.submit)
        assertNull(state.code.error)
        assertEquals("123456", state.code.value)
        assertEquals(ResetStep.Code, state.step)
    }

    @Test
    fun aDigitTypedClearsAFailureAboveTheAction() = runTest {
        repo.checkResetResult = Result.Failure(DataError.Network.NoInternet)
        typeCode()
        viewModel.onAction(ResetPasswordAction.OnCodeChange("12345"))
        assertEquals(SubmitStatus.Idle, viewModel.state.value.submit)
    }

    @Test
    fun aSecondTapWhileCheckingSendsNothingMoreAndTheCodeHolds() = runTest {
        val gate = CompletableDeferred<Unit>().also { repo.gate = it }
        typeCode()
        assertEquals(SubmitStatus.Sending, viewModel.state.value.submit)

        viewModel.onAction(ResetPasswordAction.OnSubmit)
        viewModel.onAction(ResetPasswordAction.OnCodeChange("1"))
        assertEquals(1, repo.checkResetCalls.size)
        assertEquals("123456", viewModel.state.value.code.value)

        gate.complete(Unit)
        assertEquals(ResetStep.Password, viewModel.state.value.step)
    }

    @Test
    fun withNoAddressATypedCodeAsksForTheLink() = runTest {
        val opened = ResetPasswordViewModel("", repo)
        typeCode(vm = opened)
        assertEquals(UiText.Resource(Res.string.code_error_no_address), opened.state.value.code.error)
        assertTrue(repo.checkResetCalls.isEmpty())
    }

    // ----- Back -----

    @Test
    fun backOnTheCodeLeavesTheScreen() = runTest {
        viewModel.events.test {
            viewModel.onAction(ResetPasswordAction.OnBackClick)
            assertEquals(ResetPasswordEvent.NavigateBack, awaitItem())
        }
    }

    @Test
    fun backOnThePasswordReturnsToTheCodeAsTyped() = runTest {
        fill()
        assertEquals(ResetStep.Password, viewModel.state.value.step)
        viewModel.onAction(ResetPasswordAction.OnBackClick)
        val state = viewModel.state.value
        assertEquals(ResetStep.Code, state.step)
        assertEquals("123456", state.code.value)
        assertEquals("", state.password.value)
        assertNull(state.code.error)
        assertTrue(repo.resetCalls.isEmpty())

        // The right code can be sent on again from the button.
        viewModel.onAction(ResetPasswordAction.OnSubmit)
        assertEquals(ResetStep.Password, viewModel.state.value.step)
    }

    // ----- Step two: saving -----

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
    fun aPastedLinkIsTheProofAndOpensThePasswordWithoutACheck() = runTest {
        typeCode("https://getmoyi.app/reset-password?token=abc123\n")
        assertEquals("", viewModel.state.value.code.value)
        assertEquals("abc123", viewModel.state.value.linkToken)
        assertEquals(ResetStep.Password, viewModel.state.value.step)
        assertTrue(repo.checkResetCalls.isEmpty())
        viewModel.onAction(ResetPasswordAction.OnPasswordChange("correct horse"))
        viewModel.onAction(ResetPasswordAction.OnSubmit)
        assertEquals(listOf<Pair<EmailProof, String>>(EmailProof.Link("abc123") to "correct horse"), repo.resetCalls)
    }

    @Test
    fun backFromAPastedLinkReturnsToTheCodeAndDropsTheLink() = runTest {
        typeCode("https://getmoyi.app/reset-password?token=abc123")
        viewModel.onAction(ResetPasswordAction.OnBackClick)
        assertEquals(ResetStep.Code, viewModel.state.value.step)
        assertNull(viewModel.state.value.linkToken)
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

    // ----- Refusals at the reset -----

    @Test
    fun aCodeRefusedAtTheResetSendsTheScreenBackToTheCodeWithTheError() = runTest {
        // Right at the check, expired or replaced by a newer email before the password was saved.
        repo.resetResult = Result.Failure(apiError(422, ApiErrorCode.PASSWORD_RESET_TOKEN_INVALID))
        fill()
        viewModel.onAction(ResetPasswordAction.OnSubmit)
        val state = viewModel.state.value
        assertEquals(ResetStep.Code, state.step)
        assertEquals(UiText.Resource(Res.string.code_error_invalid), state.code.error)
        assertNull(state.password.error)
        assertEquals(SubmitStatus.Idle, state.submit)
        assertTrue(repo.loginCalls.isEmpty())
        assertEquals("123456", state.code.value)

        // The same refused digits are not sent again; a changed code is checked.
        viewModel.onAction(ResetPasswordAction.OnSubmit)
        assertEquals(1, repo.checkResetCalls.size)
        viewModel.onAction(ResetPasswordAction.OnCodeChange("12345"))
        viewModel.onAction(ResetPasswordAction.OnCodeChange("123457"))
        assertEquals(2, repo.checkResetCalls.size)
        assertEquals(ResetStep.Password, viewModel.state.value.step)
    }

    @Test
    fun tooManyCodesAtTheResetIsAboveTheActionWithNoRetry() = runTest {
        repo.resetResult = Result.Failure(apiError(429, ApiErrorCode.RATE_LIMITED))
        fill()
        viewModel.onAction(ResetPasswordAction.OnSubmit)
        assertEquals(SubmitStatus.Failed(UiText.Resource(Res.string.code_error_rate_limited), offerRetry = false), viewModel.state.value.submit)
        assertEquals(ResetStep.Password, viewModel.state.value.step)
        assertInputsKept()
    }

    @Test
    fun anExpiredLinkSendsTheScreenToTheCodeAndTheFieldTakesACodeNext() = runTest {
        repo.resetResult = Result.Failure(apiError(410, ApiErrorCode.PASSWORD_RESET_TOKEN_EXPIRED))
        fill(code = "https://getmoyi.app/reset-password?token=old")
        viewModel.onAction(ResetPasswordAction.OnSubmit)
        val state = viewModel.state.value
        assertEquals(UiText.Resource(Res.string.reset_token_error_expired), state.code.error)
        assertNull(state.linkToken)
        assertEquals(ResetStep.Code, state.step)
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
    fun aBreachedPasswordIsAnErrorOnThePasswordFieldAndStaysOnThePassword() = runTest {
        repo.resetResult = Result.Failure(apiError(422, ApiErrorCode.VALIDATION_FAILED, violation("password", "NOT_BREACHED")))
        fill()
        viewModel.onAction(ResetPasswordAction.OnSubmit)
        val state = viewModel.state.value
        assertEquals(UiText.Resource(Res.string.register_password_error_breached), state.password.error)
        assertNull(state.code.error)
        assertEquals(ResetStep.Password, state.step)
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
        assertEquals(1, repo.checkResetCalls.size)
    }

    @Test
    fun offlineOnTheResetIsAnInlineErrorAndKeepsBothInputs() = runTest {
        repo.resetResult = Result.Failure(DataError.Network.NoInternet)
        fill()
        viewModel.onAction(ResetPasswordAction.OnSubmit)
        val state = viewModel.state.value
        assertEquals(SubmitStatus.Failed(UiText.Resource(Res.string.auth_error_offline), offerRetry = true), state.submit)
        assertNull(state.code.error)
        assertNull(state.password.error)
        assertEquals(ResetStep.Password, state.step)
        assertInputsKept()
    }

    @Test
    fun aSecondTapWhileSavingSendsNothingMore() = runTest {
        fill()
        val gate = CompletableDeferred<Unit>().also { repo.gate = it }
        viewModel.onAction(ResetPasswordAction.OnSubmit)
        assertEquals(SubmitStatus.Sending, viewModel.state.value.submit)

        viewModel.onAction(ResetPasswordAction.OnSubmit)
        viewModel.onAction(ResetPasswordAction.OnBackClick)
        assertEquals(1, repo.resetCalls.size)
        assertEquals(ResetStep.Password, viewModel.state.value.step)

        gate.complete(Unit)
        assertEquals(1, repo.resetCalls.size)
        assertEquals(1, repo.loginCalls.size)
    }

    // ----- Opened by the emailed link -----

    @Test
    fun aLinkThatOpenedTheAppStartsOnThePasswordAndSendsNothingUntilSubmit() = runTest {
        val opened = ResetPasswordViewModel("amara@example.com", repo, linkToken = "fromLink")
        assertEquals(ResetStep.Password, opened.state.value.step)
        assertEquals("fromLink", opened.state.value.linkToken)
        assertEquals("", opened.state.value.code.value)
        assertTrue(repo.resetCalls.isEmpty())
        assertTrue(repo.checkResetCalls.isEmpty())

        opened.onAction(ResetPasswordAction.OnPasswordChange("correct horse"))
        opened.events.test {
            opened.onAction(ResetPasswordAction.OnSubmit)
            assertEquals(ResetPasswordEvent.NavigateToHome, awaitItem())
        }
        assertEquals(listOf<Pair<EmailProof, String>>(EmailProof.Link("fromLink") to "correct horse"), repo.resetCalls)
        assertEquals(listOf("amara@example.com" to "correct horse"), repo.loginCalls)
    }

    @Test
    fun backOnALinkOpenedPasswordLeavesTheScreenThereIsNoCodeToReturnTo() = runTest {
        val opened = ResetPasswordViewModel("amara@example.com", repo, linkToken = "fromLink")
        opened.events.test {
            opened.onAction(ResetPasswordAction.OnBackClick)
            assertEquals(ResetPasswordEvent.NavigateBack, awaitItem())
        }
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
    fun aSpentLinkShowsTheExpiredFieldErrorOnTheCodeStep() = runTest {
        repo.resetResult = Result.Failure(apiError(410, ApiErrorCode.PASSWORD_RESET_TOKEN_EXPIRED))
        val opened = ResetPasswordViewModel("", repo, linkToken = "spent")
        opened.onAction(ResetPasswordAction.OnPasswordChange("correct horse"))
        opened.onAction(ResetPasswordAction.OnSubmit)
        assertEquals(UiText.Resource(Res.string.reset_token_error_expired), opened.state.value.code.error)
        assertEquals(ResetStep.Code, opened.state.value.step)
        assertEquals(SubmitStatus.Idle, opened.state.value.submit)
    }
}
