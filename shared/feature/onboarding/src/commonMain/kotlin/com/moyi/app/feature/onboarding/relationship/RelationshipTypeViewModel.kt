package com.moyi.app.feature.onboarding.relationship

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
 * Holds the choice so a return visit (back from create bond) shows SELECTED.
 * Nothing is sent from here: the type travels to create bond and goes out
 * with `POST /api/v1/bonds`.
 */
class RelationshipTypeViewModel : ViewModel() {
    private val _state = MutableStateFlow(RelationshipTypeState())
    val state: StateFlow<RelationshipTypeState> = _state.asStateFlow()

    private val _events = Channel<RelationshipTypeEvent>()
    val events = _events.receiveAsFlow()

    fun onAction(action: RelationshipTypeAction) {
        when (action) {
            is RelationshipTypeAction.OnSelect -> {
                _state.update { it.copy(selected = action.type) }
                viewModelScope.launch { _events.send(RelationshipTypeEvent.NavigateToCreateBond(action.type)) }
            }
            RelationshipTypeAction.OnBackClick -> viewModelScope.launch { _events.send(RelationshipTypeEvent.NavigateBack) }
        }
    }
}
