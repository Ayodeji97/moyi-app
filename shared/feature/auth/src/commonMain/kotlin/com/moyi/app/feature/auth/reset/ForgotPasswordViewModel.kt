package com.moyi.app.feature.auth.reset

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

/** @param initialEmail whatever was in sign in's email field, so it need not be typed twice. */
class ForgotPasswordViewModel(
    initialEmail: String,
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ForgotPasswordState(email = FieldInput(initialEmail)))
    val state: StateFlow<ForgotPasswordState> = _state.asStateFlow()

    private val _events = Channel<ForgotPasswordEvent>()
    val events = _events.receiveAsFlow()

    fun onAction(action: ForgotPasswordAction) {
        when (action) {
            is ForgotPasswordAction.OnEmailChange ->
                _state.update { it.copy(email = it.email.edited(action.value, AuthValidation::email)) }
            ForgotPasswordAction.OnEmailBlur -> _state.update { it.copy(email = it.email.blurred(AuthValidation::email)) }
            ForgotPasswordAction.OnSubmit -> submit()
            ForgotPasswordAction.OnBackClick -> viewModelScope.launch { _events.send(ForgotPasswordEvent.NavigateBack) }
        }
    }

    /**
     * A success is a 202 whatever the address, so it moves on without saying
     * anything about the account (states.md §1b). A failure (offline, 429)
     * stays here with the address kept, the reason above the action.
     */
    private fun submit() {
        val current = _state.value
        if (current.submit == SubmitStatus.Sending) return
        val checked = current.copy(email = current.email.validated(AuthValidation::email))
        if (checked.email.error != null) {
            _state.update { checked }
            return
        }
        _state.update { checked.copy(submit = SubmitStatus.Sending) }
        val email = checked.email.value.trim()
        viewModelScope.launch {
            when (val result = authRepository.requestPasswordReset(email)) {
                is Result.Success -> {
                    _state.update { it.copy(submit = SubmitStatus.Idle) }
                    _events.send(ForgotPasswordEvent.NavigateToLinkSent(email))
                }
                is Result.Failure -> _state.update { it.copy(submit = AuthErrorMapper.formFailure(result.error)) }
            }
        }
    }
}
