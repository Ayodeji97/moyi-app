package com.moyi.app.feature.today.write

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moyi.app.feature.today.common.DailyLoop
import com.moyi.app.feature.today.common.DailyLoopState
import com.moyi.app.feature.today.common.FakeCopy
import com.moyi.app.feature.today.common.WriteResult
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * The composer, over the shared [DailyLoop]: it opens on the day Today loaded (its
 * prompt, or my words when editing) and sends through the loop, which refreshes Today.
 *
 * Online only. Sending waits for the server (PrimaryAction Sending, no spinner) and
 * closes on its answer; a failure keeps the words and shows Save failed (384:229).
 * There is no offline queue yet (doc 13 §5's outbox is owed), so Queued is reached
 * only by the debug switch.
 *
 * @param editing true when reached from "Edit what you wrote" (before the reveal).
 * @param newIdempotencyKey mints the `Idempotency-Key` at the tap; tests pass a counter.
 */
class WriteViewModel(
    editing: Boolean,
    private val loop: DailyLoop,
    private val newIdempotencyKey: () -> String = ::randomIdempotencyKey,
) : ViewModel() {

    private val day = (loop.state.value as? DailyLoopState.Ready)?.day

    /** Today's prompt, sent with the entry unless the person dismissed it. */
    private val promptId: String? = day?.prompt?.id

    private val _state = MutableStateFlow(
        if (editing) {
            day?.myEntry.orEmpty().let { mine -> WriteState(text = mine, prompt = null, editing = true, originalText = mine) }
        } else {
            // TODO(logic): restore today's local draft first (doc 13 §5: drafts are local-first,
            //  in SQLDelight, and survive a crash or force-quit). Owed with SQLDelight.
            WriteState(prompt = day?.prompt?.text)
        },
    )
    val state: StateFlow<WriteState> = _state.asStateFlow()

    private val _events = Channel<WriteEvent>()
    val events = _events.receiveAsFlow()

    /**
     * The key of the send that has not yet succeeded, and the words it carried. A retry
     * of the same words is the same send, so it reuses the key: if the first attempt
     * reached the server and only its answer was lost, the server replays it instead of
     * writing twice (doc 06 §1). Different words are a different send, with a new key.
     */
    private var pending: Pending? = null

    private var debugStep = 0

    fun onAction(action: WriteAction) {
        when (action) {
            is WriteAction.OnTextChange -> {
                // The words being sent are the words on screen: no edits in flight.
                if (_state.value.submit == WriteSubmit.Sending) return
                // TODO(logic): autosave the draft locally on every keystroke pause (debounced, about
                //  500 ms; doc 13 §5, states.md §4 "Draft saved: silent"). No toast, no "saved". Owed.
                _state.update { it.copy(text = action.text, submit = it.submit.afterEdit()) }
            }
            // A dismissed prompt sends no promptId with the entry (submit, below).
            WriteAction.OnDismissPrompt -> _state.update { it.copy(prompt = null) }
            WriteAction.OnSubmit -> submit()
            WriteAction.OnCloseClick -> requestClose()
            WriteAction.OnDiscardConfirm -> {
                // TODO(logic): delete today's local draft (doc 13 §5) before closing. Owed.
                _state.update { it.copy(confirmingDiscard = false) }
                close()
            }
            WriteAction.OnDiscardDismiss -> _state.update { it.copy(confirmingDiscard = false) }
            WriteAction.OnAddPhotoClick -> {
                // TODO(logic): pick a photo and send its id as `imageMediaId` (POST and PATCH both take it).
                //  Optional (NFR-103). The contract has no media upload endpoint yet, so there is no id to send.
            }
            WriteAction.OnRecordVoiceClick -> {
                // TODO(logic): record a voice note and send its id as `voiceMediaId` (POST and PATCH both take
                //  it). Optional and secondary; text is always sufficient (NFR-103). The contract has no media
                //  upload endpoint yet, so there is no id to send.
            }
            WriteAction.OnDebugCycle -> debugCycle()
        }
    }

    /**
     * New entry: POST with today's prompt id (unless dismissed) and the key minted at
     * this tap. Editing: PATCH, with a key minted the same way (optional in the contract).
     * A 409 (ENTRY_ALREADY_EXISTS; ENTRY_IMMUTABLE or BOND_ARCHIVED on an edit) has no
     * drawn state (states.md §5a): it shows Save failed with the words kept, and the loop
     * refreshes Today underneath, so closing lands on what the server holds.
     */
    private fun submit() {
        val current = _state.value
        if (!current.canSubmit || current.submit == WriteSubmit.Sending || current.submit == WriteSubmit.Queued) return
        val key = pending?.takeIf { it.text == current.text }?.key ?: newIdempotencyKey()
        pending = Pending(current.text, key)
        _state.update { it.copy(submit = WriteSubmit.Sending) }
        viewModelScope.launch {
            val result = if (current.editing) {
                loop.edit(current.text, key)
            } else {
                loop.submit(current.text, promptId.takeIf { current.prompt != null }, key)
            }
            when (result) {
                WriteResult.Saved -> {
                    pending = null
                    // The state stays Sending until the composer closes: the words are saved, and a
                    // close tapped in the moment before it goes must not ask to discard them (D10).
                    _events.send(WriteEvent.Close)
                }
                WriteResult.Failed -> _state.update { it.copy(submit = WriteSubmit.Failed) }
            }
        }
    }

    /**
     * D10: closing asks first only when it would lose words. While the words are
     * being sent, closing waits: the send decides, and it closes on its own.
     */
    private fun requestClose() {
        val current = _state.value
        when {
            current.submit == WriteSubmit.Sending || current.confirmingDiscard -> Unit
            current.wouldLoseWords -> _state.update { it.copy(confirmingDiscard = true) }
            else -> close()
        }
    }

    private fun close() {
        viewModelScope.launch { _events.send(WriteEvent.Close) }
    }

    /** Typing after a failure or while queued keeps the state honest: the words changed. */
    private fun WriteSubmit.afterEdit(): WriteSubmit = if (this == WriteSubmit.Failed) WriteSubmit.Idle else this

    /**
     * DEBUG ONLY. Steps through the drawn composer frames, in Figma's order: empty (181:3),
     * past 400 (181:43), over limit (181:83), sending (419:193), editing (419:291), save
     * failed (384:229), queued, and back to empty.
     */
    private fun debugCycle() {
        debugStep = (debugStep + 1) % DEBUG_STEPS
        _state.value = when (debugStep) {
            1 -> WriteState(text = sampleOf(PAST_400), prompt = FakeCopy.PROMPT)
            2 -> WriteState(text = sampleOf(OVER_LIMIT), prompt = FakeCopy.PROMPT)
            3 -> WriteState(text = FakeCopy.MY_ENTRY, prompt = FakeCopy.PROMPT, submit = WriteSubmit.Sending)
            4 -> WriteState(text = FakeCopy.MY_ENTRY, prompt = null, editing = true, originalText = FakeCopy.MY_ENTRY)
            5 -> WriteState(text = FakeCopy.MY_ENTRY, prompt = FakeCopy.PROMPT, submit = WriteSubmit.Failed)
            6 -> WriteState(text = FakeCopy.MY_ENTRY, prompt = FakeCopy.PROMPT, submit = WriteSubmit.Queued)
            else -> WriteState(prompt = FakeCopy.PROMPT)
        }
    }

    /**
     * Filler for the long debug frames: my own words, repeated. Never the partner's: their
     * words must never appear in my composer, not even as a stand-in.
     */
    private fun sampleOf(length: Int): String {
        val words = "${FakeCopy.MY_ENTRY} "
        return words.repeat(length / words.length + 1).take(length)
    }

    /** Never prints the words. */
    private class Pending(val text: String, val key: String)

    companion object {
        private const val DEBUG_STEPS = 7
        private const val PAST_400 = 412
        private const val OVER_LIMIT = 512
    }
}

/** A random (version 4) UUID: entry ids and keys must not leak when they were made (doc 06 §1). */
@OptIn(ExperimentalUuidApi::class)
internal fun randomIdempotencyKey(): String = Uuid.random().toString()
