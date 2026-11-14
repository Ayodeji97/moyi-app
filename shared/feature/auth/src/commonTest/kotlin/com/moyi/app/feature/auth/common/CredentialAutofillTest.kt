package com.moyi.app.feature.auth.common

import app.cash.turbine.test
import com.moyi.app.core.ApiErrorCode
import com.moyi.app.core.Result
import com.moyi.app.feature.auth.FakeAuthRepository
import com.moyi.app.feature.auth.MainDispatcherTest
import com.moyi.app.feature.auth.apiError
import com.moyi.app.feature.auth.login.LoginAction
import com.moyi.app.feature.auth.login.LoginEvent
import com.moyi.app.feature.auth.login.LoginViewModel
import com.moyi.app.feature.auth.register.RegisterEvent
import com.moyi.app.feature.auth.reset.ResetPasswordEvent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The password manager is offered a login only once the server has accepted it, and
 * everything typed is forgotten when a screen is left any other way.
 */
class CredentialAutofillTest : MainDispatcherTest() {
    private val repo = FakeAuthRepository()

    @Test
    fun aSuccessfulSignInIsSaved() = runTest {
        val vm = LoginViewModel("amara@example.com", sessionEnded = false, authRepository = repo)
        vm.onAction(LoginAction.OnPasswordChange("correct horse"))
        vm.events.test {
            vm.onAction(LoginAction.OnSubmit)
            assertEquals(CredentialAutofill.Save, awaitItem().credentialAutofill)
        }
    }

    @Test
    fun aRefusedSignInIsNeverSaved() = runTest {
        repo.loginResult = Result.Failure(apiError(401, ApiErrorCode.INVALID_CREDENTIALS))
        val vm = LoginViewModel("amara@example.com", sessionEnded = false, authRepository = repo)
        vm.onAction(LoginAction.OnPasswordChange("wrong one"))
        vm.events.test {
            vm.onAction(LoginAction.OnSubmit)
            // No event, so nothing is settled: the person stays on the form to try again.
            expectNoEvents()
            // Leaving for Forgot password then forgets the wrong password.
            vm.onAction(LoginAction.OnForgotPasswordClick)
            assertEquals(CredentialAutofill.Discard, awaitItem().credentialAutofill)
        }
    }

    @Test
    fun leavingSignInWithoutSigningInDiscards() {
        assertEquals(CredentialAutofill.Discard, LoginEvent.NavigateToRegister.credentialAutofill)
        assertEquals(CredentialAutofill.Discard, LoginEvent.NavigateToForgotPassword("").credentialAutofill)
    }

    @Test
    fun aCreatedAccountIsSavedAndLeavingSignUpOtherwiseDiscards() {
        assertEquals(CredentialAutofill.Save, RegisterEvent.NavigateToVerify("amara@example.com").credentialAutofill)
        assertEquals(CredentialAutofill.Discard, RegisterEvent.NavigateToLogin.credentialAutofill)
        assertEquals(CredentialAutofill.Discard, RegisterEvent.NavigateBack.credentialAutofill)
    }

    @Test
    fun aLegalPageOverSignUpKeepsTheForm() {
        assertNull(RegisterEvent.OpenLegalPage("https://getmoyi.app/terms").credentialAutofill)
    }

    @Test
    fun aResetNeverSavesAPasswordWithoutItsEmail() {
        assertEquals(CredentialAutofill.Discard, ResetPasswordEvent.NavigateToHome.credentialAutofill)
        assertEquals(CredentialAutofill.Discard, ResetPasswordEvent.NavigateToLogin("amara@example.com").credentialAutofill)
        assertEquals(CredentialAutofill.Discard, ResetPasswordEvent.NavigateBack.credentialAutofill)
    }
}
