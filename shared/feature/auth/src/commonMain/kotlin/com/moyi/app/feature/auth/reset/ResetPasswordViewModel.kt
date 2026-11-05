package com.moyi.app.feature.auth.reset

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moyi.app.core.DataError
import com.moyi.app.core.Result
import com.moyi.app.designsystem.presentation.UiText
import com.moyi.app.domain.auth.AuthRepository
import com.moyi.app.feature.auth.common.AuthErrorMapper
import com.moyi.app.feature.auth.common.AuthValidation
import com.moyi.app.feature.auth.common.SubmitStatus
import com.moyi.app.feature.auth.common.extractVerificationToken
import com.moyi.app.feature.auth.register.blurred
import com.moyi.app.feature.auth.register.edited
import com.moyi.app.feature.auth.register.validated
import com.moyi.app.feature.auth.register.withServerError
import com.moyi.app.feature.auth.resources.Res
import com.moyi.app.feature.auth.resources.verify_token_error_blank
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ResetPasswordViewModel(
    email: String,
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ResetPasswordState(email = email))
    val state: StateFlow<ResetPasswordState> = _state.asStateFlow()

    private val _events = Channel<ResetPasswordEvent>()
    val events = _events.receiveAsFlow()

    fun onAction(action: ResetPasswordAction) {
        when (action) {
            is ResetPasswordAction.OnTokenChange -> _state.update {
                // A new paste clears the old verdict; there is nothing to re-check client-side.
                it.copy(token = it.token.copy(value = action.value, error = null))
            }
            is ResetPasswordAction.OnPasswordChange ->
                _state.update { it.copy(password = it.password.edited(action.value, AuthValidation::newPassword)) }
            ResetPasswordAction.OnPasswordBlur ->
                _state.update { it.copy(password = it.password.blurred(AuthValidation::newPassword)) }
            ResetPasswordAction.OnSubmit -> submit()
            ResetPasswordAction.OnBackClick -> viewModelScope.launch { _events.send(ResetPasswordEvent.NavigateBack) }
        }
    }

    /**
     * Reset, then sign in with the new password: the reset answers 200 with no
     * session and revokes every session there was (doc 06 §3.1), so "Save and
     * sign in" is two requests behind one Sending label.
     *
     * Nothing typed is cleared on a failure. A refused password does not spend
     * the token (doc 06 §3.1), so the same code can be sent again with a
     * different password.
     */
    private fun submit() {
        val current = _state.value
        if (current.submit == SubmitStatus.Sending) return
        val token = extractVerificationToken(current.token.value)
        val checked = current.copy(
            token = if (token.isEmpty()) {
                current.token.copy(showErrors = true, error = UiText.Resource(Res.string.verify_token_error_blank))
            } else {
                current.token
            },
            password = current.password.validated(AuthValidation::newPassword),
        )
        if (checked.token.error != null || checked.password.error != null) {
            _state.update { checked }
            return
        }
        _state.update { checked.copy(submit = SubmitStatus.Sending) }
        val password = checked.password.value
        viewModelScope.launch {
            when (val reset = authRepository.resetPassword(token, password)) {
                is Result.Success -> signIn(current.email, password)
                is Result.Failure -> onResetRefused(reset.error, password)
            }
        }
    }

    private suspend fun signIn(email: String, password: String) {
        when (authRepository.login(email, password)) {
            is Result.Success -> {
                _state.update { it.copy(submit = SubmitStatus.Idle) }
                _events.send(ResetPasswordEvent.NavigateToHome)
            }
            // The password is saved either way; sign in says what is wrong, with a fresh form.
            is Result.Failure -> {
                _state.update { it.copy(submit = SubmitStatus.Idle) }
                _events.send(ResetPasswordEvent.NavigateToLogin(email))
            }
        }
    }

    private fun onResetRefused(error: DataError, submittedPassword: String) {
        val tokenError = AuthErrorMapper.resetTokenError(error)
        val passwordError = AuthErrorMapper.resetPasswordError(error, submittedPassword)
        _state.update {
            if (tokenError == null && passwordError == null) {
                it.copy(submit = AuthErrorMapper.formFailure(error))
            } else {
                it.copy(
                    token = if (tokenError == null) it.token else it.token.copy(showErrors = true, error = tokenError),
                    password = it.password.withServerError(passwordError),
                    submit = SubmitStatus.Idle,
                )
            }
        }
    }
}
