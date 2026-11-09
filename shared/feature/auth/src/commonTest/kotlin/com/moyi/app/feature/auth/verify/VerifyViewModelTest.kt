package com.moyi.app.feature.auth.verify

import app.cash.turbine.test
import com.moyi.app.core.ApiErrorCode
import com.moyi.app.core.DataError
import com.moyi.app.core.Result
import com.moyi.app.designsystem.presentation.UiText
import com.moyi.app.domain.auth.EmailProof
import com.moyi.app.feature.auth.FakeAuthRepository
import com.moyi.app.feature.auth.MainDispatcherTest
import com.moyi.app.feature.auth.apiError
import com.moyi.app.feature.auth.common.AuthErrorMapper
import com.moyi.app.feature.auth.common.SubmitStatus
import com.moyi.app.feature.auth.resources.Res
import com.moyi.app.feature.auth.resources.code_error_blank
import com.moyi.app.feature.auth.resources.code_error_invalid
import com.moyi.app.feature.auth.resources.code_error_link_invalid
import com.moyi.app.feature.auth.resources.code_error_no_address
import com.moyi.app.feature.auth.resources.code_error_rate_limited
import com.moyi.app.feature.auth.resources.code_error_short
import com.moyi.app.feature.auth.resources.code_error_unavailable
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VerifyViewModelTest : MainDispatcherTest() {
    private val repo = FakeAuthRepository()
    private val viewModel = VerifyViewModel("amara@example.com", repo)

    private fun type(value: String) = viewModel.onAction(VerifyAction.OnCodeChange(value))

    private val code get() = viewModel.state.value.code

    // ----- Input: digits only, six at most -----

    @Test
    fun theFieldKeepsOnlyDigitsAndAtMostSix() = runTest {
        type("12a")
        assertEquals("12", code.value)
        type("12 3-4")
        assertEquals("1234", code.value)
        assertTrue(repo.verifyCalls.isEmpty())
    }

    @Test
    fun anotherScriptsDigitsBecomeAscii() = runTest {
        type("١٢٣")
        assertEquals("123", code.value)
    }

    // ----- Auto-submit -----

    @Test
    fun theSixthDigitChecksTheCodeWithTheAddressAndSuccessGoesToLogin() = runTest {
        viewModel.events.test {
            type("04291")
            assertTrue(repo.verifyCalls.isEmpty())
            type("042917")
            assertEquals(VerifyEvent.NavigateToLogin("amara@example.com"), awaitItem())
        }
        assertEquals(listOf<EmailProof>(EmailProof.Code("amara@example.com", "042917")), repo.verifyCalls)
    }

    @Test
    fun aPastedCodeFillsTheFieldAndIsCheckedOnce() = runTest {
        repo.verifyResult = Result.Failure(DataError.Network.NoInternet)
        type("Your code: 482 913\n")
        assertEquals("482913", code.value)
        // A seventh digit, or the same paste again, changes nothing and sends nothing more.
        type("4829135")
        type("482913")
        assertEquals(1, repo.verifyCalls.size)
    }

    @Test
    fun theCheckShowsSendingAndIgnoresEditsAndTapsUntilItAnswers() = runTest {
        val gate = CompletableDeferred<Unit>().also { repo.gate = it }
        type("123456")
        assertEquals(SubmitStatus.Sending, viewModel.state.value.submit)

        type("12345")
        viewModel.onAction(VerifyAction.OnSubmit)
        assertEquals("123456", code.value)
        assertEquals(1, repo.verifyCalls.size)

        gate.complete(Unit)
        assertEquals(SubmitStatus.Idle, viewModel.state.value.submit)
    }

    @Test
    fun aRefusedCodeIsNotSentAgainUntilItChanges() = runTest {
        repo.verifyResult = Result.Failure(apiError(422, ApiErrorCode.VERIFICATION_TOKEN_INVALID))
        type("111111")
        assertEquals(UiText.Resource(Res.string.code_error_invalid), code.error)
        assertEquals(SubmitStatus.Idle, viewModel.state.value.submit)

        // "Check again" or Done on the same digits would only spend another of the five tries.
        viewModel.onAction(VerifyAction.OnSubmit)
        assertEquals(1, repo.verifyCalls.size)

        // Editing clears the verdict; the new sixth digit checks the new code.
        type("11111")
        assertNull(code.error)
        repo.verifyResult = Result.Success(Unit)
        type("111112")
        assertEquals(2, repo.verifyCalls.size)
    }

    // ----- A pasted link -----

    @Test
    fun aPastedLinkSendsOnlyItsTokenAndIsNeverShown() = runTest {
        viewModel.events.test {
            type("https://getmoyi.app/verify?token=abc123&utm=x\n")
            assertEquals(VerifyEvent.NavigateToLogin("amara@example.com"), awaitItem())
        }
        assertEquals(listOf<EmailProof>(EmailProof.Link("abc123")), repo.verifyCalls)
        assertEquals("", code.value)
    }

    @Test
    fun aPastedLinkThatMatchesNothingSaysSoUnderTheField() = runTest {
        repo.verifyResult = Result.Failure(apiError(422, ApiErrorCode.VERIFICATION_TOKEN_INVALID))
        type("http://localhost:3000/verify?token=nope")
        assertEquals(UiText.Resource(Res.string.code_error_link_invalid), code.error)
    }

    // ----- The action -----

    @Test
    fun checkAgainWithNothingTypedIsAFieldErrorAndSendsNothing() = runTest {
        viewModel.onAction(VerifyAction.OnSubmit)
        assertTrue(repo.verifyCalls.isEmpty())
        assertEquals(UiText.Resource(Res.string.code_error_blank), code.error)
    }

    @Test
    fun checkAgainWithTooFewDigitsSaysHowManyAndSendsNothing() = runTest {
        type("123")
        viewModel.onAction(VerifyAction.OnSubmit)
        assertTrue(repo.verifyCalls.isEmpty())
        assertEquals(UiText.Resource(Res.string.code_error_short), code.error)
    }

    @Test
    fun anOfflineCheckCanBeTriedAgainWithTheSameCode() = runTest {
        repo.verifyResult = Result.Failure(DataError.Network.NoInternet)
        type("123456")
        assertEquals(AuthErrorMapper.formFailure(DataError.Network.NoInternet), viewModel.state.value.submit)
        assertNull(code.error)

        repo.verifyResult = Result.Success(Unit)
        viewModel.onAction(VerifyAction.OnSubmit)
        assertEquals(2, repo.verifyCalls.size)
    }

    // ----- Error mapping -----

    @Test
    fun tooManyCodesTodayPointsAtTheLinkAboveTheAction() = runTest {
        repo.verifyResult = Result.Failure(apiError(429, ApiErrorCode.RATE_LIMITED))
        type("123456")
        assertEquals(SubmitStatus.Failed(UiText.Resource(Res.string.code_error_rate_limited), offerRetry = false), viewModel.state.value.submit)
        assertNull(code.error)
    }

    @Test
    fun codesThatCannotBeCheckedJustNowPointAtTheLinkAndOfferARetry() = runTest {
        repo.verifyResult = Result.Failure(apiError(503, ApiErrorCode.CODE_CHECK_UNAVAILABLE))
        type("123456")
        assertEquals(SubmitStatus.Failed(UiText.Resource(Res.string.code_error_unavailable), offerRetry = true), viewModel.state.value.submit)
    }

    @Test
    fun anExpiredLinkShowsTheExpiredStateAndSendANewLinkReturnsToWaiting() = runTest {
        repo.verifyResult = Result.Failure(apiError(410, ApiErrorCode.VERIFICATION_TOKEN_EXPIRED))
        type("https://getmoyi.app/verify?token=old")
        assertEquals(VerifyMode.Expired, viewModel.state.value.mode)

        // The code field is hidden while expired; keystrokes are ignored.
        type("123456")
        assertEquals(1, repo.verifyCalls.size)

        viewModel.onAction(VerifyAction.OnResendClick)
        val state = viewModel.state.value
        assertEquals(listOf("amara@example.com"), repo.resendCalls)
        assertEquals(VerifyMode.Waiting, state.mode)
        assertEquals("", state.code.value)
        assertEquals(ResendStatus.Sent, state.resend)
    }

    // ----- Resend -----

    @Test
    fun resendClearsTheOldCodeBecauseANewEmailVoidsIt() = runTest {
        type("123")
        viewModel.onAction(VerifyAction.OnResendClick)
        assertEquals("", code.value)
        assertEquals(ResendStatus.Sent, viewModel.state.value.resend)
    }

    @Test
    fun resendWaitsOutTheServersMinuteThenComesBack() = runTest {
        viewModel.onAction(VerifyAction.OnResendClick)
        assertEquals(ResendStatus.Sent, viewModel.state.value.resend)
        viewModel.onAction(VerifyAction.OnResendClick)
        assertEquals(1, repo.resendCalls.size)

        advanceTimeBy(59_000)
        assertEquals(ResendStatus.Sent, viewModel.state.value.resend)
        advanceTimeBy(2_000)
        assertEquals(ResendStatus.Ready, viewModel.state.value.resend)

        viewModel.onAction(VerifyAction.OnResendClick)
        assertEquals(2, repo.resendCalls.size)
    }

    @Test
    fun aRefusedResendSaysWhyAboveTheAction() = runTest {
        val limited = apiError(429, ApiErrorCode.RATE_LIMITED)
        repo.resendResult = Result.Failure(limited)
        viewModel.onAction(VerifyAction.OnResendClick)
        assertEquals(ResendStatus.Ready, viewModel.state.value.resend)
        assertEquals(AuthErrorMapper.formFailure(limited).message, viewModel.state.value.resendFailure)
    }

    @Test
    fun sendANewLinkFromExpiredWaitsOutTheResendCooldown() = runTest {
        viewModel.onAction(VerifyAction.OnResendClick)
        assertEquals(ResendStatus.Sent, viewModel.state.value.resend)

        repo.verifyResult = Result.Failure(apiError(410, ApiErrorCode.VERIFICATION_TOKEN_EXPIRED))
        type("https://getmoyi.app/verify?token=old")
        assertEquals(VerifyMode.Expired, viewModel.state.value.mode)

        // "Send a new link" within the minute sends nothing: the server would refuse it.
        viewModel.onAction(VerifyAction.OnResendClick)
        assertEquals(1, repo.resendCalls.size)
        assertEquals(VerifyMode.Expired, viewModel.state.value.mode)
        assertEquals(SubmitStatus.Idle, viewModel.state.value.submit)
    }

    @Test
    fun sendItAgainWhileCheckingNeitherReopensCheckAgainNorOverwritesItsVerdict() = runTest {
        val verifying = CompletableDeferred<Unit>().also { repo.verifyGate = it }
        repo.verifyResult = Result.Failure(DataError.Network.NoInternet)
        type("123456")

        viewModel.onAction(VerifyAction.OnResendClick)
        assertEquals(SubmitStatus.Sending, viewModel.state.value.submit)
        viewModel.onAction(VerifyAction.OnSubmit)
        assertEquals(1, repo.verifyCalls.size)

        verifying.complete(Unit)
        assertEquals(AuthErrorMapper.formFailure(DataError.Network.NoInternet), viewModel.state.value.submit)
        assertEquals(1, repo.verifyCalls.size)
        assertTrue(repo.resendCalls.isEmpty())
    }

    @Test
    fun useADifferentAddressGoesBack() = runTest {
        viewModel.events.test {
            viewModel.onAction(VerifyAction.OnChangeEmailClick)
            assertEquals(VerifyEvent.NavigateBack, awaitItem())
        }
    }

    // ----- Opened by the emailed link -----

    @Test
    fun aLinkThatOpenedTheAppIsCheckedAtOnceAndNotShown() = runTest {
        val opened = VerifyViewModel("amara@example.com", repo, linkToken = "fromLink")
        opened.events.test {
            assertEquals(VerifyEvent.NavigateToLogin("amara@example.com"), awaitItem())
        }
        assertEquals(listOf<EmailProof>(EmailProof.Link("fromLink")), repo.verifyCalls)
        assertEquals("", opened.state.value.code.value)
    }

    @Test
    fun withNoAddressATypedCodeAsksForTheLinkAndSendsNothing() = runTest {
        repo.verifyResult = Result.Failure(apiError(422, ApiErrorCode.VERIFICATION_TOKEN_INVALID))
        val opened = VerifyViewModel("", repo, linkToken = "spent")
        assertEquals(UiText.Resource(Res.string.code_error_link_invalid), opened.state.value.code.error)

        opened.onAction(VerifyAction.OnCodeChange("123456"))
        assertEquals(UiText.Resource(Res.string.code_error_no_address), opened.state.value.code.error)
        assertEquals(1, repo.verifyCalls.size)
    }

    @Test
    fun anExpiredLinkWithNoAddressOffersSignInAndResendsNothing() = runTest {
        repo.verifyResult = Result.Failure(apiError(410, ApiErrorCode.VERIFICATION_TOKEN_EXPIRED))
        val opened = VerifyViewModel("", repo, linkToken = "old")
        assertEquals(VerifyMode.Expired, opened.state.value.mode)
        assertEquals(false, opened.state.value.addressKnown)

        opened.onAction(VerifyAction.OnResendClick)
        assertTrue(repo.resendCalls.isEmpty())
        opened.events.test {
            opened.onAction(VerifyAction.OnSignInClick)
            assertEquals(VerifyEvent.NavigateToSignIn, awaitItem())
        }
    }
}
