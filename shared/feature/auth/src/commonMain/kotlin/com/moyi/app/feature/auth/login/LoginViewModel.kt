package com.moyi.app.feature.auth.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moyi.app.core.Result
import com.moyi.app.domain.auth.AuthRepository
import com.moyi.app.feature.auth.common.AuthErrorMapper
import com.moyi.app.feature.auth.common.AuthValidation
import com.moyi.app.feature.auth.common.FieldInput
import com.moyi.app.feature.auth.common.SubmitStatus
import com.moyi.app.feature.auth.register.blurred
import com.moyi.app.feature.auth.register.edited
import com.moyi.app.feature.auth.register.validated
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * @param initialEmail prefilled after verifying (empty otherwise).
 * @param sessionEnded true when the app routed here because the server ended the session.
 */
class LoginViewModel(
    initialEmail: String,
    sessionEnded: Boolean,
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(LoginState(email = FieldInput(initialEmail), sessionEnded = sessionEnded))
    val state: StateFlow<LoginState> = _state.asStateFlow()

    private val _events = Channel<LoginEvent>()
    val events = _events.receiveAsFlow()

    fun onAction(action: LoginAction) {
        when (action) {
            is LoginAction.OnEmailChange -> _state.update { it.copy(email = it.email.edited(action.value, AuthValidation::email)) }
            is LoginAction.OnPasswordChange ->
                _state.update { it.copy(password = it.password.edited(action.value, AuthValidation::existingPassword)) }
            is LoginAction.OnFieldBlur -> _state.update {
                when (action.field) {
                    LoginField.Email -> it.copy(email = it.email.blurred(AuthValidation::email))
                    // An empty password on blur is not worth a message; only submit says so.
                    LoginField.Password -> it
                }
            }
            LoginAction.OnSubmit -> submit()
            // Password reset (forgot / link sent / new password) is a later PR;
            // until then this link goes nowhere.
            LoginAction.OnForgotPasswordClick -> Unit
            LoginAction.OnCreateAccountClick -> viewModelScope.launch { _events.send(LoginEvent.NavigateToRegister) }
        }
    }

    private fun submit() {
        val current = _state.value
        if (current.submit == SubmitStatus.Sending) return
        val checked = current.copy(
            email = current.email.validated(AuthValidation::email),
            password = current.password.validated(AuthValidation::existingPassword),
        )
        if (checked.email.error != null || checked.password.error != null) {
            _state.update { checked }
            return
        }
        _state.update { checked.copy(submit = SubmitStatus.Sending, sessionEnded = false) }
        viewModelScope.launch {
            when (val result = authRepository.login(checked.email.value.trim(), checked.password.value)) {
                is Result.Success -> {
                    _state.update { it.copy(submit = SubmitStatus.Idle) }
                    _events.send(LoginEvent.NavigateToHome)
                }
                is Result.Failure -> _state.update { it.copy(submit = AuthErrorMapper.loginFailure(result.error)) }
            }
        }
    }
}
