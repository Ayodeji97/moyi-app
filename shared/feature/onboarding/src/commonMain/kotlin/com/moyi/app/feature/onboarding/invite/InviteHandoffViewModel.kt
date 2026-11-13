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
 * The invite arrives from create bond. Copy puts the code on the clipboard
 * and flips to Copied, which stays: there is nothing to undo, and a second tap
 * copies the same code. Share opens the platform sheet and goes nowhere; the
 * sheet closing is never proof the invite was sent (SKILL.md §9).
 *
 * "I have an invite code" (owner ruling 2026-11-13) is for the person who created a bond but
 * was meant to join the other one's: it opens the join flow, and joining ends this bond.
 */
class InviteHandoffViewModel(code: String, link: String, canJoinInstead: Boolean = false) : ViewModel() {
    private val _state = MutableStateFlow(InviteHandoffState(code = code, link = link, canJoinInstead = canJoinInstead))
    val state: StateFlow<InviteHandoffState> = _state.asStateFlow()

    private val _events = Channel<InviteHandoffEvent>()
    val events = _events.receiveAsFlow()

    fun onAction(action: InviteHandoffAction) {
        when (action) {
            InviteHandoffAction.OnCopyClick -> {
                _state.update { it.copy(copied = true) }
                viewModelScope.launch { _events.send(InviteHandoffEvent.CopyCode(_state.value.code)) }
            }
            InviteHandoffAction.OnShareClick -> viewModelScope.launch {
                val current = _state.value
                _events.send(InviteHandoffEvent.Share(link = current.link, code = current.code))
            }
            InviteHandoffAction.OnContinueClick -> viewModelScope.launch { _events.send(InviteHandoffEvent.Finish) }
            InviteHandoffAction.OnHaveCodeClick -> if (_state.value.canJoinInstead) {
                viewModelScope.launch { _events.send(InviteHandoffEvent.NavigateToJoin) }
            }
        }
    }
}
