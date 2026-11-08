package com.moyi.app.feature.today.reveal

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moyi.app.designsystem.component.EntryPhoto
import com.moyi.app.feature.today.common.DailyLoop
import com.moyi.app.feature.today.common.DailyLoopState
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlin.time.Duration

/** The two drawn frames of the reveal: before (179:3) and after (179:8). */
enum class RevealPhase { Before, After }

@Immutable
data class RevealState(
    val phase: RevealPhase = RevealPhase.Before,
    val date: LocalDate? = null,
    val partnerName: String = "",
    val myEntry: String = "",
    /** The partner's words: null in [RevealPhase.Before], set only once the day turns to [RevealPhase.After]. */
    val partnerEntry: String? = null,
    val myPhoto: EntryPhoto? = null,
    /** The partner's photo: like [partnerEntry], only in [RevealPhase.After]. */
    val partnerPhoto: EntryPhoto? = null,
)

sealed interface RevealAction {
    /**
     * Android Back during the reveal (D12): the moment ends early and settles into
     * Today · revealed, exactly as if it had played out. Never out of the app, and
     * never back to before the day opened: the reveal is a point of no return.
     */
    data object OnBack : RevealAction

    /** DEBUG ONLY (`-PmoyiUiPreview=today`): play the reveal again from before. */
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
 * Reached from Today when a refresh first finds the day revealed with the partner's
 * words, once per bond-day in this process (Today's RevealLedger; never for
 * PENDING_REVEAL, states.md §5a). It plays the day Today loaded: the partner's text
 * arrived in that same response, and is held here until the turn.
 *
 * TODO(logic): open it from the "Your day is open." push too (no push handling yet),
 *  and remember across launches that it played (the ledger is in memory: owed).
 */
class RevealViewModel(
    loop: DailyLoop,
    private val beforeHold: Duration = RevealMotion.BeforeHold,
    private val afterHold: Duration = RevealMotion.AfterHold,
) : ViewModel() {

    private val day = (loop.state.value as? DailyLoopState.Ready)?.day

    /** Shown only once the day has turned: the before frame never holds them. */
    private val partnerWords: String? = day?.partnerEntry
    private val partnerPhoto: EntryPhoto? = day?.partnerPhoto

    private val _state = MutableStateFlow(
        RevealState(date = day?.date, partnerName = day?.partnerName.orEmpty(), myEntry = day?.myEntry.orEmpty(), myPhoto = day?.myPhoto),
    )
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
        // Nothing to write back: the day is already revealed, and Today below shows it.
        viewModelScope.launch { _events.send(RevealEvent.Settle) }
    }

    private fun play() {
        if (settled) return
        sequence?.cancel()
        _state.update { it.copy(phase = RevealPhase.Before, partnerEntry = null, partnerPhoto = null) }
        sequence = viewModelScope.launch {
            delay(beforeHold)
            _state.update { it.copy(phase = RevealPhase.After, partnerEntry = partnerWords, partnerPhoto = partnerPhoto) }
            delay(afterHold)
            settle()
        }
    }
}
