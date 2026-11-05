package com.moyi.app.feature.auth.reset

import androidx.compose.runtime.Immutable
import com.moyi.app.feature.auth.common.FieldInput
import com.moyi.app.feature.auth.common.SubmitStatus

/**
 * Choose a new password (Account 4, Figma 345:30), plus the paste-code field
 * (not in Figma): a development stand-in until deep links carry the token.
 */
@Immutable
data class ResetPasswordState(
    /** The address from Forgot password, used to sign in once the password is saved. */
    val email: String,
    val token: FieldInput = FieldInput(),
    val password: FieldInput = FieldInput(),
    /** Sending covers both the reset and the sign-in after it: one action, "Save and sign in". */
    val submit: SubmitStatus = SubmitStatus.Idle,
)

sealed interface ResetPasswordAction {
    data class OnTokenChange(val value: String) : ResetPasswordAction
    data class OnPasswordChange(val value: String) : ResetPasswordAction

    /** Left the password field. The paste field has no blur check: like verify, empty is only an error on submit. */
    data object OnPasswordBlur : ResetPasswordAction
    data object OnSubmit : ResetPasswordAction
    data object OnBackClick : ResetPasswordAction
}

sealed interface ResetPasswordEvent {
    /** Saved and signed in: the session is SignedIn and stored. */
    data object NavigateToHome : ResetPasswordEvent

    /**
     * Saved, but the sign-in after it failed. The new password is set, so
     * sign in next with the address filled in, and no way back into the reset.
     */
    data class NavigateToLogin(val email: String) : ResetPasswordEvent

    /** Back to Reset link sent. */
    data object NavigateBack : ResetPasswordEvent
}
