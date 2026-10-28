package com.moyi.app.feature.auth.register

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
import com.moyi.app.feature.auth.resources.register_name_error_blank
import com.moyi.app.feature.auth.resources.register_password_error_breached
import com.moyi.app.feature.auth.resources.register_password_error_long
import com.moyi.app.feature.auth.resources.register_password_error_short
import com.moyi.app.feature.auth.violation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RegisterViewModelTest : MainDispatcherTest() {
    private val repo = FakeAuthRepository()
    private val viewModel = RegisterViewModel(repo) { "en-GB" }

    private fun fillValidForm(consent: Boolean = true) {
        viewModel.onAction(RegisterAction.OnNameChange("Amara"))
        viewModel.onAction(RegisterAction.OnEmailChange(" amara@example.com "))
        viewModel.onAction(RegisterAction.OnPasswordChange("correct horse"))
        viewModel.onAction(RegisterAction.OnConsentChange(consent))
    }

    @Test
    fun successSendsTheRequestValuesAndNavigatesToVerify() = runTest {
        fillValidForm()
        viewModel.events.test {
            viewModel.onAction(RegisterAction.OnSubmit)
            assertEquals(RegisterEvent.NavigateToVerify("amara@example.com"), awaitItem())
        }
        assertEquals(
            FakeAuthRepository.RegisterCall(
                displayName = "Amara",
                email = "amara@example.com",
                password = "correct horse",
                locale = "en-GB",
                acceptedTermsVersion = "2026-09-01",
                over18 = true,
            ),
            repo.registerCalls.single(),
        )
    }

    @Test
    fun theActionIsDisabledAndSendsNothingWithoutConsent() = runTest {
        fillValidForm(consent = false)
        assertFalse(viewModel.state.value.canSubmit)
        viewModel.onAction(RegisterAction.OnSubmit)
        assertTrue(repo.registerCalls.isEmpty())

        viewModel.onAction(RegisterAction.OnConsentChange(true))
        assertTrue(viewModel.state.value.canSubmit)
    }

    @Test
    fun showsSendingWhileTheRequestIsInFlight() = runTest {
        fillValidForm()
        val gate = CompletableDeferred<Unit>().also { repo.gate = it }
        viewModel.onAction(RegisterAction.OnSubmit)
        assertEquals(SubmitStatus.Sending, viewModel.state.value.submit)
        assertFalse(viewModel.state.value.canSubmit)

        // A second tap while sending sends nothing more.
        viewModel.onAction(RegisterAction.OnSubmit)
        assertEquals(1, repo.registerCalls.size)

        gate.complete(Unit)
        assertEquals(SubmitStatus.Idle, viewModel.state.value.submit)
    }

    @Test
    fun clientValidationBlocksTheRequestAndShowsFieldErrors() = runTest {
        viewModel.onAction(RegisterAction.OnEmailChange("amara"))
        viewModel.onAction(RegisterAction.OnPasswordChange("short"))
        viewModel.onAction(RegisterAction.OnConsentChange(true))
        viewModel.onAction(RegisterAction.OnSubmit)

        val state = viewModel.state.value
        assertTrue(repo.registerCalls.isEmpty())
        assertEquals(UiText.Resource(Res.string.register_name_error_blank), state.name.error)
        assertEquals(UiText.Resource(Res.string.auth_email_error), state.email.error)
        assertEquals(UiText.Resource(Res.string.register_password_error_short), state.password.error)
    }

    @Test
    fun errorsStayHiddenWhileTypingUntilBlurOrSubmit() = runTest {
        viewModel.onAction(RegisterAction.OnEmailChange("amara"))
        assertNull(viewModel.state.value.email.error)

        viewModel.onAction(RegisterAction.OnFieldBlur(com.moyi.app.feature.auth.common.RegisterField.Email))
        assertEquals(UiText.Resource(Res.string.auth_email_error), viewModel.state.value.email.error)

        // Once showing, fixing the value clears it at once.
        viewModel.onAction(RegisterAction.OnEmailChange("amara@example.com"))
        assertNull(viewModel.state.value.email.error)
    }

    @Test
    fun server422MapsEachViolationToItsField() = runTest {
        repo.registerResult = Result.Failure(
            apiError(422, ApiErrorCode.VALIDATION_FAILED, violation("email", "VALID_EMAIL"), violation("password", "VALID_PASSWORD")),
        )
        fillValidForm()
        viewModel.onAction(RegisterAction.OnSubmit)

        val state = viewModel.state.value
        assertEquals(UiText.Resource(Res.string.auth_email_error), state.email.error)
        // The client already stops anything under 8, so a VALID_PASSWORD for an
        // 8-or-more password can only be the server's upper bound (512 octets, 128 characters).
        assertEquals(UiText.Resource(Res.string.register_password_error_long), state.password.error)
        assertNull(state.name.error)
        assertEquals(SubmitStatus.Idle, state.submit)
    }

    @Test
    fun aPasswordOver128CharactersIsCaughtBeforeTheRequest() = runTest {
        fillValidForm()
        viewModel.onAction(RegisterAction.OnPasswordChange("a".repeat(129)))
        viewModel.onAction(RegisterAction.OnSubmit)

        assertTrue(repo.registerCalls.isEmpty())
        assertEquals(UiText.Resource(Res.string.register_password_error_long), viewModel.state.value.password.error)

        // Exactly 128 is allowed.
        viewModel.onAction(RegisterAction.OnPasswordChange("a".repeat(128)))
        assertNull(viewModel.state.value.password.error)
        viewModel.onAction(RegisterAction.OnSubmit)
        assertEquals(1, repo.registerCalls.size)
    }

    @Test
    fun notBreachedShowsTheCopyMdBreachMessageOnThePassword() = runTest {
        repo.registerResult = Result.Failure(apiError(422, ApiErrorCode.VALIDATION_FAILED, violation("password", "NOT_BREACHED")))
        fillValidForm()
        viewModel.onAction(RegisterAction.OnSubmit)

        assertEquals(UiText.Resource(Res.string.register_password_error_breached), viewModel.state.value.password.error)
        // Editing the password clears the server's verdict.
        viewModel.onAction(RegisterAction.OnPasswordChange("another good one"))
        assertNull(viewModel.state.value.password.error)
    }

    @Test
    fun offlineKeepsEveryInputAndOffersTryAgain() = runTest {
        repo.registerResult = Result.Failure(DataError.Network.NoInternet)
        fillValidForm()
        viewModel.onAction(RegisterAction.OnSubmit)

        val state = viewModel.state.value
        assertEquals(SubmitStatus.Failed(UiText.Resource(Res.string.auth_error_offline), offerRetry = true), state.submit)
        assertEquals("Amara", state.name.value)
        assertEquals(" amara@example.com ", state.email.value)
        assertEquals("correct horse", state.password.value)
        assertTrue(state.consent)
        assertTrue(state.canSubmit, "Try again is Idle, not Disabled (auth-placement-spec D4)")
    }

    @Test
    fun rateLimitedShowsThePausedMessageWithoutTryAgain() = runTest {
        repo.registerResult = Result.Failure(apiError(429, ApiErrorCode.RATE_LIMITED))
        fillValidForm()
        viewModel.onAction(RegisterAction.OnSubmit)
        assertEquals(
            SubmitStatus.Failed(UiText.Resource(Res.string.auth_error_rate_limited), offerRetry = false),
            viewModel.state.value.submit,
        )
    }
}
