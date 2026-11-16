package com.moyi.app.feature.auth.reset

import androidx.compose.runtime.Immutable
import com.moyi.app.feature.auth.common.FieldInput
import com.moyi.app.feature.auth.common.SubmitStatus

/** Forgot password (Account 2, Figma 344:41): one email field, then the code is sent. */
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
     * 202, which the server answers whether or not the address has an account. The code
     * step of Choose a new password next (owner ruling 2026-11-16: no screen in between).
     * [email] travels on to say where the code went, in words that read the same whether or
     * not the address has an account, and to sign in with afterwards.
     */
    data class NavigateToCode(val email: String) : ForgotPasswordEvent

    /** Back to sign in. */
    data object NavigateBack : ForgotPasswordEvent
}
