package com.moyi.app.feature.auth.login

import app.cash.turbine.test
import com.moyi.app.core.ApiErrorCode
import com.moyi.app.core.DataError
import com.moyi.app.core.Result
import com.moyi.app.designsystem.presentation.UiText
import com.moyi.app.domain.auth.SessionState
import com.moyi.app.feature.auth.FakeAuthRepository
import com.moyi.app.feature.auth.MainDispatcherTest
import com.moyi.app.feature.auth.apiError
import com.moyi.app.feature.auth.common.SubmitStatus
import com.moyi.app.feature.auth.resources.Res
import com.moyi.app.feature.auth.resources.auth_error_offline
import com.moyi.app.feature.auth.resources.login_error_credentials
import com.moyi.app.feature.auth.resources.login_error_rate_limited
import com.moyi.app.feature.auth.resources.login_password_error_blank
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LoginViewModelTest : MainDispatcherTest() {
    private val repo = FakeAuthRepository()

    private fun viewModel(email: String = "amara@example.com", sessionEnded: Boolean = false) =
        LoginViewModel(email, sessionEnded, repo)

    @Test
    fun theVerifiedEmailIsPrefilled() {
        assertEquals("amara@example.com", viewModel().state.value.email.value)
    }

    @Test
    fun successSignsInAndNavigatesHome() = runTest {
        val vm = viewModel()
        vm.onAction(LoginAction.OnPasswordChange("correct horse"))
        vm.events.test {
            vm.onAction(LoginAction.OnSubmit)
            assertEquals(LoginEvent.NavigateToHome, awaitItem())
        }
        assertEquals(listOf("amara@example.com" to "correct horse"), repo.loginCalls)
        assertIs<SessionState.SignedIn>(repo.session.value)
    }

    @Test
    fun signingInWhileInFlight() = runTest {
        val vm = viewModel()
        val gate = CompletableDeferred<Unit>().also { repo.gate = it }
        vm.onAction(LoginAction.OnPasswordChange("correct horse"))
        vm.onAction(LoginAction.OnSubmit)
        assertEquals(SubmitStatus.Sending, vm.state.value.submit)
        gate.complete(Unit)
    }

    @Test
    fun every401IsTheSameSentence() = runTest {
        val credentials = SubmitStatus.Failed(UiText.Resource(Res.string.login_error_credentials), offerRetry = false)
        for (code in listOf(ApiErrorCode.INVALID_CREDENTIALS, ApiErrorCode.UNAUTHENTICATED, ApiErrorCode.Unknown)) {
            repo.loginResult = Result.Failure(apiError(401, code))
            val vm = viewModel()
            vm.onAction(LoginAction.OnPasswordChange("wrong one"))
            vm.onAction(LoginAction.OnSubmit)
            assertEquals(credentials, vm.state.value.submit, "for $code")
            assertEquals("wrong one", vm.state.value.password.value)
        }
    }

    @Test
    fun rateLimitedShowsThePausedLine() = runTest {
        repo.loginResult = Result.Failure(apiError(429, ApiErrorCode.RATE_LIMITED))
        val vm = viewModel()
        vm.onAction(LoginAction.OnPasswordChange("correct horse"))
        vm.onAction(LoginAction.OnSubmit)
        assertEquals(
            SubmitStatus.Failed(UiText.Resource(Res.string.login_error_rate_limited), offerRetry = false),
            vm.state.value.submit,
        )
    }

    @Test
    fun offlineKeepsInputAndOffersTryAgain() = runTest {
        repo.loginResult = Result.Failure(DataError.Network.Timeout)
        val vm = viewModel()
        vm.onAction(LoginAction.OnPasswordChange("correct horse"))
        vm.onAction(LoginAction.OnSubmit)
        assertEquals(SubmitStatus.Failed(UiText.Resource(Res.string.auth_error_offline), offerRetry = true), vm.state.value.submit)
        assertEquals("correct horse", vm.state.value.password.value)
    }

    @Test
    fun anEmptyPasswordIsCaughtBeforeTheRequest() = runTest {
        val vm = viewModel()
        vm.onAction(LoginAction.OnSubmit)
        assertTrue(repo.loginCalls.isEmpty())
        assertEquals(UiText.Resource(Res.string.login_password_error_blank), vm.state.value.password.error)
    }

    @Test
    fun theSessionEndedNoticeShowsUntilTheNextAttempt() = runTest {
        val vm = viewModel(sessionEnded = true)
        assertTrue(vm.state.value.sessionEnded)
        vm.onAction(LoginAction.OnPasswordChange("correct horse"))
        vm.onAction(LoginAction.OnSubmit)
        assertFalse(vm.state.value.sessionEnded)
    }
}
