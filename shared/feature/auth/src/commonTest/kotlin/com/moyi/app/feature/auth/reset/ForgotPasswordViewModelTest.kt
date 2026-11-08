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
import com.moyi.app.feature.auth.resources.auth_email_error
import com.moyi.app.feature.auth.resources.auth_error_offline
import com.moyi.app.feature.auth.resources.auth_error_rate_limited
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ForgotPasswordViewModelTest : MainDispatcherTest() {
    private val repo = FakeAuthRepository()

    @Test
    fun theFieldStartsWithSignInsEmail() {
        val viewModel = ForgotPasswordViewModel("amara@example.com", repo)
        assertEquals("amara@example.com", viewModel.state.value.email.value)
        assertEquals(null, viewModel.state.value.email.error)
    }

    @Test
    fun anAddressWithoutAnAtIsAFieldErrorAndSendsNothing() = runTest {
        val viewModel = ForgotPasswordViewModel("amara", repo)
        viewModel.onAction(ForgotPasswordAction.OnSubmit)
        assertTrue(repo.requestResetCalls.isEmpty())
        assertEquals(UiText.Resource(Res.string.auth_email_error), viewModel.state.value.email.error)
        assertEquals(SubmitStatus.Idle, viewModel.state.value.submit)
    }

    @Test
    fun anEmptyFieldIsAFieldErrorOnSubmit() = runTest {
        val viewModel = ForgotPasswordViewModel("", repo)
        viewModel.onAction(ForgotPasswordAction.OnSubmit)
        assertTrue(repo.requestResetCalls.isEmpty())
        assertEquals(UiText.Resource(Res.string.auth_email_error), viewModel.state.value.email.error)
    }

    @Test
    fun acceptedGoesToLinkSentWithTheTrimmedEmail() = runTest {
        val viewModel = ForgotPasswordViewModel("", repo)
        viewModel.onAction(ForgotPasswordAction.OnEmailChange(" amara@example.com\n"))
        viewModel.events.test {
            viewModel.onAction(ForgotPasswordAction.OnSubmit)
            assertEquals(ForgotPasswordEvent.NavigateToLinkSent("amara@example.com"), awaitItem())
        }
        assertEquals(listOf("amara@example.com"), repo.requestResetCalls)
        assertEquals(SubmitStatus.Idle, viewModel.state.value.submit)
    }

    @Test
    fun offlineKeepsTheEmailAndOffersARetryAboveTheAction() = runTest {
        repo.requestResetResult = Result.Failure(DataError.Network.NoInternet)
        val viewModel = ForgotPasswordViewModel("amara@example.com", repo)
        viewModel.events.test {
            viewModel.onAction(ForgotPasswordAction.OnSubmit)
            expectNoEvents()
        }
        val state = viewModel.state.value
        assertEquals("amara@example.com", state.email.value)
        assertEquals(null, state.email.error)
        assertEquals(SubmitStatus.Failed(UiText.Resource(Res.string.auth_error_offline), offerRetry = true), state.submit)
    }

    @Test
    fun rateLimitedSaysWhenAndOffersNoRetry() = runTest {
        repo.requestResetResult = Result.Failure(apiError(429, ApiErrorCode.RATE_LIMITED))
        val viewModel = ForgotPasswordViewModel("amara@example.com", repo)
        viewModel.onAction(ForgotPasswordAction.OnSubmit)
        assertEquals(
            SubmitStatus.Failed(UiText.Resource(Res.string.auth_error_rate_limited), offerRetry = false),
            viewModel.state.value.submit,
        )
        assertEquals("amara@example.com", viewModel.state.value.email.value)
    }

    @Test
    fun aSecondTapWhileSendingSendsNothingMore() = runTest {
        val gate = CompletableDeferred<Unit>().also { repo.gate = it }
        val viewModel = ForgotPasswordViewModel("amara@example.com", repo)
        viewModel.onAction(ForgotPasswordAction.OnSubmit)
        assertEquals(SubmitStatus.Sending, viewModel.state.value.submit)

        viewModel.onAction(ForgotPasswordAction.OnSubmit)
        assertEquals(1, repo.requestResetCalls.size)

        gate.complete(Unit)
        assertEquals(SubmitStatus.Idle, viewModel.state.value.submit)
    }

    @Test
    fun backGoesBack() = runTest {
        val viewModel = ForgotPasswordViewModel("", repo)
        viewModel.events.test {
            viewModel.onAction(ForgotPasswordAction.OnBackClick)
            assertEquals(ForgotPasswordEvent.NavigateBack, awaitItem())
        }
    }
}
