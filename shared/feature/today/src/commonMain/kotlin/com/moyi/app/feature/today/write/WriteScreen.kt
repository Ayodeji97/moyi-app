package com.moyi.app.feature.today.write

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moyi.app.designsystem.MoyiTheme
import com.moyi.app.designsystem.component.AttachedPhoto
import com.moyi.app.designsystem.component.AttachedVoice
import com.moyi.app.designsystem.component.ComposeField
import com.moyi.app.designsystem.component.ConfirmDialog
import com.moyi.app.designsystem.component.InlineError
import com.moyi.app.designsystem.component.MoyiScaffold
import com.moyi.app.designsystem.component.PrimaryAction
import com.moyi.app.designsystem.component.PrimaryActionState
import com.moyi.app.designsystem.component.SentPhoto
import com.moyi.app.designsystem.component.SentVoice
import com.moyi.app.designsystem.platform.NoArrowBackHandler
import com.moyi.app.designsystem.platform.rememberPhotoPicker
import com.moyi.app.designsystem.platform.rememberVoiceRecorder
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.moyi.app.designsystem.presentation.ObserveAsEvents
import com.moyi.app.feature.today.common.debugLongPress
import com.moyi.app.feature.today.resources.Res
import com.moyi.app.feature.today.resources.write_announce_editing
import com.moyi.app.feature.today.resources.write_counter
import com.moyi.app.feature.today.resources.write_discard_confirm
import com.moyi.app.feature.today.resources.write_discard_keep
import com.moyi.app.feature.today.resources.write_discard_keep_draft
import com.moyi.app.feature.today.resources.write_discard_title
import com.moyi.app.feature.today.resources.write_discard_title_editing
import com.moyi.app.feature.today.resources.write_photo_refused
import com.moyi.app.feature.today.resources.write_photo_source_camera
import com.moyi.app.feature.today.resources.write_photo_source_cancel
import com.moyi.app.feature.today.resources.write_photo_source_library
import com.moyi.app.feature.today.resources.write_photo_source_title
import com.moyi.app.feature.today.resources.write_photo_source_title_replace
import com.moyi.app.feature.today.resources.write_photo_unreadable
import com.moyi.app.feature.today.resources.write_queued
import com.moyi.app.feature.today.resources.write_save
import com.moyi.app.feature.today.resources.write_save_failed
import com.moyi.app.feature.today.resources.write_save_failed_photo
import com.moyi.app.feature.today.resources.write_save_failed_photo_voice
import com.moyi.app.feature.today.resources.write_save_failed_voice
import com.moyi.app.feature.today.resources.write_saving
import com.moyi.app.feature.today.resources.write_send
import com.moyi.app.feature.today.resources.write_sending
import com.moyi.app.feature.today.resources.write_sending_photo
import com.moyi.app.feature.today.resources.write_sending_voice
import com.moyi.app.feature.today.resources.write_voice_failed
import com.moyi.app.feature.today.resources.write_voice_microphone_denied
import com.moyi.app.feature.today.resources.write_voice_refused
import com.moyi.app.feature.today.resources.write_try_again
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Write is a task (D10): Android Back asks to close it, exactly as the X does,
 * and the iOS edge swipe does nothing (no arrow, D3). While the discard question
 * is up, the dialog takes Back itself (Android: Keep writing).
 */
@Composable
fun WriteRoot(
    editing: Boolean,
    onClose: () -> Unit,
    debugTools: Boolean,
    viewModel: WriteViewModel = koinViewModel { parametersOf(editing) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val photoPicker = rememberPhotoPicker { pick -> viewModel.onAction(WriteAction.OnPhotoPicked(pick)) }
    // Held here, as the photo launchers are: leaving Write throws an unfinished recording away.
    val recorder = rememberVoiceRecorder { event -> viewModel.onAction(WriteAction.OnRecorderEvent(event)) }
    // The microphone's level, drawn by the live waveform and nowhere else: not screen state,
    // so it stays out of the ViewModel, and it is read only when the bars are drawn.
    val recordingLevels = recorder.levels.collectAsStateWithLifecycle()
    // The app going to the background ends a recording where it is, and keeps it.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { recorder.stop() }
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            WriteEvent.Close -> onClose()
            is WriteEvent.PickPhoto -> when (event.source) {
                PhotoSource.Library -> photoPicker.chooseFromLibrary()
                PhotoSource.Camera -> photoPicker.takePhoto()
            }
            WriteEvent.StartRecording -> recorder.start()
            WriteEvent.StopRecording -> recorder.stop()
            WriteEvent.CancelRecording -> recorder.cancel()
        }
    }
    NoArrowBackHandler { viewModel.onAction(WriteAction.OnCloseClick) }
    if (state.restoringDraft) {
        // A few milliseconds while the draft is read: the bare `surface` (states.md §0), no
        // spinner. The composer then opens with the words in it, focused, cursor at their end.
        Box(Modifier.fillMaxSize().background(MoyiTheme.colors.surface))
    } else {
        WriteScreen(
            state = state,
            onAction = viewModel::onAction,
            debugTools = debugTools,
            canTakePhoto = photoPicker.canTakePhoto,
            recordingLevels = { recordingLevels.value },
        )
    }
}

/**
 * The composer with the keyboard up (Figma 181:3): the header with the task's
 * close only (D10: an X on Android, Cancel on iOS, where 181:3 draws a back
 * arrow), no title (0 to 72); the ComposeField from y 96, filling the space to the action;
 * the action block riding on the keyboard. In save failed (384:229) the error line
 * sits between the field and the action, 8 above the button, and the field gives
 * up the room.
 *
 * @param debugTools PR #8 only: a long press beside the button steps the fake state.
 */
@Composable
fun WriteScreen(
    state: WriteState,
    onAction: (WriteAction) -> Unit,
    debugTools: Boolean = false,
    canTakePhoto: Boolean = false,
    /** The microphone's level while recording, read when the live waveform draws. */
    recordingLevels: () -> List<Float> = { emptyList() },
) {
    val focusRequester = remember { FocusRequester() }
    // The composer opens ready to type: the keyboard is up in every Write frame.
    LaunchedEffect(focusRequester) { focusRequester.requestFocus() }

    val editingAnnounce = stringResource(Res.string.write_announce_editing)
    MoyiScaffold(
        onClose = { onAction(WriteAction.OnCloseClick) },
        scrollContent = false,
        action = {
            errorLine(state)?.let { line ->
                InlineError(stringResource(line))
                Spacer(Modifier.height(MoyiTheme.spacing.sm))
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .debugLongPress(if (debugTools) ({ onAction(WriteAction.OnDebugCycle) }) else null),
                contentAlignment = Alignment.Center,
            ) {
                PrimaryAction(
                    text = stringResource(actionLabel(state)),
                    onClick = { onAction(WriteAction.OnSubmit) },
                    state = actionState(state),
                )
            }
        },
    ) {
        // Header bottom (72) + the scaffold's 16 + 8 = the field at y 96 (Figma 181:3).
        Spacer(Modifier.height(MoyiTheme.spacing.sm))
        ComposeField(
            value = state.text,
            onValueChange = { onAction(WriteAction.OnTextChange(it)) },
            prompt = state.prompt,
            onDismissPrompt = { onAction(WriteAction.OnDismissPrompt) },
            counter = if (state.showCounter) stringResource(Res.string.write_counter, state.characterCount, WriteState.MAX_CHARACTERS) else null,
            invalid = state.overLimit,
            // Never disabled or read-only while sending: either drops focus and the keyboard
            // (419:193 keeps it up). The ViewModel ignores edits in flight instead.
            // In Edit, the entry's own photo carries Replace on its row (§60), so the toolbar does not offer it twice.
            onAddPhoto = if (state.photosOffered && (state.photo != null || state.sentPhoto == null)) {
                { onAction(WriteAction.OnAddPhotoClick(canTakePhoto)) }
            } else {
                null
            },
            // One voice note per entry (FR-043): once one is recording, recorded or (in Edit) on the
            // entry, its own row offers what is next.
            onRecordVoice = if (state.voiceOffered && state.voice == null && state.sentVoice == null) ({ onAction(WriteAction.OnRecordVoiceClick) }) else null,
            focusRequester = focusRequester,
            // With the keyboard up they step back to one compact row (§66), so the words keep their room.
            attachment = if (state.hasAttachments) ({ compact -> Attachments(state, onAction, canTakePhoto, recordingLevels, compact) }) else null,
            modifier = Modifier
                .weight(1f)
                .then(if (state.editing) Modifier.semantics { paneTitle = editingAnnounce } else Modifier),
        )
        // The field ends 24 above the first thing in the action block (Figma 181:3:
        // field to 456, button at 480; 384:229: field to 424, error at 448): the
        // scaffold's 16 plus these 8.
        Spacer(Modifier.height(MoyiTheme.spacing.sm))
    }
    if (state.choosingPhotoSource) {
        // Interim (docs/missing-components.md §41): no frame draws the choice, so the platform dialog asks.
        // Titled for what the pick does: adds a photo, or replaces the one already there (§61).
        ConfirmDialog(
            title = stringResource(
                if (state.replacingPhoto) Res.string.write_photo_source_title_replace else Res.string.write_photo_source_title,
            ),
            confirmLabel = stringResource(Res.string.write_photo_source_library),
            dismissLabel = stringResource(Res.string.write_photo_source_cancel),
            onConfirm = { onAction(WriteAction.OnChooseFromLibrary) },
            onDismiss = { onAction(WriteAction.OnPhotoSourceDismiss) },
            alternativeLabel = stringResource(Res.string.write_photo_source_camera),
            onAlternative = { onAction(WriteAction.OnTakePhoto) },
        )
    }
    if (state.confirmingDiscard) {
        ConfirmDialog(
            title = stringResource(if (state.editing) Res.string.write_discard_title_editing else Res.string.write_discard_title),
            confirmLabel = stringResource(Res.string.write_discard_confirm),
            dismissLabel = stringResource(Res.string.write_discard_keep),
            onConfirm = { onAction(WriteAction.OnDiscardConfirm) },
            onDismiss = { onAction(WriteAction.OnDiscardDismiss) },
            destructive = true,
            alternativeLabel = stringResource(Res.string.write_discard_keep_draft),
            onAlternative = { onAction(WriteAction.OnKeepDraft) },
        )
    }
}

private val WriteState.hasAttachments: Boolean
    get() = photo != null || voice != null || sentPhoto != null || sentVoice != null

/**
 * What is attached, inside the box above its toolbar (interim, §41 and §44): the photo, then
 * the voice note. In Edit (§60) the entry's own photo and voice note stand in each slot until a
 * new one is picked or recorded; they offer Replace and Record again, never Remove (the
 * contract cannot take one off, docs/backend-contract-gaps.md row 41). Removing a new one
 * puts the entry's own back, and its control says so.
 *
 * Emitted as siblings for the composer's `AttachmentsLayout`, which stacks them, or, when
 * [compact] (the keyboard is up, §66), sets them side by side on one row.
 */
@Composable
private fun Attachments(
    state: WriteState,
    onAction: (WriteAction) -> Unit,
    canTakePhoto: Boolean,
    recordingLevels: () -> List<Float>,
    compact: Boolean,
) {
    // Not while they are being sent: what is on its way is what is shown.
    val sending = state.submit == WriteSubmit.Sending
    val photo = state.photo
    val sentPhoto = state.sentPhoto
    when {
        photo != null -> AttachedPhoto(
            photo = photo,
            onRemove = if (sending) null else ({ onAction(WriteAction.OnRemovePhoto) }),
            removeRestoresSent = sentPhoto != null,
            compact = compact,
        )
        sentPhoto != null -> SentPhoto(
            photo = sentPhoto,
            onReplace = if (sending || !state.photosOffered) null else ({ onAction(WriteAction.OnAddPhotoClick(canTakePhoto)) }),
            compact = compact,
        )
    }
    val voice = state.voice
    val sentVoice = state.sentVoice
    when {
        voice != null -> AttachedVoice(
            state = voice,
            onStop = { onAction(WriteAction.OnStopRecordingClick) },
            onDiscard = { onAction(WriteAction.OnDiscardRecordingClick) },
            onRecordAgain = if (sending) null else ({ onAction(WriteAction.OnRecordAgainClick) }),
            onRemove = if (sending) null else ({ onAction(WriteAction.OnRemoveVoice) }),
            recordingLevels = recordingLevels,
            removeRestoresSent = sentVoice != null,
            compact = compact,
        )
        sentVoice != null -> SentVoice(
            voice = sentVoice,
            onRecordAgain = if (sending || !state.voiceOffered) null else ({ onAction(WriteAction.OnRecordVoiceClick) }),
            compact = compact,
        )
    }
}

/** The one line above the action: the media's own problems first, then Save failed. */
private fun errorLine(state: WriteState): StringResource? = when {
    state.photoNotice == PhotoNotice.Refused -> Res.string.write_photo_refused
    state.voiceNotice == VoiceNotice.Refused -> Res.string.write_voice_refused
    state.photoNotice == PhotoNotice.Unreadable -> Res.string.write_photo_unreadable
    state.voiceNotice == VoiceNotice.MicrophoneDenied -> Res.string.write_voice_microphone_denied
    state.voiceNotice == VoiceNotice.CouldNotRecord -> Res.string.write_voice_failed
    state.submit == WriteSubmit.Failed -> when {
        state.photo != null && state.recordedVoice != null -> Res.string.write_save_failed_photo_voice
        state.photo != null -> Res.string.write_save_failed_photo
        state.recordedVoice != null -> Res.string.write_save_failed_voice
        else -> Res.string.write_save_failed
    }
    else -> null
}

private fun actionLabel(state: WriteState) = when {
    state.submit == WriteSubmit.Failed -> Res.string.write_try_again
    state.editing -> Res.string.write_save
    else -> Res.string.write_send
}

@Composable
private fun actionState(state: WriteState): PrimaryActionState = when (state.submit) {
    WriteSubmit.Sending -> PrimaryActionState.Sending(
        stringResource(
            when {
                // The media go first, in Edit as at Send (§60): the action says which is on its way.
                state.sendingPhoto -> Res.string.write_sending_photo
                state.sendingVoice -> Res.string.write_sending_voice
                state.editing -> Res.string.write_saving
                else -> Res.string.write_sending
            },
        ),
    )
    WriteSubmit.Queued -> PrimaryActionState.QueuedOffline(stringResource(Res.string.write_queued))
    // Over the limit or empty: not sendable (181:83's state was not read; Disabled is the reading).
    WriteSubmit.Idle, WriteSubmit.Failed -> if (state.canSubmit) PrimaryActionState.Idle else PrimaryActionState.Disabled
}

// ---- Previews ----

@Composable
private fun WritePreview(state: WriteState) {
    MoyiTheme(darkTheme = true) { WriteScreen(state = state, onAction = {}) }
}

private const val PREVIEW_TEXT = "The way you sat on the phone with me until I fell asleep."

@Preview @Composable private fun WriteEmptyPreview() = WritePreview(WriteState())

@Preview @Composable private fun WritePast400Preview() = WritePreview(WriteState(text = "a ".repeat(206)))

@Preview @Composable private fun WriteOverLimitPreview() = WritePreview(WriteState(text = "a ".repeat(256)))

@Preview @Composable private fun WriteSendingPreview() = WritePreview(WriteState(text = PREVIEW_TEXT, submit = WriteSubmit.Sending))

@Preview @Composable private fun WriteEditingPreview() = WritePreview(WriteState(text = PREVIEW_TEXT, prompt = null, editing = true))

@Preview @Composable private fun WriteSaveFailedPreview() = WritePreview(WriteState(text = PREVIEW_TEXT, submit = WriteSubmit.Failed))

@Preview @Composable private fun WriteDiscardPreview() = WritePreview(WriteState(text = PREVIEW_TEXT, confirmingDiscard = true))

@Preview @Composable private fun WriteQueuedPreview() = WritePreview(WriteState(text = PREVIEW_TEXT, submit = WriteSubmit.Queued))
