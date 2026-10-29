package com.moyi.app.feature.today.reveal

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moyi.app.feature.today.common.FakeCopy
import com.moyi.app.feature.today.common.FakeDailyLoop
import com.moyi.app.feature.today.common.TodayStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration

/** The two drawn frames of the reveal: before (179:3) and after (179:8). */
enum class RevealPhase { Before, After }

@Immutable
data class RevealState(
    val phase: RevealPhase = RevealPhase.Before,
    val date: String = FakeCopy.DATE,
    val partnerName: String = FakeCopy.PARTNER_NAME,
    val myEntry: String = FakeCopy.MY_ENTRY,
    /** The partner's words: null in [RevealPhase.Before], set only once the day turns to [RevealPhase.After]. */
    val partnerEntry: String? = null,
)

sealed interface RevealAction {
    /**
     * Android Back during the reveal (D12): the moment ends early and settles into
     * Today · revealed, exactly as if it had played out. Never out of the app, and
     * never back to before the day opened: the reveal is a point of no return.
     */
    data object OnBack : RevealAction

    /** DEBUG ONLY (PR #8): play the reveal again from before. */
    data object OnDebugReplay : RevealAction
}

sealed interface RevealEvent {
    /** The moment is over: settle into Today · revealed (159:19). */
    data object Settle : RevealEvent
}

/**
 * The reveal, a state of Today rather than a destination (SKILL.md §9): it opens on
 * the before frame, turns to after (the product's one animation), holds, and settles
 * into Today · revealed.
 *
 * TODO(logic): reached when GET …/today first reports REVEALED for a day this device
 *  last saw as PARTIAL or PENDING_REVEAL (so it plays once per day per device, and
 *  never for PENDING_REVEAL, states.md §5a), or from the "Your day is open." push.
 *  The partner's text arrives in that same response.
 */
class RevealViewModel(
    private val loop: FakeDailyLoop,
    private val beforeHold: Duration = RevealMotion.BeforeHold,
    private val afterHold: Duration = RevealMotion.AfterHold,
) : ViewModel() {

    private val _state = MutableStateFlow(RevealState(myEntry = loop.day.value.myEntry))
    val state: StateFlow<RevealState> = _state.asStateFlow()

    private val _events = Channel<RevealEvent>()
    val events = _events.receiveAsFlow()

    private var sequence: Job? = null

    /** Settle is sent once: a Back as the hold ends must not navigate twice. */
    private var settled = false

    init {
        play()
    }

    fun onAction(action: RevealAction) {
        when (action) {
            RevealAction.OnDebugReplay -> play()
            RevealAction.OnBack -> settle()
        }
    }

    /** Ends the moment now: the day is open whether or not the animation finished. */
    private fun settle() {
        if (settled) return
        settled = true
        sequence?.cancel()
        loop.setStatus(TodayStatus.Revealed)
        viewModelScope.launch { _events.send(RevealEvent.Settle) }
    }

    private fun play() {
        if (settled) return
        sequence?.cancel()
        _state.update { it.copy(phase = RevealPhase.Before, partnerEntry = null) }
        sequence = viewModelScope.launch {
            delay(beforeHold)
            _state.update { it.copy(phase = RevealPhase.After, partnerEntry = loop.day.value.partnerEntry) }
            delay(afterHold)
            settle()
        }
    }
}
