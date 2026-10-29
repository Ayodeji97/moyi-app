package com.moyi.app.feature.today.write

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
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
import com.moyi.app.designsystem.component.ComposeField
import com.moyi.app.designsystem.component.InlineError
import com.moyi.app.designsystem.component.MoyiScaffold
import com.moyi.app.designsystem.component.PrimaryAction
import com.moyi.app.designsystem.component.PrimaryActionState
import com.moyi.app.designsystem.presentation.ObserveAsEvents
import com.moyi.app.feature.today.common.debugLongPress
import com.moyi.app.feature.today.resources.Res
import com.moyi.app.feature.today.resources.write_announce_editing
import com.moyi.app.feature.today.resources.write_counter
import com.moyi.app.feature.today.resources.write_queued
import com.moyi.app.feature.today.resources.write_save
import com.moyi.app.feature.today.resources.write_save_failed
import com.moyi.app.feature.today.resources.write_saving
import com.moyi.app.feature.today.resources.write_send
import com.moyi.app.feature.today.resources.write_sending
import com.moyi.app.feature.today.resources.write_try_again
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun WriteRoot(
    editing: Boolean,
    onNavigateBack: () -> Unit,
    debugTools: Boolean,
    viewModel: WriteViewModel = koinViewModel { parametersOf(editing) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            WriteEvent.NavigateBack -> onNavigateBack()
        }
    }
    WriteScreen(state = state, onAction = viewModel::onAction, debugTools = debugTools)
}

/**
 * The composer with the keyboard up (Figma 181:3): the header with back only, no
 * title (0 to 72); the ComposeField from y 96, filling the space to the action;
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
) {
    val focusRequester = remember { FocusRequester() }
    // The composer opens ready to type: the keyboard is up in every Write frame.
    LaunchedEffect(focusRequester) { focusRequester.requestFocus() }

    val editingAnnounce = stringResource(Res.string.write_announce_editing)
    MoyiScaffold(
        onBack = { onAction(WriteAction.OnBackClick) },
        scrollContent = false,
        action = {
            if (state.submit == WriteSubmit.Failed) {
                InlineError(stringResource(Res.string.write_save_failed))
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
            onAddPhoto = { onAction(WriteAction.OnAddPhotoClick) },
            onRecordVoice = { onAction(WriteAction.OnRecordVoiceClick) },
            focusRequester = focusRequester,
            modifier = Modifier
                .weight(1f)
                .then(if (state.editing) Modifier.semantics { paneTitle = editingAnnounce } else Modifier),
        )
        // The field ends 24 above the first thing in the action block (Figma 181:3:
        // field to 456, button at 480; 384:229: field to 424, error at 448): the
        // scaffold's 16 plus these 8.
        Spacer(Modifier.height(MoyiTheme.spacing.sm))
    }
}

private fun actionLabel(state: WriteState) = when {
    state.submit == WriteSubmit.Failed -> Res.string.write_try_again
    state.editing -> Res.string.write_save
    else -> Res.string.write_send
}

@Composable
private fun actionState(state: WriteState): PrimaryActionState = when (state.submit) {
    WriteSubmit.Sending -> PrimaryActionState.Sending(stringResource(if (state.editing) Res.string.write_saving else Res.string.write_sending))
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

@Preview @Composable private fun WriteQueuedPreview() = WritePreview(WriteState(text = PREVIEW_TEXT, submit = WriteSubmit.Queued))
