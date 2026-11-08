package com.moyi.app.feature.bond.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.moyi.app.designsystem.MoyiTheme
import com.moyi.app.designsystem.component.MoyiScaffold
import com.moyi.app.designsystem.component.MoyiText
import com.moyi.app.designsystem.component.ScreenHeader
import com.moyi.app.designsystem.component.StateBlock
import com.moyi.app.designsystem.component.TextTone
import com.moyi.app.designsystem.component.TimeOfDayPicker
import com.moyi.app.domain.notification.NotificationCategory
import com.moyi.app.feature.bond.resources.Res
import com.moyi.app.feature.bond.resources.notifications_category_milestone
import com.moyi.app.feature.bond.resources.notifications_category_on_this_day
import com.moyi.app.feature.bond.resources.notifications_category_partner_joined
import com.moyi.app.feature.bond.resources.notifications_category_proposal
import com.moyi.app.feature.bond.resources.notifications_category_reminder
import com.moyi.app.feature.bond.resources.notifications_category_reveal
import com.moyi.app.feature.bond.resources.notifications_group_kinds
import com.moyi.app.feature.bond.resources.notifications_group_phone
import com.moyi.app.feature.bond.resources.notifications_group_quiet
import com.moyi.app.feature.bond.resources.notifications_line
import com.moyi.app.feature.bond.resources.notifications_phone_allow
import com.moyi.app.feature.bond.resources.notifications_phone_denied
import com.moyi.app.feature.bond.resources.notifications_phone_not_asked
import com.moyi.app.feature.bond.resources.notifications_phone_open_settings
import com.moyi.app.feature.bond.resources.notifications_quiet_end
import com.moyi.app.feature.bond.resources.notifications_quiet_line
import com.moyi.app.feature.bond.resources.notifications_quiet_start
import com.moyi.app.feature.bond.resources.notifications_quiet_toggle
import com.moyi.app.feature.bond.resources.notifications_reminder_time
import com.moyi.app.feature.bond.resources.settings_cancel
import com.moyi.app.feature.bond.resources.settings_load_error_action
import com.moyi.app.feature.bond.resources.settings_load_error_heading
import com.moyi.app.feature.bond.resources.settings_load_error_line
import com.moyi.app.feature.bond.resources.settings_picker_confirm
import com.moyi.app.feature.bond.resources.settings_row_notifications
import com.moyi.app.feature.bond.resources.settings_saving
import com.moyi.app.feature.bond.resources.settings_toggle_hint
import com.moyi.app.feature.bond.resources.settings_value_off
import com.moyi.app.feature.bond.resources.settings_value_on
import com.moyi.app.feature.bond.settings.RowKind
import com.moyi.app.feature.bond.settings.SettingsGroup
import com.moyi.app.feature.bond.settings.SettingsLoad
import com.moyi.app.feature.bond.settings.SettingsRow
import com.moyi.app.feature.bond.settings.SettingsToggleRow
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Bond settings › Notifications. Not drawn in Figma: built from Bond settings' own rows
 * (SettingsGroup, SettingsRow, SettingsToggleRow) so it adds no component, and raised in
 * docs/missing-components.md.
 *
 * Back to Bond settings, the heading, one line, then up to three groups 24 apart:
 * This phone (only while the phone has never been asked, or said no), What to hear about
 * (the six kinds, and the reminder's time under the reminder), and Quiet hours. Every
 * toggle changes in place and says Saving while it goes (no spinner, states.md); a
 * failure keeps the screen, and its row says what happened.
 */
@Composable
fun NotificationSettingsScreen(
    state: NotificationSettingsState,
    onAction: (NotificationSettingsAction) -> Unit,
    onBack: () -> Unit,
) {
    when (state.load) {
        SettingsLoad.Loading -> Box(Modifier.fillMaxSize().background(MoyiTheme.colors.surface))
        SettingsLoad.Failed -> MoyiScaffold(onBack = onBack, contentArrangement = Arrangement.Center) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                StateBlock(
                    heading = stringResource(Res.string.settings_load_error_heading),
                    line = stringResource(Res.string.settings_load_error_line),
                    actionLabel = stringResource(Res.string.settings_load_error_action),
                    onAction = { onAction(NotificationSettingsAction.OnRetryClick) },
                )
            }
        }
        SettingsLoad.Ready -> Content(state, onAction, onBack)
    }
}

@Composable
private fun Content(state: NotificationSettingsState, onAction: (NotificationSettingsAction) -> Unit, onBack: () -> Unit) {
    val preferences = state.preferences ?: return
    val member = state.member ?: return
    val spacing = MoyiTheme.spacing
    val busy = state.saving != null
    val errorText = state.error?.message?.asString()
    fun errorFor(row: NotificationRow): String? = errorText?.takeIf { state.error?.row == row }
    val saving = stringResource(Res.string.settings_saving)
    val on = stringResource(Res.string.settings_value_on)
    val off = stringResource(Res.string.settings_value_off)
    val hint = stringResource(Res.string.settings_toggle_hint)

    MoyiScaffold(onBack = onBack) {
        val heading = stringResource(Res.string.settings_row_notifications)
        ScreenHeader(eyebrow = heading, heading = heading)
        MoyiText(stringResource(Res.string.notifications_line), tone = TextTone.Muted)
        Spacer(Modifier.height(spacing.lg))
        Column(verticalArrangement = Arrangement.spacedBy(spacing.lg)) {
            PhoneGroup(state, onAction)
            SettingsGroup(stringResource(Res.string.notifications_group_kinds)) {
                NotificationCategory.entries.forEach { category ->
                    val row = NotificationRow.Category(category)
                    val isOn = preferences.isOn(category)
                    SettingsToggleRow(
                        label = stringResource(category.label),
                        value = if (state.saving == row) saving else if (isOn) on else off,
                        checked = isOn,
                        hint = hint,
                        enabled = !busy,
                        error = errorFor(row),
                        onToggle = { onAction(NotificationSettingsAction.OnCategoryToggle(category)) },
                    )
                    if (category == NotificationCategory.Reminder) {
                        SettingsRow(
                            label = stringResource(Res.string.notifications_reminder_time),
                            value = if (state.saving == NotificationRow.ReminderTime) saving else member.reminderTime.toString(),
                            kind = RowKind.Opens,
                            enabled = !busy,
                            error = errorFor(NotificationRow.ReminderTime),
                            onClick = { onAction(NotificationSettingsAction.OnReminderTimeClick) },
                        )
                    }
                }
            }
            SettingsGroup(stringResource(Res.string.notifications_group_quiet)) {
                MoyiText(stringResource(Res.string.notifications_quiet_line), tone = TextTone.Muted)
                SettingsToggleRow(
                    label = stringResource(Res.string.notifications_quiet_toggle),
                    value = if (state.saving == NotificationRow.QuietHours) saving else if (state.quietHoursOn) on else off,
                    checked = state.quietHoursOn,
                    hint = hint,
                    enabled = !busy,
                    error = errorFor(NotificationRow.QuietHours),
                    onToggle = { onAction(NotificationSettingsAction.OnQuietHoursToggle) },
                )
                val start = member.quietHoursStart
                val end = member.quietHoursEnd
                if (start != null && end != null) {
                    SettingsRow(
                        label = stringResource(Res.string.notifications_quiet_start),
                        value = start.toString(),
                        kind = RowKind.Opens,
                        enabled = !busy,
                        onClick = { onAction(NotificationSettingsAction.OnQuietStartClick) },
                    )
                    SettingsRow(
                        label = stringResource(Res.string.notifications_quiet_end),
                        value = end.toString(),
                        kind = RowKind.Opens,
                        enabled = !busy,
                        onClick = { onAction(NotificationSettingsAction.OnQuietEndClick) },
                    )
                }
            }
        }
    }
    TimePicker(state, onAction)
}

/** Only while the phone has never been asked (Allow), or said no (where to turn them on). Nothing once granted. */
@Composable
private fun PhoneGroup(state: NotificationSettingsState, onAction: (NotificationSettingsAction) -> Unit) {
    val asking = NotificationPermissionPolicy.offersAllow(state.permission)
    val off = NotificationPermissionPolicy.saysItIsOff(state.permission)
    if (!asking && !off) return
    SettingsGroup(stringResource(Res.string.notifications_group_phone)) {
        MoyiText(stringResource(if (off) Res.string.notifications_phone_denied else Res.string.notifications_phone_not_asked))
        if (off) {
            SettingsRow(
                label = stringResource(Res.string.notifications_phone_open_settings),
                kind = RowKind.Opens,
                onClick = { onAction(NotificationSettingsAction.OnOpenPhoneSettingsClick) },
            )
        } else {
            SettingsRow(
                label = stringResource(Res.string.notifications_phone_allow),
                kind = RowKind.Acts,
                enabled = !state.askingPermission,
                onClick = { onAction(NotificationSettingsAction.OnAllowClick) },
            )
        }
    }
}

@Composable
private fun TimePicker(state: NotificationSettingsState, onAction: (NotificationSettingsAction) -> Unit) {
    val target = state.picking ?: return
    val initial = state.pickerInitial ?: return
    val title = when (target) {
        TimeTarget.Reminder -> Res.string.notifications_reminder_time
        TimeTarget.QuietStart -> Res.string.notifications_quiet_start
        TimeTarget.QuietEnd -> Res.string.notifications_quiet_end
    }
    TimeOfDayPicker(
        title = stringResource(title),
        initialHour = initial.hour,
        initialMinute = initial.minute,
        confirmLabel = stringResource(Res.string.settings_picker_confirm),
        dismissLabel = stringResource(Res.string.settings_cancel),
        onConfirm = { hour, minute -> onAction(NotificationSettingsAction.OnTimePicked(hour, minute)) },
        onDismiss = { onAction(NotificationSettingsAction.OnTimeDismiss) },
    )
}

private val NotificationCategory.label: StringResource
    get() = when (this) {
        NotificationCategory.Reminder -> Res.string.notifications_category_reminder
        NotificationCategory.Reveal -> Res.string.notifications_category_reveal
        NotificationCategory.PartnerJoined -> Res.string.notifications_category_partner_joined
        NotificationCategory.Milestone -> Res.string.notifications_category_milestone
        NotificationCategory.OnThisDay -> Res.string.notifications_category_on_this_day
        NotificationCategory.Proposal -> Res.string.notifications_category_proposal
    }
