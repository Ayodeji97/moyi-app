package com.moyi.app.feature.today.write

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moyi.app.designsystem.component.AttachedVoiceState
import com.moyi.app.designsystem.platform.PhotoPick
import com.moyi.app.designsystem.platform.PickedPhoto
import com.moyi.app.designsystem.platform.RecordedVoice
import com.moyi.app.designsystem.platform.RecorderEvent
import com.moyi.app.designsystem.platform.VoiceRecording
import com.moyi.app.domain.media.MediaBytes
import com.moyi.app.domain.media.MediaKind
import com.moyi.app.domain.media.MediaUploader
import com.moyi.app.domain.media.UploadFailure
import com.moyi.app.domain.media.UploadOutcome
import com.moyi.app.domain.media.UploadProgress
import com.moyi.app.domain.today.Draft
import com.moyi.app.domain.today.DraftKind
import com.moyi.app.domain.today.DraftStore
import com.moyi.app.feature.today.common.DailyLoop
import com.moyi.app.feature.today.common.DailyLoopState
import com.moyi.app.feature.today.common.FakeCopy
import com.moyi.app.feature.today.common.WriteResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * The composer, over the shared [DailyLoop]: it opens on the day Today loaded (its
 * prompt, or my words when editing) and sends through the loop, which refreshes Today.
 *
 * Sending waits for the server (PrimaryAction Sending, no spinner) and closes on its answer;
 * a refusal keeps the words and shows Save failed (384:229). A new entry that gets **no**
 * answer (offline, a timeout, the server's own failure) is queued on the device instead
 * (doc 13 §5): the composer closes as on success, the draft goes (the outbox holds the
 * words now), and Today shows it as Queued. An edit is never queued: it shows Save failed.
 *
 * **Drafts** (doc 13 §5: local first, always; states.md §4: saved silently). The words are
 * written to [drafts] [DRAFT_PAUSE] after the last keystroke, and at the Send tap, so they
 * survive closing the app, a force-quit or a crash, and come back when Write reopens. A new
 * entry and an edit keep separate drafts ([DraftKind]). A send or save that succeeds deletes
 * the draft; one that fails keeps it. Discard deletes it; Keep as a draft, the discard
 * question's third answer, closes and keeps it. Nothing about a draft is shown or
 * said: no toast, no "saved".
 *
 * A new-entry draft is restored whichever Bond-day it was written on: dropping unsent words
 * is the harm BR-3a exists to prevent. The server files it by the `intendedAt` of the Send
 * tap, not by the day the draft was begun. An edit draft is restored only into
 * the entry it was written for: put into another entry, its Save would change the wrong words.
 *
 * **A photo** (docs/missing-components.md §40) is prepared on the phone when picked and
 * uploaded at the Send tap, before the words: intent, a PUT straight to storage, complete,
 * then a wait until the server has processed it ([MediaUploader]). The action reads
 * "Sending your photo" meanwhile, then "Sending". Only a processed photo can be attached,
 * so the entry is sent with its id after that. A photo entry is never queued offline: a
 * failure anywhere keeps the words and the photo, and Try again resumes where it stopped
 * (bytes already in storage are not sent twice). The photo is not part of the draft.
 *
 * **A voice note** (§44) is recorded by the platform recorder the screen holds: this asks it
 * to start and stop ([WriteEvent.StartRecording], [WriteEvent.StopRecording]) and runs the
 * clock, stopping it at [VoiceRecording.MAX_DURATION_MS] (the recorder stops itself there
 * too). An interruption ends the recording where it is and keeps it. It uploads at Send
 * the same way, after the photo ("Sending your voice note"), and goes as `voiceMediaId`. An
 * entry may carry both. Like a photo, it is never queued offline and not part of the draft.
 *
 * **Editing** (§60) shows the photo and voice note already on the entry ([WriteState.sentPhoto],
 * [WriteState.sentVoice]). A new photo or recording uploads at Save exactly as at Send and
 * goes in the PATCH in place of the entry's; removing the new one before Save keeps the
 * entry's. Nothing takes one off: the contract cannot (docs/backend-contract-gaps.md row 41).
 * An edit is never queued, with or without media: it needs a connection, and without one it
 * is Save failed with everything kept.
 *
 * @param editing true when reached from "Edit what you wrote" (before the reveal).
 * @param drafts where the unsent words are kept, on this device only.
 * @param uploader sends a photo or a voice note; null offers neither (the debug preview).
 * @param newIdempotencyKey mints the `Idempotency-Key` at the tap; tests pass a counter.
 * @param clock reads the instant of the Send tap, sent as `intendedAt`; tests pass a fixed one.
 */
class WriteViewModel(
    private val editing: Boolean,
    private val loop: DailyLoop,
    private val drafts: DraftStore,
    private val uploader: MediaUploader? = null,
    private val newIdempotencyKey: () -> String = ::randomIdempotencyKey,
    private val clock: Clock = Clock.System,
) : ViewModel() {

    private val day = (loop.state.value as? DailyLoopState.Ready)?.day

    /** Today's prompt, sent with the entry unless the person dismissed it. */
    private val promptId: String? = day?.prompt?.id

    private val draftKind = if (editing) DraftKind.Edit else DraftKind.NewEntry

    private val _state = MutableStateFlow(
        if (editing) {
            day?.myEntry.orEmpty().let { mine ->
                WriteState(text = mine, prompt = null, editing = true, originalText = mine, sentPhoto = day?.myPhoto, sentVoice = day?.myVoice)
            }
        } else {
            WriteState(prompt = day?.prompt?.text)
        }.copy(
            restoringDraft = day != null,
            photosOffered = uploader != null,
            voiceOffered = uploader != null,
        ),
    )
    val state: StateFlow<WriteState> = _state.asStateFlow()

    private val _events = Channel<WriteEvent>()
    val events = _events.receiveAsFlow()

    /**
     * The key of the send that has not yet succeeded, and everything it carried: the words
     * and the prompt id actually sent. A retry of the identical payload is the same send, so
     * it reuses the key: if the first attempt reached the server and only its answer was
     * lost, the server replays it instead of writing twice (doc 06 §1). Any change, the words
     * or a prompt dismissed since, is a different send with a new key, because the contract
     * answers the same key with a different body with 422. After a lost success that new key
     * meets 409 ENTRY_ALREADY_EXISTS, and the loop reloads the day, so Today shows the entry.
     *
     * The key's `intendedAt` is part of the same body, so it is taken with the key and kept
     * with it: a retry of the same words sends the instant of the first tap, never a new one.
     */
    private var pending: Pending? = null

    private var debugStep = 0

    /** The draft write waiting for the keystroke pause; replaced by every keystroke. */
    private var draftSave: Job? = null

    /** How far the attached photo's upload got: a retry resumes from here. Reset with every new photo. */
    private var upload: UploadProgress = UploadProgress.None

    /** How far the recorded voice note's upload got. Reset with every new recording. */
    private var voiceUpload: UploadProgress = UploadProgress.None

    /** Counts the recording's time; cancelled when it ends. */
    private var recordingClock: Job? = null

    init {
        if (day != null) {
            viewModelScope.launch {
                val draft = quietly { drafts.read(day.bondId, draftKind) }?.takeIf { it.belongsHere() }
                _state.update { state ->
                    if (draft == null) state.copy(restoringDraft = false) else state.copy(text = draft.text, restoringDraft = false)
                }
            }
        }
    }

    fun onAction(action: WriteAction) {
        when (action) {
            is WriteAction.OnTextChange -> {
                // The words being sent are the words on screen: no edits in flight.
                if (_state.value.submit == WriteSubmit.Sending || _state.value.restoringDraft) return
                _state.update { it.copy(text = action.text, submit = it.submit.afterEdit()) }
                saveDraftAfterPause()
            }
            // A dismissed prompt sends no promptId with the entry (submit, below).
            WriteAction.OnDismissPrompt -> _state.update { it.copy(prompt = null) }
            WriteAction.OnSubmit -> submit()
            WriteAction.OnCloseClick -> requestClose()
            WriteAction.OnDiscardConfirm -> {
                _state.update { it.copy(confirmingDiscard = false) }
                // Discarding is the person's choice (D10): the draft goes with the words.
                viewModelScope.launch {
                    discardUploads()
                    deleteDraft()
                    _events.send(WriteEvent.Close)
                }
            }
            WriteAction.OnDiscardDismiss -> _state.update { it.copy(confirmingDiscard = false) }
            WriteAction.OnKeepDraft -> {
                _state.update { it.copy(confirmingDiscard = false) }
                // Keeping is the other choice: the words on screen are written now (not after
                // the pause, which closing would cancel), so they are there when Write reopens.
                close()
            }
            is WriteAction.OnAddPhotoClick -> when {
                !_state.value.photosOffered || _state.value.submit == WriteSubmit.Sending -> Unit
                action.canTakePhoto -> _state.update { it.copy(choosingPhotoSource = true) }
                else -> pickPhoto(PhotoSource.Library)
            }
            WriteAction.OnChooseFromLibrary -> pickPhoto(PhotoSource.Library)
            WriteAction.OnTakePhoto -> pickPhoto(PhotoSource.Camera)
            WriteAction.OnPhotoSourceDismiss -> _state.update { it.copy(choosingPhotoSource = false) }
            is WriteAction.OnPhotoPicked -> onPhotoPicked(action.pick)
            WriteAction.OnRemovePhoto -> {
                if (_state.value.submit == WriteSubmit.Sending) return
                replacePhoto(null)
            }
            WriteAction.OnRecordVoiceClick -> startRecording()
            WriteAction.OnStopRecordingClick -> if (_state.value.recording) send(WriteEvent.StopRecording)
            WriteAction.OnDiscardRecordingClick -> discardRecording()
            WriteAction.OnRecordAgainClick -> {
                if (_state.value.submit == WriteSubmit.Sending || _state.value.recordedVoice == null) return
                replaceVoice(null)
                startRecording()
            }
            WriteAction.OnRemoveVoice -> {
                if (_state.value.submit == WriteSubmit.Sending || _state.value.recording) return
                replaceVoice(null)
            }
            is WriteAction.OnRecorderEvent -> onRecorderEvent(action.event)
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
        // In Edit, a new photo or voice note replaces the entry's (§60); none keeps the entry's.
        val photo = current.photo
        val voice = current.recordedVoice?.voice
        // Taken at the tap, before any upload: the instant the person sent it.
        val tap = tapInstant()
        _state.update {
            it.copy(
                submit = WriteSubmit.Sending,
                sendingPhoto = photo != null,
                sendingVoice = photo == null && voice != null,
                photoNotice = null,
                voiceNotice = null,
            )
        }
        viewModelScope.launch {
            // Kept before it leaves: if the app dies while the send is in flight, the words are still here.
            saveDraftNow()
            val imageMediaId = if (photo == null) null else sendPhoto(photo) ?: return@launch
            _state.update { it.copy(sendingPhoto = false, sendingVoice = voice != null) }
            val voiceMediaId = if (voice == null) null else sendVoice(voice) ?: return@launch
            _state.update { it.copy(sendingVoice = false) }
            // An edit (PATCH) sends no prompt id; a new entry sends it unless dismissed.
            val payload = Payload(current.text, promptId.takeIf { !current.editing && current.prompt != null }, imageMediaId, voiceMediaId)
            val reused = pending?.takeIf { it.payload == payload }
            val key = reused?.key ?: newIdempotencyKey()
            val intendedAt = reused?.intendedAt ?: tap
            pending = Pending(payload, key, intendedAt)
            val result = if (current.editing) {
                // By name: absent media keep the entry's, so only a new upload is sent.
                loop.edit(payload.text, key, imageMediaId = imageMediaId, voiceMediaId = voiceMediaId)
            } else {
                loop.submit(payload.text, payload.promptId, key, intendedAt, imageMediaId, voiceMediaId)
            }
            when (result) {
                // Queued: the outbox holds the words now, with this key, and sends them later.
                WriteResult.Saved, WriteResult.Queued -> {
                    pending = null
                    // Attached to the entry now: nothing left to discard.
                    upload = UploadProgress.None
                    voiceUpload = UploadProgress.None
                    // The server (or the outbox) has the words: a draft left behind would come back as a second entry.
                    deleteDraft()
                    // The state stays Sending until the composer closes: the words are saved, and a
                    // close tapped in the moment before it goes must not ask to discard them (D10).
                    _events.send(WriteEvent.Close)
                }
                // The draft stays: the words are still unsent (states.md §10, Save failed).
                WriteResult.Failed -> _state.update { it.copy(submit = WriteSubmit.Failed) }
                // The server would not attach it: the next photo, or none, starts a new upload.
                WriteResult.PhotoRefused -> {
                    upload = UploadProgress.None
                    _state.update { it.copy(submit = WriteSubmit.Idle, photoNotice = PhotoNotice.Refused) }
                }
                WriteResult.VoiceRefused -> {
                    voiceUpload = UploadProgress.None
                    _state.update { it.copy(submit = WriteSubmit.Idle, voiceNotice = VoiceNotice.Refused) }
                }
            }
        }
    }

    /**
     * Uploads [photo], resuming [upload]; its media id once processed, or null after
     * setting the failure, when the words must not be sent yet.
     */
    private suspend fun sendPhoto(photo: PickedPhoto): String? = sendMedia(
        media = MediaBytes(MediaKind.Image, photo.contentType, photo.bytes),
        from = upload,
        // A newer photo may have been attached meanwhile: only this photo's progress is kept.
        stillAttached = { _state.value.photo === photo },
        keep = { upload = it },
        refused = { it.copy(photoNotice = PhotoNotice.Refused) },
    )

    /** [sendPhoto], for the voice note, resuming [voiceUpload]. */
    private suspend fun sendVoice(voice: RecordedVoice): String? = sendMedia(
        media = MediaBytes(MediaKind.Voice, voice.contentType, voice.bytes),
        from = voiceUpload,
        stillAttached = { _state.value.recordedVoice?.voice === voice },
        keep = { voiceUpload = it },
        refused = { it.copy(voiceNotice = VoiceNotice.Refused) },
    )

    /**
     * One upload through [MediaUploader], the same for a photo and a voice note: its media id
     * once processed, or null after setting the failure (Save failed, or [refused]'s notice),
     * when the words must not be sent yet.
     */
    private suspend fun sendMedia(
        media: MediaBytes,
        from: UploadProgress,
        stillAttached: () -> Boolean,
        keep: (UploadProgress) -> Unit,
        refused: (WriteState) -> WriteState,
    ): String? {
        val notSending: (WriteState) -> WriteState = { it.copy(sendingPhoto = false, sendingVoice = false) }
        val uploader = uploader ?: return null.also { _state.update { notSending(it).copy(submit = WriteSubmit.Failed) } }
        val outcome = uploader.send(media, from)
        if (!stillAttached()) return null
        keep(outcome.progress)
        return when (outcome) {
            is UploadOutcome.Ready -> outcome.mediaId
            is UploadOutcome.Failed -> {
                _state.update {
                    when (outcome.failure) {
                        UploadFailure.Retryable -> notSending(it).copy(submit = WriteSubmit.Failed)
                        UploadFailure.Refused -> refused(notSending(it).copy(submit = WriteSubmit.Idle))
                    }
                }
                null
            }
        }
    }

    /** Asks the screen's recorder to start; the state changes only once it has ([RecorderEvent.Started]). */
    private fun startRecording() {
        val current = _state.value
        if (!current.voiceOffered || current.submit == WriteSubmit.Sending || current.voice != null) return
        _state.update { it.copy(voiceNotice = it.voiceNotice.takeIf { notice -> notice == VoiceNotice.Refused }) }
        send(WriteEvent.StartRecording)
    }

    /**
     * Throws the recording in progress away, at once and with no question: it was never
     * sent, so nothing is lost that the person did not just choose to lose (states.md §4,
     * "confirm the irreversible, only"). The voice slot is empty again, ready to record.
     */
    private fun discardRecording() {
        if (!_state.value.recording) return
        recordingClock?.cancel()
        _state.update { it.copy(voice = null, submit = it.submit.afterEdit()) }
        send(WriteEvent.CancelRecording)
    }

    private fun onRecorderEvent(event: RecorderEvent) {
        when (event) {
            RecorderEvent.Started -> {
                if (_state.value.voice != null) return
                _state.update { it.copy(voice = AttachedVoiceState.Recording(0), voiceNotice = null, submit = it.submit.afterEdit()) }
                runRecordingClock()
            }
            is RecorderEvent.Finished -> {
                // Only a recording in progress becomes the voice note (not a late report after removal).
                if (!_state.value.recording) return
                recordingClock?.cancel()
                replaceVoice(AttachedVoiceState.Recorded(event.voice))
            }
            RecorderEvent.PermissionDenied -> {
                recordingClock?.cancel()
                _state.update { it.copy(voice = null, voiceNotice = VoiceNotice.MicrophoneDenied) }
            }
            RecorderEvent.Failed -> {
                recordingClock?.cancel()
                _state.update { it.copy(voice = null, voiceNotice = VoiceNotice.CouldNotRecord) }
            }
        }
    }

    /**
     * The time on screen while recording. At the limit it asks the recorder to stop; the
     * recorder also stops itself there, whichever is first, and reports what it kept.
     */
    private fun runRecordingClock() {
        recordingClock?.cancel()
        recordingClock = viewModelScope.launch {
            var elapsed = 0L
            while (elapsed < VoiceRecording.MAX_DURATION_MS) {
                delay(RECORDING_TICK)
                elapsed = (elapsed + RECORDING_TICK).coerceAtMost(VoiceRecording.MAX_DURATION_MS)
                _state.update { state ->
                    if (state.recording) state.copy(voice = AttachedVoiceState.Recording(elapsed)) else state
                }
            }
            _events.send(WriteEvent.StopRecording)
        }
    }

    /** Attaches [voice] (or none) in place of the one attached, whose upload, if any, is deleted. */
    private fun replaceVoice(voice: AttachedVoiceState?) {
        val previous = voiceUpload
        voiceUpload = UploadProgress.None
        _state.update {
            it.copy(
                voice = voice,
                voiceNotice = it.voiceNotice.takeIf { notice -> notice != VoiceNotice.Refused && voice == null },
                submit = it.submit.afterEdit(),
            )
        }
        if (previous.mediaId != null) viewModelScope.launch { quietly { uploader?.discard(previous) } }
    }

    private fun send(event: WriteEvent) {
        viewModelScope.launch { _events.send(event) }
    }

    private fun pickPhoto(source: PhotoSource) {
        _state.update { it.copy(choosingPhotoSource = false) }
        if (!_state.value.photosOffered || _state.value.submit == WriteSubmit.Sending) return
        viewModelScope.launch { _events.send(WriteEvent.PickPhoto(source)) }
    }

    private fun onPhotoPicked(pick: PhotoPick) {
        if (!_state.value.photosOffered || _state.value.submit == WriteSubmit.Sending) return
        when (pick) {
            is PhotoPick.Picked -> replacePhoto(pick.photo)
            // Whatever was attached stays; the line says this one could not be opened.
            PhotoPick.Unreadable -> _state.update { it.copy(photoNotice = PhotoNotice.Unreadable) }
        }
    }

    /** Attaches [photo] (or none) in place of the one attached, whose upload, if any, is deleted. */
    private fun replacePhoto(photo: PickedPhoto?) {
        val previous = upload
        upload = UploadProgress.None
        _state.update { it.copy(photo = photo, photoNotice = null, submit = it.submit.afterEdit()) }
        if (previous.mediaId != null) viewModelScope.launch { quietly { uploader?.discard(previous) } }
    }

    /** Deletes what the photo and the voice note uploaded, when neither will be sent. */
    private suspend fun discardUploads() {
        val previous = listOf(upload, voiceUpload)
        upload = UploadProgress.None
        voiceUpload = UploadProgress.None
        previous.filter { it.mediaId != null }.forEach { quietly { uploader?.discard(it) } }
    }

    /**
     * D10: closing asks first only when it would lose words. While the words are
     * being sent, closing waits: the send decides, and it closes on its own.
     */
    private fun requestClose() {
        val current = _state.value
        // Recording stops before the question: what was said so far is kept, and is what Discard throws away.
        if (current.recording) send(WriteEvent.StopRecording)
        when {
            current.submit == WriteSubmit.Sending || current.confirmingDiscard -> Unit
            current.wouldLoseWords -> _state.update { it.copy(confirmingDiscard = true) }
            else -> close()
        }
    }

    /** Closing loses nothing here (blank, or an edit that changed nothing), so the draft is settled first. */
    private fun close() {
        viewModelScope.launch {
            // The photo and voice note are not part of the draft (§40, §44): an upload they left is deleted, not orphaned.
            discardUploads()
            saveDraftNow()
            _events.send(WriteEvent.Close)
        }
    }

    /** Debounce: each keystroke replaces the waiting write, so it lands only once typing pauses. */
    private fun saveDraftAfterPause() {
        draftSave?.cancel()
        draftSave = viewModelScope.launch {
            delay(DRAFT_PAUSE)
            writeDraft()
        }
    }

    /** Writes what is on screen now, after any write still waiting for its pause. */
    private suspend fun saveDraftNow() {
        draftSave?.cancelAndJoin()
        draftSave = null
        writeDraft()
    }

    /**
     * Cancels the waiting write and waits for it, so it cannot land after the delete and
     * bring the draft back.
     */
    private suspend fun deleteDraft() {
        draftSave?.cancelAndJoin()
        draftSave = null
        val day = day ?: return
        quietly { drafts.delete(day.bondId, draftKind) }
    }

    /**
     * Keeps the words worth keeping: not blank, and for an edit, not the words already
     * saved. Anything else deletes the draft, so emptying the field leaves none behind.
     */
    private suspend fun writeDraft() {
        val day = day ?: return
        if (debugStep != 0) return // The debug frames are FakeCopy, never a draft.
        val current = _state.value
        quietly {
            if (current.text.isNotBlank() && current.text != current.originalText) {
                drafts.write(Draft(day.bondId, draftKind, current.text, day.date.toString(), day.myEntryId.takeIf { editing }))
            } else {
                drafts.delete(day.bondId, draftKind)
            }
        }
    }

    /** A new-entry draft belongs to any day (see the class note); an edit draft only to its own entry. */
    private fun Draft.belongsHere(): Boolean = kind == DraftKind.NewEntry || entryId == day?.myEntryId

    /**
     * A draft that cannot be read or written must never stop the composer: the words are
     * still on screen and still sendable. Cancellation is not a failure and goes through.
     */
    private suspend fun <T> quietly(block: suspend () -> T): T? = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (ignored: Exception) {
        null
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

    /** The request body a key is bound to, the media ids included. Never prints the words. */
    private class Payload(
        val text: String,
        val promptId: String?,
        val imageMediaId: String? = null,
        val voiceMediaId: String? = null,
    ) {
        override fun equals(other: Any?): Boolean =
            other is Payload && other.text == text && other.promptId == promptId &&
                other.imageMediaId == imageMediaId && other.voiceMediaId == voiceMediaId

        override fun hashCode(): Int = listOf(text, promptId, imageMediaId, voiceMediaId).hashCode()

        override fun toString(): String = "Payload(promptId=$promptId, imageMediaId=$imageMediaId, voiceMediaId=$voiceMediaId)"
    }

    private class Pending(val payload: Payload, val key: String, val intendedAt: Instant)

    /**
     * The Send tap, in whole milliseconds: the outbox keeps milliseconds, and a retry must
     * send the very same body (doc 06 §1), so the first try must not carry more precision.
     */
    private fun tapInstant(): Instant = Instant.fromEpochMilliseconds(clock.now().toEpochMilliseconds())

    companion object {
        /**
         * How long typing must pause before the draft is written. A steady typist leaves
         * 150 to 300 ms between keys, so this writes once per pause, not per letter, and a
         * force-quit loses at most the last half second of words.
         */
        const val DRAFT_PAUSE = 500L

        /** How often the recording's clock moves: a seconds display turns on time. */
        const val RECORDING_TICK = 250L

        private const val DEBUG_STEPS = 7
        private const val PAST_400 = 412
        private const val OVER_LIMIT = 512
    }
}

/** A random (version 4) UUID: entry ids and keys must not leak when they were made (doc 06 §1). */
@OptIn(ExperimentalUuidApi::class)
internal fun randomIdempotencyKey(): String = Uuid.random().toString()
