package com.moyi.app.feature.auth.reset

import androidx.compose.runtime.Immutable
import com.moyi.app.feature.auth.common.FieldInput
import com.moyi.app.feature.auth.common.SubmitStatus

/** Forgot password (Account 2, Figma 344:41): one email field, then the link is sent. */
@Immutable
data class ForgotPasswordState(
    val email: FieldInput = FieldInput(),
    val submit: SubmitStatus = SubmitStatus.Idle,
)

sealed interface ForgotPasswordAction {
    data class OnEmailChange(val value: String) : ForgotPasswordAction
    data object OnEmailBlur : ForgotPasswordAction
    data object OnSubmit : ForgotPasswordAction
    data object OnBackClick : ForgotPasswordAction
}

sealed interface ForgotPasswordEvent {
    /**
     * 202, which the server answers whether or not the address has an
     * account. Reset link sent next; [email] travels on only so that "Choose
     * a new password" can sign in with it afterwards. It is never shown.
     */
    data class NavigateToLinkSent(val email: String) : ForgotPasswordEvent

    /** Back to sign in. */
    data object NavigateBack : ForgotPasswordEvent
}
