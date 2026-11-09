package com.moyi.app.feature.support

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moyi.app.designsystem.MoyiTheme
import com.moyi.app.designsystem.component.Field
import com.moyi.app.designsystem.component.FieldType
import com.moyi.app.designsystem.component.InlineError
import com.moyi.app.designsystem.component.MoyiScaffold
import com.moyi.app.designsystem.component.MoyiText
import com.moyi.app.designsystem.component.PrimaryAction
import com.moyi.app.designsystem.component.PrimaryActionState
import com.moyi.app.designsystem.component.ScreenHeader
import com.moyi.app.designsystem.component.StateBlock
import com.moyi.app.designsystem.component.TextTone
import com.moyi.app.designsystem.component.rememberAutoFocus
import com.moyi.app.designsystem.presentation.ObserveAsEvents
import com.moyi.app.designsystem.presentation.UiText
import com.moyi.app.feature.support.resources.Res
import com.moyi.app.feature.support.resources.support_back_generic
import com.moyi.app.feature.support.resources.support_back_settings
import com.moyi.app.feature.support.resources.support_eyebrow
import com.moyi.app.feature.support.resources.support_failed_error
import com.moyi.app.feature.support.resources.support_heading
import com.moyi.app.feature.support.resources.support_message_label
import com.moyi.app.feature.support.resources.support_paused_heading
import com.moyi.app.feature.support.resources.support_paused_line
import com.moyi.app.feature.support.resources.support_privacy
import com.moyi.app.feature.support.resources.support_retry
import com.moyi.app.feature.support.resources.support_send
import com.moyi.app.feature.support.resources.support_sending
import com.moyi.app.feature.support.resources.support_sent_heading
import com.moyi.app.feature.support.resources.support_sent_line
import com.moyi.app.feature.support.resources.support_subject_label
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** Holds the ViewModel and answers its one event: close, back to where Get help was opened. */
@Composable
fun SupportRoot(
    origin: SupportOrigin,
    onClose: () -> Unit,
    viewModel: SupportViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            SupportEvent.Close -> onClose()
        }
    }
    SupportScreen(state = state, origin = origin, onAction = viewModel::onAction, onBack = onClose)
}

/**
 * Support (strings-proposed.md §9; Figma's Support section was not read, so the layout is
 * the Bond name editor's: back, header, fields, the action at the foot).
 *
 * - **Writing**: back, eyebrow Support and heading Get help, the privacy line, a `Field ·
 *   Text` Subject and a `Field · Multiline` Message, and Send. Sending is the action
 *   saying so (no spinner). A send that did not go is an InlineError 8 above the action,
 *   which then reads Try again; the words stay.
 * - **Sent** and **Paused**: an acknowledgement, no header and no back (SKILL.md: its one
 *   action already returns the reader), a StateBlock centred with that action.
 */
@Composable
fun SupportScreen(
    state: SupportState,
    origin: SupportOrigin,
    onAction: (SupportAction) -> Unit,
    onBack: () -> Unit,
) {
    when (state.step) {
        SupportStep.Writing -> SupportForm(state, onAction, onBack)
        SupportStep.Sent -> Acknowledgement(
            heading = stringResource(Res.string.support_sent_heading),
            line = stringResource(Res.string.support_sent_line),
            origin = origin,
            onAction = onAction,
        )
        SupportStep.Paused -> Acknowledgement(
            heading = stringResource(Res.string.support_paused_heading),
            line = stringResource(Res.string.support_paused_line),
            origin = origin,
            onAction = onAction,
        )
    }
}

@Composable
private fun SupportForm(state: SupportState, onAction: (SupportAction) -> Unit, onBack: () -> Unit) {
    val spacing = MoyiTheme.spacing
    val send = { onAction(SupportAction.OnSendClick) }
    val focus = LocalFocusManager.current
    MoyiScaffold(
        onBack = onBack,
        action = {
            state.sendError?.let {
                InlineError(it.asString())
                Spacer(Modifier.height(spacing.sm))
            }
            PrimaryAction(
                text = stringResource(if (state.sendError != null) Res.string.support_retry else Res.string.support_send),
                onClick = send,
                state = if (state.sending) {
                    PrimaryActionState.Sending(stringResource(Res.string.support_sending))
                } else {
                    PrimaryActionState.Idle
                },
            )
        },
    ) {
        ScreenHeader(eyebrow = stringResource(Res.string.support_eyebrow), heading = stringResource(Res.string.support_heading))
        Spacer(Modifier.height(spacing.md))
        MoyiText(stringResource(Res.string.support_privacy), tone = TextTone.Muted)
        Spacer(Modifier.height(spacing.xl))
        val autoFocus = rememberAutoFocus()
        Field(
            modifier = Modifier.focusRequester(autoFocus),
            value = state.subject,
            onValueChange = { onAction(SupportAction.OnSubjectChange(it)) },
            label = stringResource(Res.string.support_subject_label),
            error = state.subjectError?.asString(),
            enabled = !state.sending,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
        )
        Spacer(Modifier.height(spacing.lg))
        Field(
            value = state.body,
            onValueChange = { onAction(SupportAction.OnBodyChange(it)) },
            label = stringResource(Res.string.support_message_label),
            type = FieldType.Multiline,
            error = state.bodyError?.asString(),
            enabled = !state.sending,
        )
    }
}

@Composable
private fun Acknowledgement(heading: String, line: String, origin: SupportOrigin, onAction: (SupportAction) -> Unit) {
    MoyiScaffold(contentArrangement = Arrangement.Center) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            StateBlock(
                heading = heading,
                line = line,
                actionLabel = stringResource(
                    when (origin) {
                        SupportOrigin.Settings -> Res.string.support_back_settings
                        SupportOrigin.Error -> Res.string.support_back_generic
                    },
                ),
                onAction = { onAction(SupportAction.OnDoneClick) },
            )
        }
    }
}

@Preview
@Composable
private fun SupportFormPreview() {
    MoyiTheme(darkTheme = true) {
        SupportScreen(SupportState(subject = "Reveal time", body = "The day opened an hour late."), SupportOrigin.Settings, onAction = {}, onBack = {})
    }
}

@Preview
@Composable
private fun SupportFailedLightPreview() {
    MoyiTheme(darkTheme = false) {
        SupportScreen(
            SupportState(subject = "Reveal time", body = "The day opened an hour late.", sendError = UiText.Resource(Res.string.support_failed_error)),
            SupportOrigin.Settings,
            onAction = {},
            onBack = {},
        )
    }
}

@Preview
@Composable
private fun SupportSentPreview() {
    MoyiTheme(darkTheme = true) { SupportScreen(SupportState(step = SupportStep.Sent), SupportOrigin.Settings, onAction = {}, onBack = {}) }
}

@Preview
@Composable
private fun SupportPausedPreview() {
    MoyiTheme(darkTheme = true) { SupportScreen(SupportState(step = SupportStep.Paused), SupportOrigin.Error, onAction = {}, onBack = {}) }
}
