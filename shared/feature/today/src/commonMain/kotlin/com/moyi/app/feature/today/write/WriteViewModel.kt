package com.moyi.app.feature.today.write

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moyi.app.feature.today.common.FakeCopy
import com.moyi.app.feature.today.common.FakeDailyLoop
import com.moyi.app.feature.today.common.FakeLatency
import com.moyi.app.feature.today.common.TodayStatus
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration

/**
 * The composer, over the FAKE day (PR #8: UI only).
 *
 * @param editing true when reached from "Edit what you wrote" (before the reveal).
 * @param fakeLatency how long the fake send takes, so Sending is visible; tests pass zero.
 */
class WriteViewModel(
    editing: Boolean,
    private val loop: FakeDailyLoop,
    private val fakeLatency: Duration = FakeLatency,
) : ViewModel() {

    private val _state = MutableStateFlow(
        if (editing) {
            WriteState(text = loop.day.value.myEntry, prompt = null, editing = true)
        } else {
            // TODO(logic): restore today's local draft first (doc 13 §5: drafts are local-first,
            //  in SQLDelight, and survive a crash or force-quit), and take the prompt from
            //  GET /api/v1/bonds/{bondId}/today.
            WriteState()
        },
    )
    val state: StateFlow<WriteState> = _state.asStateFlow()

    private val _events = Channel<WriteEvent>()
    val events = _events.receiveAsFlow()

    private var debugStep = 0

    fun onAction(action: WriteAction) {
        when (action) {
            is WriteAction.OnTextChange -> {
                // The words being sent are the words on screen: no edits in flight.
                if (_state.value.submit == WriteSubmit.Sending) return
                // TODO(logic): autosave the draft locally on every keystroke pause (debounced, about
                //  500 ms; doc 13 §5, states.md §4 "Draft saved: silent"). No toast, no "saved".
                _state.update { it.copy(text = action.text, submit = it.submit.afterEdit()) }
            }
            WriteAction.OnDismissPrompt -> {
                // TODO(logic): a dismissed prompt sends no `promptId` with the entry.
                _state.update { it.copy(prompt = null) }
            }
            WriteAction.OnSubmit -> submit()
            WriteAction.OnBackClick -> {
                // TODO(logic): the draft is already saved locally, so back loses nothing.
                viewModelScope.launch { _events.send(WriteEvent.NavigateBack) }
            }
            WriteAction.OnAddPhotoClick -> {
                // TODO(logic): pick a photo, upload it, and send its id as `imageMediaId`. Optional (NFR-103).
            }
            WriteAction.OnRecordVoiceClick -> {
                // TODO(logic): record a voice note. Optional and secondary; text is always sufficient (NFR-103).
            }
            WriteAction.OnDebugCycle -> debugCycle()
        }
    }

    private fun submit() {
        val current = _state.value
        if (!current.canSubmit || current.submit == WriteSubmit.Sending || current.submit == WriteSubmit.Queued) return
        // TODO(logic): new entry: mint the Idempotency-Key at THIS tap (a UUID, kept with the queued
        //  entry and reused on every retry; states.md §4), stamp `intendedAt` (the Bond-day it was
        //  written for, BR-3a), and POST /api/v1/bonds/{bondId}/entries with { text, promptId?,
        //  imageMediaId?, intendedAt }. Optimistic: Today shows written-waiting at once.
        //  Offline: put it in the local outbox and show Today · queued (384:327) instead.
        //  Editing: PATCH /api/v1/entries/{entryId} with { text }; 409 ENTRY_IMMUTABLE means the day
        //  opened while editing, which has no drawn state yet (states.md §5a).
        //  A failure keeps the text and shows Failed (384:229); a 2xx clears the local draft.
        _state.update { it.copy(submit = WriteSubmit.Sending) }
        viewModelScope.launch {
            delay(fakeLatency)
            loop.setMyEntry(current.text, TodayStatus.Waiting)
            _state.update { it.copy(submit = WriteSubmit.Idle) }
            _events.send(WriteEvent.NavigateBack)
        }
    }

    /** Typing after a failure or while queued keeps the state honest: the words changed. */
    private fun WriteSubmit.afterEdit(): WriteSubmit = if (this == WriteSubmit.Failed) WriteSubmit.Idle else this

    /**
     * Steps through the drawn composer frames, in Figma's order: empty (181:3), past 400
     * (181:43), over limit (181:83), sending (419:193), editing (419:291), save failed
     * (384:229), queued, and back to empty.
     */
    private fun debugCycle() {
        debugStep = (debugStep + 1) % DEBUG_STEPS
        _state.value = when (debugStep) {
            1 -> WriteState(text = sampleOf(PAST_400))
            2 -> WriteState(text = sampleOf(OVER_LIMIT))
            3 -> WriteState(text = FakeCopy.MY_ENTRY, submit = WriteSubmit.Sending)
            4 -> WriteState(text = FakeCopy.MY_ENTRY, prompt = null, editing = true)
            5 -> WriteState(text = FakeCopy.MY_ENTRY, submit = WriteSubmit.Failed)
            6 -> WriteState(text = FakeCopy.MY_ENTRY, submit = WriteSubmit.Queued)
            else -> WriteState()
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

    companion object {
        private const val DEBUG_STEPS = 7
        private const val PAST_400 = 412
        private const val OVER_LIMIT = 512
    }
}
