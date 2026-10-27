package com.moyi.app.feature.auth.verify

import app.cash.turbine.test
import com.moyi.app.core.ApiErrorCode
import com.moyi.app.core.Result
import com.moyi.app.designsystem.presentation.UiText
import com.moyi.app.feature.auth.FakeAuthRepository
import com.moyi.app.feature.auth.MainDispatcherTest
import com.moyi.app.feature.auth.apiError
import com.moyi.app.feature.auth.common.SubmitStatus
import com.moyi.app.feature.auth.resources.Res
import com.moyi.app.feature.auth.resources.verify_token_error_blank
import com.moyi.app.feature.auth.resources.verify_token_error_invalid
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VerifyViewModelTest : MainDispatcherTest() {
    private val repo = FakeAuthRepository()
    private val viewModel = VerifyViewModel("amara@example.com", repo)

    @Test
    fun aPastedLinkSendsOnlyItsTokenAndSuccessGoesToLoginWithTheEmail() = runTest {
        viewModel.onAction(VerifyAction.OnTokenChange("https://getmoyi.app/verify?token=abc123&utm=x\n"))
        viewModel.events.test {
            viewModel.onAction(VerifyAction.OnSubmit)
            assertEquals(VerifyEvent.NavigateToLogin("amara@example.com"), awaitItem())
        }
        assertEquals(listOf("abc123"), repo.verifyCalls)
    }

    @Test
    fun checkingWhileInFlight() = runTest {
        val gate = CompletableDeferred<Unit>().also { repo.gate = it }
        viewModel.onAction(VerifyAction.OnTokenChange("abc"))
        viewModel.onAction(VerifyAction.OnSubmit)
        assertEquals(SubmitStatus.Sending, viewModel.state.value.submit)

        // A second tap while checking sends nothing more.
        viewModel.onAction(VerifyAction.OnSubmit)
        assertEquals(1, repo.verifyCalls.size)

        gate.complete(Unit)
        assertEquals(SubmitStatus.Idle, viewModel.state.value.submit)
    }

    @Test
    fun anEmptyPasteIsAFieldErrorAndSendsNothing() = runTest {
        viewModel.onAction(VerifyAction.OnSubmit)
        assertTrue(repo.verifyCalls.isEmpty())
        assertEquals(UiText.Resource(Res.string.verify_token_error_blank), viewModel.state.value.token.error)
    }

    @Test
    fun invalidTokenIsAFieldError() = runTest {
        repo.verifyResult = Result.Failure(apiError(422, ApiErrorCode.VERIFICATION_TOKEN_INVALID))
        viewModel.onAction(VerifyAction.OnTokenChange("wrong"))
        viewModel.onAction(VerifyAction.OnSubmit)
        val state = viewModel.state.value
        assertEquals(UiText.Resource(Res.string.verify_token_error_invalid), state.token.error)
        assertEquals(VerifyMode.Waiting, state.mode)
        assertEquals(SubmitStatus.Idle, state.submit)
    }

    @Test
    fun expiredTokenShowsTheExpiredStateAndSendANewLinkReturnsToWaiting() = runTest {
        repo.verifyResult = Result.Failure(apiError(410, ApiErrorCode.VERIFICATION_TOKEN_EXPIRED))
        viewModel.onAction(VerifyAction.OnTokenChange("old"))
        viewModel.onAction(VerifyAction.OnSubmit)
        assertEquals(VerifyMode.Expired, viewModel.state.value.mode)

        viewModel.onAction(VerifyAction.OnResendClick)
        val state = viewModel.state.value
        assertEquals(listOf("amara@example.com"), repo.resendCalls)
        assertEquals(VerifyMode.Waiting, state.mode)
        assertEquals("", state.token.value)
        assertEquals(ResendStatus.Sent, state.resend)
    }

    @Test
    fun resendShowsTheAcknowledgementAndIgnoresASecondTap() = runTest {
        viewModel.onAction(VerifyAction.OnResendClick)
        assertEquals(ResendStatus.Sent, viewModel.state.value.resend)
        viewModel.onAction(VerifyAction.OnResendClick)
        assertEquals(1, repo.resendCalls.size)
    }

    @Test
    fun useADifferentAddressGoesBack() = runTest {
        viewModel.events.test {
            viewModel.onAction(VerifyAction.OnChangeEmailClick)
            assertEquals(VerifyEvent.NavigateBack, awaitItem())
        }
    }
}
