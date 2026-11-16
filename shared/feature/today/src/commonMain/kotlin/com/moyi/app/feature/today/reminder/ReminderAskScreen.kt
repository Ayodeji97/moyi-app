package com.moyi.app.feature.today.reminder

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moyi.app.designsystem.MoyiTheme
import com.moyi.app.designsystem.component.Field
import com.moyi.app.designsystem.component.FieldType
import com.moyi.app.designsystem.component.InlineError
import com.moyi.app.designsystem.component.MoyiScaffold
import com.moyi.app.designsystem.component.MoyiText
import com.moyi.app.designsystem.component.PrimaryAction
import com.moyi.app.designsystem.component.PrimaryActionState
import com.moyi.app.designsystem.component.TextLink
import com.moyi.app.designsystem.component.TextRole
import com.moyi.app.designsystem.component.TextTone
import com.moyi.app.designsystem.component.TimeOfDayPicker
import com.moyi.app.designsystem.platform.rememberNotificationPermission
import com.moyi.app.designsystem.presentation.ObserveAsEvents
import com.moyi.app.domain.bond.MemberSettings
import com.moyi.app.domain.bond.TimeOfDay
import com.moyi.app.designsystem.platform.timeOfDayText
import com.moyi.app.feature.today.resources.Res
import com.moyi.app.feature.today.resources.reminder_ask_continue
import com.moyi.app.feature.today.resources.reminder_ask_denied
import com.moyi.app.feature.today.resources.reminder_ask_line
import com.moyi.app.feature.today.resources.reminder_ask_not_now
import com.moyi.app.feature.today.resources.reminder_ask_open_settings
import com.moyi.app.feature.today.resources.reminder_ask_picker_cancel
import com.moyi.app.feature.today.resources.reminder_ask_picker_confirm
import com.moyi.app.feature.today.resources.reminder_ask_preview_app
import com.moyi.app.feature.today.resources.reminder_ask_preview_body
import com.moyi.app.feature.today.resources.reminder_ask_preview_title
import com.moyi.app.feature.today.resources.reminder_ask_save_failed
import com.moyi.app.feature.today.resources.reminder_ask_saving
import com.moyi.app.feature.today.resources.reminder_ask_time_helper
import com.moyi.app.feature.today.resources.reminder_ask_time_label
import com.moyi.app.feature.today.resources.reminder_ask_time_unknown
import com.moyi.app.feature.today.resources.reminder_ask_title
import com.moyi.app.feature.today.resources.reminder_ask_turn_on
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * The reminder screen's root: the phone's permission controller, the same one Today's note
 * and Bond settings › Notifications use, read on every resume and asked when the ViewModel
 * says so.
 */
@Composable
fun ReminderAskRoot(
    bondId: String,
    onDone: () -> Unit,
    viewModel: ReminderAskViewModel = koinViewModel { parametersOf(bondId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val permission = rememberNotificationPermission()
    LifecycleResumeEffect(permission) {
        val check = scope.launch { viewModel.onAction(ReminderAskAction.OnPermissionChecked(permission.status())) }
        onPauseOrDispose { check.cancel() }
    }
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            ReminderAskEvent.AskPermission -> scope.launch {
                viewModel.onAction(ReminderAskAction.OnPermissionAnswered(permission.request()))
            }
            ReminderAskEvent.OpenPhoneSettings -> permission.openSettings()
            ReminderAskEvent.Done -> onDone()
        }
    }
    ReminderAskScreen(state = state, onAction = viewModel::onAction)
}

/**
 * A moment each day (mockup "Reminders" v4, owner rulings 2026-11-15). Not in Figma; built
 * from the design system's own parts: the title and a muted line, a preview of the real
 * reminder push, the reminder time as a [FieldType.Select] that opens the time picker
 * Notification settings uses, then Turn on reminders ([PrimaryAction], Saving while a changed
 * time saves) and a full-width Not now ([TextLink]).
 *
 * After the phone's prompt, when something is left to say (a no, or a time that did not
 * save), the action block says it and offers the way on to Today; after a no, Open phone
 * settings is the PrimaryAction, because it is the one thing that turns reminders on.
 */
@Composable
fun ReminderAskScreen(state: ReminderAskState, onAction: (ReminderAskAction) -> Unit) {
    val spacing = MoyiTheme.spacing
    val shownTime = state.time?.let { timeOfDayText(it.hour, it.minute) }
    MoyiScaffold(
        action = {
            if (state.phase == ReminderAskPhase.Answered) {
                Answered(state, onAction)
            } else {
                PrimaryAction(
                    text = stringResource(Res.string.reminder_ask_turn_on),
                    onClick = { onAction(ReminderAskAction.OnTurnOnClick) },
                    state = if (state.phase == ReminderAskPhase.Saving) {
                        PrimaryActionState.Sending(stringResource(Res.string.reminder_ask_saving))
                    } else {
                        PrimaryActionState.Idle
                    },
                )
                Spacer(Modifier.height(spacing.lg))
                TextLink(
                    text = stringResource(Res.string.reminder_ask_not_now),
                    onClick = { onAction(ReminderAskAction.OnNotNowClick) },
                    modifier = Modifier.fillMaxWidth(),
                    centered = true,
                    enabled = state.phase == ReminderAskPhase.Offering,
                )
            }
        },
    ) {
        MoyiText(stringResource(Res.string.reminder_ask_title), role = TextRole.Title)
        Spacer(Modifier.height(spacing.md))
        MoyiText(stringResource(Res.string.reminder_ask_line), tone = TextTone.Muted)
        Spacer(Modifier.height(spacing.lg))
        ReminderPreview(shownTime)
        Spacer(Modifier.height(spacing.lg))
        if (state.load == ReminderTimeLoad.Failed) {
            MoyiText(stringResource(Res.string.reminder_ask_time_unknown), tone = TextTone.Muted)
        } else {
            Field(
                value = shownTime.orEmpty(),
                onValueChange = {},
                label = stringResource(Res.string.reminder_ask_time_label),
                type = FieldType.Select,
                helper = stringResource(Res.string.reminder_ask_time_helper),
                enabled = state.canPick,
                onClick = { onAction(ReminderAskAction.OnTimeClick) },
            )
        }
    }
    if (state.picking) {
        val initial = state.time ?: return
        TimeOfDayPicker(
            title = stringResource(Res.string.reminder_ask_time_label),
            initialHour = initial.hour,
            initialMinute = initial.minute,
            confirmLabel = stringResource(Res.string.reminder_ask_picker_confirm),
            dismissLabel = stringResource(Res.string.reminder_ask_picker_cancel),
            onConfirm = { hour, minute -> onAction(ReminderAskAction.OnTimePicked(hour, minute)) },
            onDismiss = { onAction(ReminderAskAction.OnTimeDismiss) },
        )
    }
}

/** After the prompt: what is left to say, and the way on to Today. */
@Composable
private fun Answered(state: ReminderAskState, onAction: (ReminderAskAction) -> Unit) {
    val spacing = MoyiTheme.spacing
    val serverTime = state.member?.reminderTime
    if (state.saveFailed && serverTime != null) {
        InlineError(stringResource(Res.string.reminder_ask_save_failed, timeOfDayText(serverTime.hour, serverTime.minute)))
        Spacer(Modifier.height(spacing.sm))
    }
    if (state.denied) {
        MoyiText(
            text = stringResource(Res.string.reminder_ask_denied),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        )
        Spacer(Modifier.height(spacing.md))
        PrimaryAction(
            text = stringResource(Res.string.reminder_ask_open_settings),
            onClick = { onAction(ReminderAskAction.OnOpenPhoneSettingsClick) },
        )
        Spacer(Modifier.height(spacing.lg))
        TextLink(
            text = stringResource(Res.string.reminder_ask_continue),
            onClick = { onAction(ReminderAskAction.OnContinueClick) },
            modifier = Modifier.fillMaxWidth(),
            centered = true,
        )
    } else {
        PrimaryAction(
            text = stringResource(Res.string.reminder_ask_continue),
            onClick = { onAction(ReminderAskAction.OnContinueClick) },
        )
    }
}

/**
 * The real reminder push, as the phone shows it: the app's name and the time over the push's
 * own title and body (push_reminder_title and _body). The design system has no notification
 * card, so it is built from its primitives (surfaceRaised, a 1 outline hairline, radius 12,
 * 16 inside, 8 between) and adds no token. Decorative: the line above says what it shows, so
 * screen readers skip it.
 */
@Composable
private fun ReminderPreview(time: String?) {
    val colors = MoyiTheme.colors
    val shapes = MoyiTheme.shapes
    val spacing = MoyiTheme.spacing
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics {}
            .clip(shapes.radius)
            .background(colors.surfaceRaised)
            .border(shapes.hairline, colors.outline, shapes.radius)
            .padding(spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            MoyiText(stringResource(Res.string.reminder_ask_preview_app), role = TextRole.Label)
            if (time != null) MoyiText(time, role = TextRole.Label)
        }
        MoyiText(stringResource(Res.string.reminder_ask_preview_title))
        MoyiText(stringResource(Res.string.reminder_ask_preview_body), tone = TextTone.Muted)
    }
}

private val PreviewMember = MemberSettings(reminderTime = TimeOfDay(20, 0), reminderTimezone = "Europe/London")

@Preview
@Composable
private fun ReminderAskDarkPreview() = MoyiTheme(darkTheme = true) {
    ReminderAskScreen(ReminderAskState(load = ReminderTimeLoad.Ready, member = PreviewMember), onAction = {})
}

@Preview
@Composable
private fun ReminderAskLightPreview() = MoyiTheme(darkTheme = false) {
    ReminderAskScreen(ReminderAskState(load = ReminderTimeLoad.Ready, member = PreviewMember), onAction = {})
}

@Preview
@Composable
private fun ReminderAskDeniedPreview() = MoyiTheme(darkTheme = true) {
    ReminderAskScreen(
        ReminderAskState(load = ReminderTimeLoad.Ready, member = PreviewMember, phase = ReminderAskPhase.Answered, denied = true),
        onAction = {},
    )
}
