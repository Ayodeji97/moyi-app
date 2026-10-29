package com.moyi.app.feature.onboarding.join

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moyi.app.core.ApiErrorCode
import com.moyi.app.core.Result
import com.moyi.app.designsystem.presentation.UiText
import com.moyi.app.domain.bond.BondRepository
import com.moyi.app.feature.onboarding.common.InviteCodeInput
import com.moyi.app.feature.onboarding.common.code
import com.moyi.app.feature.onboarding.common.formMessage
import com.moyi.app.feature.onboarding.resources.Res
import com.moyi.app.feature.onboarding.resources.field_code_error_character
import com.moyi.app.feature.onboarding.resources.field_code_error_length
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The code's shape is checked here ([InviteCodeInput]); whether it works is
 * the server's: `GET /invites/{code}`, authenticated only (T-06).
 *
 * - `200`: confirm, carrying the preview so the code is resolved once.
 * - `404 INVITE_NOT_USABLE`: cannot be used, one screen for every cause
 *   (expired, revoked, used, full, unknown, blocked; FR-024).
 * - `422`: the server's word on the shape, under the field. The checks here
 *   should stop it first.
 * - `401` is the session layer's (refresh, or the session ends and the shell
 *   routes to sign in). Offline, `429` and anything else: an inline error
 *   above the action, the code kept.
 *
 * [prefilledCode] is the code the `/i/{code}` lander handed over, or null
 * for manual entry.
 */
class JoinCodeViewModel(
    prefilledCode: String?,
    private val bonds: BondRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(
        JoinCodeState(
            prefilled = prefilledCode != null,
            code = prefilledCode?.let(InviteCodeInput::normalise).orEmpty(),
        ),
    )
    val state: StateFlow<JoinCodeState> = _state.asStateFlow()

    private val _events = Channel<JoinCodeEvent>()
    val events = _events.receiveAsFlow()

    fun onAction(action: JoinCodeAction) {
        when (action) {
            is JoinCodeAction.OnCodeChange -> {
                if (_state.value.checking) return
                val code = InviteCodeInput.normalise(action.value)
                _state.update { it.copy(code = code, error = InviteCodeInput.liveProblem(code)?.toUiText(), failure = null) }
            }
            JoinCodeAction.OnContinueClick -> submit()
            JoinCodeAction.OnBackClick -> viewModelScope.launch { _events.send(JoinCodeEvent.NavigateBack) }
        }
    }

    private fun submit() {
        val current = _state.value
        if (current.checking) return
        val problem = InviteCodeInput.submitProblem(current.code)
        if (problem != null) {
            _state.update { it.copy(error = problem.toUiText()) }
            return
        }
        _state.update { it.copy(checking = true, failure = null) }
        viewModelScope.launch {
            val resolved = bonds.resolveInvite(current.code)
            _state.update { it.copy(checking = false) }
            when (resolved) {
                is Result.Success -> _events.send(JoinCodeEvent.NavigateToConfirm(current.code, resolved.data))
                is Result.Failure -> when (resolved.error.code()) {
                    ApiErrorCode.INVITE_NOT_USABLE -> _events.send(JoinCodeEvent.NavigateToCannotBeUsed)
                    ApiErrorCode.VALIDATION_FAILED -> _state.update {
                        it.copy(error = UiText.Resource(Res.string.field_code_error_character))
                    }
                    else -> _state.update { it.copy(failure = resolved.error.formMessage()) }
                }
            }
        }
    }

    private fun InviteCodeInput.Problem.toUiText(): UiText = UiText.Resource(
        when (this) {
            InviteCodeInput.Problem.UnusedCharacter -> Res.string.field_code_error_character
            InviteCodeInput.Problem.TooShort -> Res.string.field_code_error_length
        },
    )
}
