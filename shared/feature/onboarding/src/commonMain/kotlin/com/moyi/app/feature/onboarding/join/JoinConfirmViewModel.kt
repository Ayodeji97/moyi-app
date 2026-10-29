package com.moyi.app.feature.onboarding.join

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moyi.app.core.ApiErrorCode
import com.moyi.app.core.Result
import com.moyi.app.designsystem.presentation.UiText
import com.moyi.app.domain.bond.BondRepository
import com.moyi.app.domain.bond.InvitePreview
import com.moyi.app.feature.onboarding.common.RelationshipType
import com.moyi.app.feature.onboarding.common.code
import com.moyi.app.feature.onboarding.common.formMessage
import com.moyi.app.feature.onboarding.common.isUnverified
import com.moyi.app.feature.onboarding.resources.Res
import com.moyi.app.feature.onboarding.resources.join_error_limit
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Join · confirm. Shows the preview the code screen resolved, and joins with
 * `POST /invites/{code}/accept`, which spends the invite (single use, FR-023).
 *
 * - `200`: Today, Day 1 for both.
 * - `404 INVITE_NOT_USABLE`, or a `422`: cannot be used. The code may have
 *   been revoked, spent or filled since it resolved; nothing says which.
 * - `409 ALREADY_MEMBER`: the person is in this bond already (usually the
 *   creator trying their own code), so they are where joining would take
 *   them: Today.
 * - `409 BOND_LIMIT_REACHED`: said, because it is the caller's own state.
 * - `403 EMAIL_NOT_VERIFIED`: the gate.
 * - Offline, `429`, anything else: inline, and they can try again.
 */
class JoinConfirmViewModel(
    private val code: String,
    preview: InvitePreview,
    private val bonds: BondRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(
        JoinConfirmState(
            inviterName = preview.inviterName,
            bondName = preview.bondName,
            type = RelationshipType.of(preview.bondType),
        ),
    )
    val state: StateFlow<JoinConfirmState> = _state.asStateFlow()

    private val _events = Channel<JoinConfirmEvent>()
    val events = _events.receiveAsFlow()

    fun onAction(action: JoinConfirmAction) {
        when (action) {
            JoinConfirmAction.OnJoinClick -> join()
            JoinConfirmAction.OnNotNowClick, JoinConfirmAction.OnBackClick ->
                if (!_state.value.joining) viewModelScope.launch { _events.send(JoinConfirmEvent.NavigateBack) }
        }
    }

    private fun join() {
        if (_state.value.joining) return
        _state.update { it.copy(joining = true, failure = null) }
        viewModelScope.launch {
            val accepted = bonds.acceptInvite(code)
            // Joined (or already in it): the button stays Joining until the screen goes,
            // so a second tap cannot send a second accept while Finish is on its way.
            if (accepted is Result.Success) {
                _events.send(JoinConfirmEvent.Finish)
                return@launch
            }
            val error = (accepted as Result.Failure).error
            if (error.code() == ApiErrorCode.ALREADY_MEMBER) {
                _events.send(JoinConfirmEvent.Finish)
                return@launch
            }
            _state.update { it.copy(joining = false) }
            when {
                error.code() == ApiErrorCode.INVITE_NOT_USABLE || error.code() == ApiErrorCode.VALIDATION_FAILED ->
                    _events.send(JoinConfirmEvent.NavigateToCannotBeUsed)
                error.isUnverified() -> _events.send(JoinConfirmEvent.NavigateToGate)
                error.code() == ApiErrorCode.BOND_LIMIT_REACHED ->
                    _state.update { it.copy(failure = UiText.Resource(Res.string.join_error_limit)) }
                else -> _state.update { it.copy(failure = error.formMessage()) }
            }
        }
    }
}
