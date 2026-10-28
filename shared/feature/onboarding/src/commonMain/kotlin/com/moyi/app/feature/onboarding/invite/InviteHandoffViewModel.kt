package com.moyi.app.feature.onboarding.invite

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The code arrives from create bond (FAKE there). Copy flips to Copied and
 * stays: there is nothing to undo, and a second tap copies the same code.
 */
class InviteHandoffViewModel(code: String) : ViewModel() {
    private val _state = MutableStateFlow(InviteHandoffState(code = code))
    val state: StateFlow<InviteHandoffState> = _state.asStateFlow()

    private val _events = Channel<InviteHandoffEvent>()
    val events = _events.receiveAsFlow()

    fun onAction(action: InviteHandoffAction) {
        when (action) {
            // TODO(logic): write the code to the clipboard (Compose's LocalClipboard, in the Root).
            InviteHandoffAction.OnCopyClick -> _state.update { it.copy(copied = true) }
            // TODO(logic): open the OS share sheet with the invite link (`InviteResponse.link` from
            //  `POST /api/v1/bonds`) and the code, through an expect/actual `ShareSheet`
            //  (Intent.ACTION_SEND / UIActivityViewController). It navigates nowhere, and the sheet
            //  closing is never proof the invite was sent (SKILL.md §9).
            InviteHandoffAction.OnShareClick -> Unit
            InviteHandoffAction.OnContinueClick -> viewModelScope.launch { _events.send(InviteHandoffEvent.Finish) }
        }
    }
}
