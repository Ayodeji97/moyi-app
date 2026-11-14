package com.moyi.app.feature.auth.common

import androidx.compose.ui.autofill.AutofillManager
import com.moyi.app.feature.auth.login.LoginEvent
import com.moyi.app.feature.auth.register.RegisterEvent
import com.moyi.app.feature.auth.reset.ResetPasswordEvent

/**
 * What happens to the email and password typed on a screen when it leaves, for the
 * password manager (Android's autofill framework: Google Password Manager or another
 * service).
 *
 * Compose commits on its own once no autofill field is left on screen, so without a
 * decision the typed values are offered for saving whenever the screen goes, accepted by
 * the server or not, and sign in to sign up (both have fields) never commits at all.
 */
enum class CredentialAutofill {
    /** The server accepted them: offer to save (or update) the login now. */
    Save,

    /** Leaving without a login the server accepted: forget what was typed, offer nothing. */
    Discard,
}

/** Signed in: save. Off to sign up or Forgot password: what was typed is not a login. */
val LoginEvent.credentialAutofill: CredentialAutofill
    get() = when (this) {
        LoginEvent.NavigateToHome -> CredentialAutofill.Save
        LoginEvent.NavigateToRegister, is LoginEvent.NavigateToForgotPassword -> CredentialAutofill.Discard
    }

/**
 * The account exists once the server answers 201, so the email and new password are a
 * login from then on, before the email is verified. A legal page opens over the form and
 * comes back to it: nothing is decided then.
 */
val RegisterEvent.credentialAutofill: CredentialAutofill?
    get() = when (this) {
        is RegisterEvent.NavigateToVerify -> CredentialAutofill.Save
        RegisterEvent.NavigateToLogin, RegisterEvent.NavigateBack -> CredentialAutofill.Discard
        is RegisterEvent.OpenLegalPage -> null
    }

/**
 * Discarded even after a successful reset: the screen has a new password but no username
 * field, so a save would store a password with no account beside it. The saved login is
 * updated at the next sign in instead, where the email and password are both on screen.
 */
val ResetPasswordEvent.credentialAutofill: CredentialAutofill
    get() = CredentialAutofill.Discard

/**
 * Null outside Android: iOS has no AutofillManager in Compose Multiplatform 1.12, and its
 * text input there is one native view shared by every field, so iOS never sees the email and
 * password together and offers no "Save password?" sheet. Compose Multiplatform 1.13 adds one
 * native view per field and the save sheet (compose-multiplatform-core#3335, CMP-5802).
 */
fun AutofillManager?.settle(decision: CredentialAutofill?) {
    when (decision) {
        CredentialAutofill.Save -> this?.commit()
        CredentialAutofill.Discard -> this?.cancel()
        null -> Unit
    }
}
