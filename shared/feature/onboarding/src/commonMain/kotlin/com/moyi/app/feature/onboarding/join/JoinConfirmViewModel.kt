package com.moyi.app.feature.onboarding.join

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moyi.app.feature.onboarding.common.FakeOnboardingData
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** FAKE: every code resolves to the same inviter and bond, and joining succeeds at once. */
class JoinConfirmViewModel(private val code: String) : ViewModel() {
    // TODO(logic): the InvitePreviewResponse (bondName, bondType, inviterDisplayName) from the
    //  `GET /api/v1/invites/{code}` the code screen made; pass it on, do not resolve the code twice.
    //  `bondType` includes PARENT_CHILD, which RelationshipType cannot map: it needs a fallback line.
    private val _state = MutableStateFlow(
        JoinConfirmState(
            inviterName = FakeOnboardingData.INVITER_NAME,
            bondName = FakeOnboardingData.BOND_NAME,
            type = FakeOnboardingData.BOND_TYPE,
        ),
    )
    val state: StateFlow<JoinConfirmState> = _state.asStateFlow()

    private val _events = Channel<JoinConfirmEvent>()
    val events = _events.receiveAsFlow()

    fun onAction(action: JoinConfirmAction) {
        when (action) {
            // TODO(logic): `POST /api/v1/invites/{code}/accept` with [code], PrimaryAction Sending
            //  ("Joining"). The contract declares 200, 401, 403, 404, 409, 422, 429 and 500 (no 410).
            //  200 → Today, Day 1. 404 INVITE_NOT_USABLE (every way a code fails, full included, FR-024) →
            //  cannot be used. 409 is the caller's own state, so it may be said: ALREADY_MEMBER or
            //  BOND_LIMIT_REACHED (copy owed). 422 (malformed; the code screen should stop it first) →
            //  cannot be used. 401 → the session layer; 403 EMAIL_NOT_VERIFIED, 429, 500 and offline →
            //  InlineError and stay. Accepting spends the invite (single-use, FR-023).
            JoinConfirmAction.OnJoinClick -> viewModelScope.launch { _events.send(JoinConfirmEvent.Finish) }
            JoinConfirmAction.OnNotNowClick, JoinConfirmAction.OnBackClick ->
                viewModelScope.launch { _events.send(JoinConfirmEvent.NavigateBack) }
        }
    }
}
