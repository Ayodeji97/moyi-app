package com.moyi.app.feature.today.streak

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moyi.app.core.Result
import com.moyi.app.domain.streak.StreakRepository
import com.moyi.app.feature.today.common.DailyLoop
import com.moyi.app.feature.today.common.DailyLoopState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The Streak screen, from the Archive's "Your streak" (states.md §7).
 *
 * `GET /bonds/{bondId}/streak` each time it opens, with the bare surface until it answers
 * and the Generic error with Try again if it does not. **Server-authoritative**: the
 * numbers are shown as sent, never computed or incremented here; the ViewModel only
 * chooses which cells and which line to draw ([toStreakUi]).
 */
class StreakViewModel(
    private val streaks: StreakRepository,
    private val loop: DailyLoop,
) : ViewModel() {
    private val _state = MutableStateFlow(StreakState())
    val state: StateFlow<StreakState> = _state.asStateFlow()

    private val _events = Channel<StreakEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch { load() }
    }

    fun onAction(action: StreakAction) {
        when (action) {
            StreakAction.OnBackClick -> viewModelScope.launch { _events.send(StreakEvent.NavigateBack) }
            StreakAction.OnRetryClick, StreakAction.OnDebugCycle -> viewModelScope.launch { load() }
        }
    }

    private suspend fun load() {
        _state.update { it.copy(load = StreakLoad.Loading) }
        val bondId = (loop.state.value as? DailyLoopState.Ready)?.day?.bondId
        val answer = bondId?.let { streaks.streak(it) }
        val ui = (answer as? Result.Success)?.data?.toStreakUi()
        _state.update { if (ui == null) StreakState(load = StreakLoad.Failed) else StreakState(StreakLoad.Ready, ui) }
    }
}
