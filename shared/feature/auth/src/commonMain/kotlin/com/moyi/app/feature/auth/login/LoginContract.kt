package com.moyi.app.feature.auth.login

import androidx.compose.runtime.Immutable
import com.moyi.app.feature.auth.common.FieldInput
import com.moyi.app.feature.auth.common.SubmitStatus

/** Sign in (Account 1, Figma 344:18). */
@Immutable
data class LoginState(
    val email: FieldInput = FieldInput(),
    val password: FieldInput = FieldInput(),
    val submit: SubmitStatus = SubmitStatus.Idle,
    /** Arrived here because the server ended the session (SessionEvent.Ended). Copy owed; see strings.xml. */
    val sessionEnded: Boolean = false,
)

enum class LoginField { Email, Password }

sealed interface LoginAction {
    data class OnEmailChange(val value: String) : LoginAction
    data class OnPasswordChange(val value: String) : LoginAction
    data class OnFieldBlur(val field: LoginField) : LoginAction
    data object OnSubmit : LoginAction

    /** No destination yet: password reset is a later PR. */
    data object OnForgotPasswordClick : LoginAction
    data object OnCreateAccountClick : LoginAction
}

sealed interface LoginEvent {
    /** Signed in: the session is SignedIn and stored. */
    data object NavigateToHome : LoginEvent
    data object NavigateToRegister : LoginEvent
}
