package com.moyi.app.feature.auth.reset

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moyi.app.core.Result
import com.moyi.app.domain.auth.AuthRepository
import com.moyi.app.domain.auth.SessionState
import com.moyi.app.feature.auth.common.AuthErrorMapper
import com.moyi.app.feature.auth.common.SubmitStatus
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Change password, step zero: sends the reset code to the signed-in address
 * (`POST /auth/forgot-password` with the session's email), then hands over to the reset's
 * code step ([ResetPasswordViewModel] with `signedIn`). The address is never typed: it is the
 * one this device is signed in with, so the code can only reach the account's own inbox.
 *
 * A `202` moves on; a failure (offline, `429`) stays here with the reason above the action,
 * as on Forgot password. One request at a time.
 */
class ChangePasswordViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ChangePasswordState(email = authRepository.session.value.signedInEmail()))
    val state: StateFlow<ChangePasswordState> = _state.asStateFlow()

    private val _events = Channel<ChangePasswordEvent>()
    val events = _events.receiveAsFlow()

    fun onAction(action: ChangePasswordAction) {
        when (action) {
            ChangePasswordAction.OnSubmit -> submit()
            ChangePasswordAction.OnBackClick -> viewModelScope.launch { _events.send(ChangePasswordEvent.NavigateBack) }
        }
    }

    private fun submit() {
        val current = _state.value
        if (current.submit == SubmitStatus.Sending) return
        // Read again: the session may have become known since the screen opened.
        val email = current.email ?: authRepository.session.value.signedInEmail() ?: return
        _state.update { it.copy(email = email, submit = SubmitStatus.Sending) }
        viewModelScope.launch {
            when (val result = authRepository.requestPasswordReset(email)) {
                is Result.Success -> {
                    _state.update { it.copy(submit = SubmitStatus.Idle) }
                    _events.send(ChangePasswordEvent.NavigateToCode(email))
                }
                is Result.Failure -> _state.update { it.copy(submit = AuthErrorMapper.formFailure(result.error)) }
            }
        }
    }
}

private fun SessionState.signedInEmail(): String? =
    (this as? SessionState.SignedIn)?.user?.email?.takeIf { it.isNotBlank() }
