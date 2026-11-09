package com.moyi.app.feature.auth.reset

import androidx.compose.runtime.Immutable
import com.moyi.app.feature.auth.common.FieldInput
import com.moyi.app.feature.auth.common.SubmitStatus

/**
 * Choose a new password (Account 4, Figma 345:30), plus the code field (not in Figma,
 * docs/missing-components.md §58): six digits typed from the email, or the link pasted.
 * When the emailed link opened the app, [linkToken] holds it and nothing needs typing.
 */
@Immutable
data class ResetPasswordState(
    /**
     * The address from Forgot password, used to sign in once the password is saved. Empty
     * when the emailed link opened the app on a phone that does not know it: the password is
     * saved, then sign in asks for the address.
     */
    val email: String,
    /** Only ever zero to six ASCII digits (parseCodeInput). */
    val code: FieldInput = FieldInput(),
    /**
     * The reset link's token, when the link opened the app or was pasted into the code
     * field. It is the proof while it is held; typing a digit drops it for the code.
     */
    val linkToken: String? = null,
    val password: FieldInput = FieldInput(),
    /** Sending covers both the reset and the sign-in after it: one action, "Save and sign in". */
    val submit: SubmitStatus = SubmitStatus.Idle,
)

sealed interface ResetPasswordAction {
    /** A keystroke or a paste in the code field: digits, or a whole link. */
    data class OnCodeChange(val value: String) : ResetPasswordAction
    data class OnPasswordChange(val value: String) : ResetPasswordAction

    /** Left the password field. The code field has no blur check: like verify, empty is only an error on submit. */
    data object OnPasswordBlur : ResetPasswordAction
    data object OnSubmit : ResetPasswordAction
    data object OnBackClick : ResetPasswordAction
}

sealed interface ResetPasswordEvent {
    /** Saved and signed in: the session is SignedIn and stored. */
    data object NavigateToHome : ResetPasswordEvent

    /**
     * Saved, but not signed in: the sign-in after it failed, or there was no address to sign
     * in with. The new password is set, so sign in next with the address filled in (when
     * known), and no way back into the reset.
     */
    data class NavigateToLogin(val email: String) : ResetPasswordEvent

    /** Back to Reset link sent. */
    data object NavigateBack : ResetPasswordEvent
}
