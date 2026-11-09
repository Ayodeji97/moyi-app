package com.moyi.app.feature.today.reveal

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moyi.app.designsystem.component.EntryPhoto
import com.moyi.app.designsystem.component.VoiceNote
import com.moyi.app.feature.today.common.DailyLoop
import com.moyi.app.feature.today.common.DailyLoopState
import com.moyi.app.feature.today.common.ReactResult
import com.moyi.app.feature.today.common.nextReaction
import com.moyi.app.domain.today.Reaction
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
import kotlin.time.Duration.Companion.milliseconds

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
    val myVoice: VoiceNote? = null,
    /** The partner's voice note: like [partnerEntry], only in [RevealPhase.After]. */
    val partnerVoice: VoiceNote? = null,
    /** The partner's entry takes my reaction (the loop's [com.moyi.app.feature.today.common.DayView.canReact]). */
    val canReact: Boolean = false,
    /** The reaction I left on the partner's entry, as shown (optimistically while on its way). */
    val myReaction: Reaction? = null,
    /**
     * The reaction set is shown and takes taps: only once the turn's motion has settled
     * (owner ruling 3, 2026-11-09). Before that it is laid out but invisible and silent, so
     * nothing moves when it appears and the turn itself is undisturbed.
     */
    val reactionsShown: Boolean = false,
    /** The last reaction tap did not go through: it is back as it was, and the Generic error line says so. */
    val reactionFailed: Boolean = false,
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

    /** A chip of the reaction set, once it is shown: leaves, replaces or takes back mine. */
    data class OnReactionClick(val reaction: Reaction) : RevealAction
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
 * **Reactions** (owner ruling 3, 2026-11-09): the set is drawn under the partner's entry, as
 * on Today, but shown only [motionSettle] after the turn, when the after frame has stopped
 * moving. A tap goes to the [DailyLoop] as Today's does (optimistic, one at a time). The
 * moment never settles away from a reaction still on its way: the settle waits for its answer,
 * so leaving the screen cannot cancel the call.
 *
 * TODO(logic): open it from the "Your day is open." push too (no push handling yet),
 *  and remember across launches that it played (the ledger is in memory: owed).
 */
class RevealViewModel(
    private val loop: DailyLoop,
    private val beforeHold: Duration = RevealMotion.BeforeHold,
    private val afterHold: Duration = RevealMotion.AfterHold,
    /** How long the turn moves: the reaction set shows once it has (the longer of the two transitions). */
    private val motionSettle: Duration = RevealMotion.RevealMillis.milliseconds,
) : ViewModel() {

    private val day = (loop.state.value as? DailyLoopState.Ready)?.day

    /** Shown only once the day has turned: the before frame never holds them. */
    private val partnerWords: String? = day?.partnerEntry
    private val partnerPhoto: EntryPhoto? = day?.partnerPhoto
    private val partnerVoice: VoiceNote? = day?.partnerVoice

    private val _state = MutableStateFlow(
        RevealState(
            date = day?.date,
            partnerName = day?.partnerName.orEmpty(),
            myEntry = day?.myEntry.orEmpty(),
            myPhoto = day?.myPhoto,
            myVoice = day?.myVoice,
            canReact = day?.canReact == true,
            myReaction = day?.myReaction?.takeIf { day.canReact },
        ),
    )
    val state: StateFlow<RevealState> = _state.asStateFlow()

    private val _events = Channel<RevealEvent>()
    val events = _events.receiveAsFlow()

    private var sequence: Job? = null

    /** Settle is sent once: a Back as the hold ends must not navigate twice. */
    private var settled = false

    /** A reaction call has not answered yet: a second tap is ignored, and the settle waits for it. */
    private var reacting = false

    /** The moment ended while a reaction was on its way: it settles as soon as the call answers. */
    private var settleWhenAnswered = false

    init {
        // Only my reaction follows the loop (optimistic, then confirmed or put back); the words
        // stay as they were when the reveal opened.
        viewModelScope.launch {
            loop.state.collect { loaded ->
                val view = (loaded as? DailyLoopState.Ready)?.day ?: return@collect
                _state.update { it.copy(canReact = view.canReact, myReaction = view.myReaction.takeIf { view.canReact }) }
            }
        }
        play()
    }

    fun onAction(action: RevealAction) {
        when (action) {
            RevealAction.OnDebugReplay -> play()
            RevealAction.OnBack -> settle()
            is RevealAction.OnReactionClick -> react(action.reaction)
        }
    }

    /** As Today's: the same one takes mine back, another replaces it; one call at a time, only once shown. */
    private fun react(tapped: Reaction) {
        val shown = _state.value
        if (reacting || settled || !shown.reactionsShown || !shown.canReact) return
        reacting = true
        _state.update { it.copy(reactionFailed = false) }
        viewModelScope.launch {
            val result = loop.react(nextReaction(shown.myReaction, tapped))
            reacting = false
            _state.update { it.copy(reactionFailed = result == ReactResult.Failed) }
            if (settleWhenAnswered) settle()
        }
    }

    /** Ends the moment now: the day is open whether or not the animation finished. */
    private fun settle() {
        if (settled) return
        if (reacting) {
            settleWhenAnswered = true
            return
        }
        settled = true
        sequence?.cancel()
        // Nothing to write back: the day is already revealed, and Today below shows it.
        viewModelScope.launch { _events.send(RevealEvent.Settle) }
    }

    private fun play() {
        if (settled) return
        sequence?.cancel()
        _state.update { it.copy(phase = RevealPhase.Before, partnerEntry = null, partnerPhoto = null, partnerVoice = null, reactionsShown = false) }
        sequence = viewModelScope.launch {
            delay(beforeHold)
            _state.update { it.copy(phase = RevealPhase.After, partnerEntry = partnerWords, partnerPhoto = partnerPhoto, partnerVoice = partnerVoice) }
            // The set shows once the turn has stopped moving, within the hold.
            val settleIn = minOf(motionSettle, afterHold)
            delay(settleIn)
            _state.update { it.copy(reactionsShown = true) }
            delay(afterHold - settleIn)
            settle()
        }
    }
}
