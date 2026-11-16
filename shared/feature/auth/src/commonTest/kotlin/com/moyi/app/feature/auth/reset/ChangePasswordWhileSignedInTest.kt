package com.moyi.app.feature.auth.reset

import app.cash.turbine.test
import com.moyi.app.core.ApiErrorCode
import com.moyi.app.core.DataError
import com.moyi.app.core.Result
import com.moyi.app.designsystem.presentation.UiText
import com.moyi.app.domain.auth.EmailProof
import com.moyi.app.domain.auth.PasswordChangeError
import com.moyi.app.domain.auth.SessionEndReason
import com.moyi.app.domain.auth.SessionEvent
import com.moyi.app.domain.auth.SessionState
import com.moyi.app.feature.auth.FakeAuthRepository
import com.moyi.app.feature.auth.MainDispatcherTest
import com.moyi.app.feature.auth.apiError
import com.moyi.app.feature.auth.common.SubmitStatus
import com.moyi.app.feature.auth.resources.Res
import com.moyi.app.feature.auth.resources.auth_error_offline
import com.moyi.app.feature.auth.resources.code_error_invalid
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Account › Change password: [ChangePasswordViewModel] sends the code to the signed-in
 * address, and [ResetPasswordViewModel] with `signedIn` saves through
 * [com.moyi.app.domain.auth.AuthRepository.changePassword], which keeps this phone signed in.
 */
class ChangePasswordWhileSignedInTest : MainDispatcherTest() {
    private val repo = FakeAuthRepository().apply { signIn() }
    private val email = FakeAuthRepository.USER.email

    // ----- Change password: the code goes to the signed-in address -----

    @Test
    fun theAddressIsTheSessionsAndNothingIsSentUntilAsked() = runTest {
        val viewModel = ChangePasswordViewModel(repo)
        assertEquals(email, viewModel.state.value.email)
        assertTrue(repo.requestResetCalls.isEmpty())
    }

    @Test
    fun sendTheCodeAsksForItForTheSignedInAddressAndOpensTheCodeStep() = runTest {
        val viewModel = ChangePasswordViewModel(repo)
        viewModel.events.test {
            viewModel.onAction(ChangePasswordAction.OnSubmit)
            assertEquals(ChangePasswordEvent.NavigateToCode(email), awaitItem())
        }
        assertEquals(listOf(email), repo.requestResetCalls)
        assertEquals(SubmitStatus.Idle, viewModel.state.value.submit)
    }

    @Test
    fun aSecondTapWhileSendingSendsNothingMore() = runTest {
        val viewModel = ChangePasswordViewModel(repo)
        val gate = CompletableDeferred<Unit>().also { repo.gate = it }
        viewModel.onAction(ChangePasswordAction.OnSubmit)
        assertEquals(SubmitStatus.Sending, viewModel.state.value.submit)
        viewModel.onAction(ChangePasswordAction.OnSubmit)
        gate.complete(Unit)
        assertEquals(1, repo.requestResetCalls.size)
    }

    @Test
    fun offlineStaysHereWithTheReasonAndTryAgain() = runTest {
        repo.requestResetResult = Result.Failure(DataError.Network.NoInternet)
        val viewModel = ChangePasswordViewModel(repo)
        viewModel.onAction(ChangePasswordAction.OnSubmit)
        val failed = assertIs<SubmitStatus.Failed>(viewModel.state.value.submit)
        assertEquals(UiText.Resource(Res.string.auth_error_offline), failed.message)
        assertTrue(failed.offerRetry)
    }

    @Test
    fun withNoSessionNothingIsSent() = runTest {
        val signedOut = FakeAuthRepository()
        val viewModel = ChangePasswordViewModel(signedOut)
        assertNull(viewModel.state.value.email)
        viewModel.onAction(ChangePasswordAction.OnSubmit)
        assertTrue(signedOut.requestResetCalls.isEmpty())
    }

    @Test
    fun theAddressNeverReachesToString() = runTest {
        val viewModel = ChangePasswordViewModel(repo)
        assertTrue(email !in viewModel.state.value.toString())
    }

    // ----- The reset, signed in -----

    private val reset = ResetPasswordViewModel(email, repo, signedIn = true)

    private fun bothSteps(code: String = "123456", password: String = "correct horse") {
        reset.onAction(ResetPasswordAction.OnCodeChange(code))
        reset.onAction(ResetPasswordAction.OnPasswordChange(password))
    }

    @Test
    fun theStateSaysSignedInSoTheScreenSaysThisPhoneStays() = runTest {
        assertTrue(reset.state.value.signedIn)
        assertEquals(ResetStep.Code, reset.state.value.step)
    }

    @Test
    fun savingChangesThePasswordKeepingThisPhoneSignedIn() = runTest {
        bothSteps()
        reset.events.test {
            reset.onAction(ResetPasswordAction.OnSubmit)
            assertEquals(ResetPasswordEvent.PasswordChanged, awaitItem())
        }
        assertEquals(
            listOf(FakeAuthRepository.ChangeCall(EmailProof.Code(email, "123456"), email, "correct horse")),
            repo.changeCalls,
        )
        // Not the signed-out path: no bare reset, no sign-in from scratch.
        assertTrue(repo.resetCalls.isEmpty())
        assertTrue(repo.loginCalls.isEmpty())
        assertIs<SessionState.SignedIn>(repo.session.value)
        assertEquals(SubmitStatus.Idle, reset.state.value.submit)
    }

    @Test
    fun theSaveSaysSavingWhileItRuns() = runTest {
        bothSteps()
        val gate = CompletableDeferred<Unit>().also { repo.gate = it }
        reset.onAction(ResetPasswordAction.OnSubmit)
        assertEquals(SubmitStatus.Sending, reset.state.value.submit)
        reset.onAction(ResetPasswordAction.OnSubmit)
        gate.complete(Unit)
        assertEquals(1, repo.changeCalls.size)
    }

    @Test
    fun aCodeRefusedAtTheSaveReturnsToTheCodeAndThePhoneIsStillSignedIn() = runTest {
        bothSteps()
        repo.changeResult = Result.Failure(
            PasswordChangeError.NotChanged(apiError(422, ApiErrorCode.PASSWORD_RESET_TOKEN_INVALID)),
        )
        reset.onAction(ResetPasswordAction.OnSubmit)
        val state = reset.state.value
        assertEquals(ResetStep.Code, state.step)
        assertEquals(UiText.Resource(Res.string.code_error_invalid), state.code.error)
        assertEquals(SubmitStatus.Idle, state.submit)
        assertIs<SessionState.SignedIn>(repo.session.value)
    }

    @Test
    fun offlineAtTheSaveKeepsEverythingTyped() = runTest {
        bothSteps()
        repo.changeResult = Result.Failure(PasswordChangeError.NotChanged(DataError.Network.NoInternet))
        reset.onAction(ResetPasswordAction.OnSubmit)
        val state = reset.state.value
        assertEquals(ResetStep.Password, state.step)
        assertEquals("correct horse", state.password.value)
        assertIs<SubmitStatus.Failed>(state.submit)
    }

    @Test
    fun savedButNotSignedInAgainLeavesTheRoutingToTheSessionEnd() = runTest {
        bothSteps()
        repo.changeResult = Result.Failure(PasswordChangeError.SignedOut(DataError.Network.NoInternet))
        repo.sessionEvents.test {
            reset.events.test {
                reset.onAction(ResetPasswordAction.OnSubmit)
                // The screen sends nothing: the shell routes on the session ending.
                expectNoEvents()
            }
            assertEquals(SessionEvent.Ended(SessionEndReason.PasswordChanged), awaitItem())
        }
        assertEquals(SubmitStatus.Idle, reset.state.value.submit)
        assertEquals(SessionState.SignedOut, repo.session.value)
    }
}
