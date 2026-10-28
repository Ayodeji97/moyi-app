package com.moyi.app.feature.onboarding.join

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moyi.app.designsystem.presentation.UiText
import com.moyi.app.feature.onboarding.common.FakeOnboardingData
import com.moyi.app.feature.onboarding.common.InviteCodeInput
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
 * The code's shape is checked here for real ([InviteCodeInput]); whether it
 * resolves is FAKE: [FakeOnboardingData.UNUSABLE_CODE] plays "cannot be used"
 * and every other well-formed code resolves.
 *
 * [prefilledCode] is the code the `/i/{code}` lander handed over, or null
 * for manual entry.
 */
class JoinCodeViewModel(prefilledCode: String?) : ViewModel() {
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
                val code = InviteCodeInput.normalise(action.value)
                _state.update { it.copy(code = code, error = InviteCodeInput.liveProblem(code)?.toUiText()) }
            }
            JoinCodeAction.OnContinueClick -> submit()
            JoinCodeAction.OnBackClick -> viewModelScope.launch { _events.send(JoinCodeEvent.NavigateBack) }
        }
    }

    private fun submit() {
        val code = _state.value.code
        val problem = InviteCodeInput.submitProblem(code)
        if (problem != null) {
            _state.update { it.copy(error = problem.toUiText()) }
            return
        }
        // TODO(logic): `GET /api/v1/invites/{code}` (authenticated only, T-06), PrimaryAction Sending
        //  ("Checking") while in flight. The contract declares 200, 401, 403, 404, 422, 429 and 500 (no 410).
        //  200 InvitePreviewResponse → confirm; 404 INVITE_NOT_USABLE → cannot be used, for every cause
        //  (expired, revoked, used, full, unknown, blocked), indistinguishable by design (FR-024, ADR-0027,
        //  06 §3); 422 → the character error; 401 → the session layer refreshes or ends it; 403, 429, 500
        //  and offline → InlineError, code kept. `bondType` may be PARENT_CHILD, which
        //  [com.moyi.app.feature.onboarding.common.RelationshipType] has no card for: map it to a fallback
        //  (the confirm screen's generic line), never crash on it.
        val event = if (code == FakeOnboardingData.UNUSABLE_CODE) {
            JoinCodeEvent.NavigateToCannotBeUsed
        } else {
            JoinCodeEvent.NavigateToConfirm(code)
        }
        viewModelScope.launch { _events.send(event) }
    }

    private fun InviteCodeInput.Problem.toUiText(): UiText = UiText.Resource(
        when (this) {
            InviteCodeInput.Problem.UnusedCharacter -> Res.string.field_code_error_character
            InviteCodeInput.Problem.TooShort -> Res.string.field_code_error_length
        },
    )
}
