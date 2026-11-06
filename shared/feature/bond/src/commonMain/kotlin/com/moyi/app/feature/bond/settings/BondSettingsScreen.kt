package com.moyi.app.feature.bond.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import com.moyi.app.designsystem.MoyiTheme
import com.moyi.app.designsystem.component.ConfirmDialog
import com.moyi.app.designsystem.component.Field
import com.moyi.app.designsystem.component.InlineError
import com.moyi.app.designsystem.component.MoyiScaffold
import com.moyi.app.designsystem.component.MoyiText
import com.moyi.app.designsystem.component.PrimaryAction
import com.moyi.app.designsystem.component.PrimaryActionState
import com.moyi.app.designsystem.component.ScreenHeader
import com.moyi.app.designsystem.component.StateBlock
import com.moyi.app.designsystem.component.TextTone
import com.moyi.app.designsystem.component.TimeOfDayPicker
import com.moyi.app.domain.bond.BondSettings
import com.moyi.app.domain.bond.BondStatus
import com.moyi.app.domain.bond.BondType
import com.moyi.app.domain.bond.TimeOfDay
import com.moyi.app.feature.bond.resources.Res
import com.moyi.app.feature.bond.resources.settings_cancel
import com.moyi.app.feature.bond.resources.settings_group_bond
import com.moyi.app.feature.bond.resources.settings_group_day
import com.moyi.app.feature.bond.resources.settings_group_ending
import com.moyi.app.feature.bond.resources.settings_group_you
import com.moyi.app.feature.bond.resources.settings_heading
import com.moyi.app.feature.bond.resources.settings_leave_confirm
import com.moyi.app.feature.bond.resources.settings_leave_line
import com.moyi.app.feature.bond.resources.settings_leave_title
import com.moyi.app.feature.bond.resources.settings_leaving
import com.moyi.app.feature.bond.resources.settings_load_error_action
import com.moyi.app.feature.bond.resources.settings_load_error_heading
import com.moyi.app.feature.bond.resources.settings_load_error_line
import com.moyi.app.feature.bond.resources.settings_name_heading
import com.moyi.app.feature.bond.resources.settings_name_save
import com.moyi.app.feature.bond.resources.settings_name_saving
import com.moyi.app.feature.bond.resources.settings_picker_confirm
import com.moyi.app.feature.bond.resources.settings_reveal_none
import com.moyi.app.feature.bond.resources.settings_row_leave
import com.moyi.app.feature.bond.resources.settings_row_name
import com.moyi.app.feature.bond.resources.settings_row_rest_days
import com.moyi.app.feature.bond.resources.settings_row_reveal
import com.moyi.app.feature.bond.resources.settings_row_sign_out
import com.moyi.app.feature.bond.resources.settings_row_type
import com.moyi.app.feature.bond.resources.settings_saving
import com.moyi.app.feature.bond.resources.settings_signing_out
import com.moyi.app.feature.bond.resources.settings_toggle_hint
import com.moyi.app.feature.bond.resources.settings_type_hint
import com.moyi.app.feature.bond.resources.settings_type_line
import com.moyi.app.feature.bond.resources.settings_value_off
import com.moyi.app.feature.bond.resources.settings_value_on
import com.moyi.app.feature.onboarding.common.RelationshipType
import com.moyi.app.feature.onboarding.relationship.RelationshipCard
import com.moyi.app.feature.onboarding.resources.bond_name_helper
import com.moyi.app.feature.onboarding.resources.bond_name_label
import com.moyi.app.feature.onboarding.resources.bond_type_heading
import org.jetbrains.compose.resources.stringResource
import com.moyi.app.feature.onboarding.resources.Res as OnboardingRes

/**
 * Bond settings · root (Figma 396:3 / 398:1509; states.md §8). Back to Today, then the
 * heading at 96 (no eyebrow under a back target, D1), then the groups 24 apart, each a
 * `label` heading 16 above its 56 rows.
 *
 * Drawn here: This bond (relationship type, bond name), The day (reveal time, rest days),
 * Just you (sign out, in place of Account), Ending (leave). Left out on purpose, with no
 * dead row (docs/figma/bond-settings-check.md): the shared time zone (its FR-027 flow is a
 * later PR), your reminder (`/members/me/notifications` is not in the contract copy yet),
 * the Invite group (416:*), and Block and leave.
 *
 * Loading is the bare `surface`; a failed load is the Generic error with Try again, under
 * the back target, so the way out stays.
 */
@Composable
fun BondSettingsScreen(
    state: BondSettingsState,
    onAction: (BondSettingsAction) -> Unit,
    onBack: () -> Unit,
) {
    val settings = state.settings
    when {
        state.load == SettingsLoad.Loading || (state.load == SettingsLoad.Ready && settings == null) ->
            Box(Modifier.fillMaxSize().background(MoyiTheme.colors.surface))
        state.load == SettingsLoad.Failed -> MoyiScaffold(onBack = onBack, contentArrangement = Arrangement.Center) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                StateBlock(
                    heading = stringResource(Res.string.settings_load_error_heading),
                    line = stringResource(Res.string.settings_load_error_line),
                    actionLabel = stringResource(Res.string.settings_load_error_action),
                    onAction = { onAction(BondSettingsAction.OnRetryClick) },
                )
            }
        }
        settings != null -> SettingsRoot(state, settings, onAction, onBack)
    }
}

@Composable
private fun SettingsRoot(
    state: BondSettingsState,
    settings: BondSettings,
    onAction: (BondSettingsAction) -> Unit,
    onBack: () -> Unit,
) {
    val spacing = MoyiTheme.spacing
    val saving = stringResource(Res.string.settings_saving)
    val busy = state.saving != null || state.signingOut
    val error = state.error
    val errorText = error?.message?.asString()
    fun errorFor(field: SettingsField): String? = errorText?.takeIf { error.field == field }

    MoyiScaffold(onBack = onBack) {
        val heading = stringResource(Res.string.settings_heading)
        ScreenHeader(eyebrow = heading, heading = heading)
        Spacer(Modifier.height(spacing.lg))
        Column(verticalArrangement = Arrangement.spacedBy(spacing.lg)) {
            SettingsGroup(stringResource(Res.string.settings_group_bond)) {
                SettingsRow(
                    label = stringResource(Res.string.settings_row_type),
                    value = stringResource(RelationshipType.of(settings.type).label),
                    kind = RowKind.Navigates,
                    enabled = !busy,
                    onClick = { onAction(BondSettingsAction.OnTypeClick) },
                )
                SettingsRow(
                    label = stringResource(Res.string.settings_row_name),
                    value = settings.name,
                    kind = RowKind.Navigates,
                    enabled = !busy,
                    onClick = { onAction(BondSettingsAction.OnNameClick) },
                )
            }
            SettingsGroup(stringResource(Res.string.settings_group_day)) {
                SettingsRow(
                    label = stringResource(Res.string.settings_row_reveal),
                    value = when {
                        state.saving == SettingsField.RevealTime -> saving
                        else -> settings.revealTime?.toString() ?: stringResource(Res.string.settings_reveal_none)
                    },
                    kind = RowKind.Opens,
                    enabled = !busy,
                    error = errorFor(SettingsField.RevealTime),
                    onClick = { onAction(BondSettingsAction.OnRevealTimeClick) },
                )
                val restDays = stringResource(if (settings.restDays) Res.string.settings_value_on else Res.string.settings_value_off)
                SettingsToggleRow(
                    label = stringResource(Res.string.settings_row_rest_days),
                    value = if (state.saving == SettingsField.RestDays) saving else restDays,
                    checked = settings.restDays,
                    hint = stringResource(Res.string.settings_toggle_hint),
                    enabled = !busy,
                    error = errorFor(SettingsField.RestDays),
                    onToggle = { onAction(BondSettingsAction.OnRestDaysToggle) },
                )
            }
            SettingsGroup(stringResource(Res.string.settings_group_you)) {
                SettingsRow(
                    label = stringResource(if (state.signingOut) Res.string.settings_signing_out else Res.string.settings_row_sign_out),
                    kind = RowKind.Acts,
                    enabled = !busy,
                    onClick = { onAction(BondSettingsAction.OnSignOutClick) },
                )
            }
            SettingsGroup(stringResource(Res.string.settings_group_ending)) {
                SettingsRow(
                    label = stringResource(if (state.leaving) Res.string.settings_leaving else Res.string.settings_row_leave),
                    kind = RowKind.Navigates,
                    destructive = true,
                    enabled = !busy,
                    error = errorFor(SettingsField.Leave),
                    onClick = { onAction(BondSettingsAction.OnLeaveClick) },
                )
            }
        }
    }

    if (state.pickingRevealTime) {
        val initial = settings.revealTime ?: DEFAULT_REVEAL_TIME
        TimeOfDayPicker(
            title = stringResource(Res.string.settings_row_reveal),
            initialHour = initial.hour,
            initialMinute = initial.minute,
            confirmLabel = stringResource(Res.string.settings_picker_confirm),
            dismissLabel = stringResource(Res.string.settings_cancel),
            onConfirm = { hour, minute -> onAction(BondSettingsAction.OnRevealTimePicked(hour, minute)) },
            onDismiss = { onAction(BondSettingsAction.OnRevealTimeDismiss) },
        )
    }
    if (state.confirmingLeave) {
        ConfirmDialog(
            title = stringResource(Res.string.settings_leave_title),
            message = stringResource(Res.string.settings_leave_line),
            confirmLabel = stringResource(Res.string.settings_leave_confirm),
            dismissLabel = stringResource(Res.string.settings_cancel),
            destructive = true,
            onConfirm = { onAction(BondSettingsAction.OnLeaveConfirm) },
            onDismiss = { onAction(BondSettingsAction.OnLeaveDismiss) },
        )
    }
}

/** states.md §8: "Reveal time · Default 21:00". Where the picker starts when none is set. */
private val DEFAULT_REVEAL_TIME = TimeOfDay(21, 0)

/**
 * Bond settings · bond name (Figma 661:1909 / 661:1929; states.md §8). Back, the heading
 * *"What do you call this bond?"*, a `Field · Text` labelled **Bond name**, and **Save**.
 * Figma draws a line under the heading whose words were not read; onboarding's helper
 * sits under the field instead, so nothing is invented. A failed save keeps the name and
 * shows the error 8 above Save; a conflict also reloads, and the typed name stays.
 */
@Composable
fun BondNameScreen(
    state: BondSettingsState,
    onAction: (BondSettingsAction) -> Unit,
    onBack: () -> Unit,
) {
    val spacing = MoyiTheme.spacing
    val save = { onAction(BondSettingsAction.OnNameSave) }
    MoyiScaffold(
        onBack = onBack,
        action = {
            state.error?.takeIf { it.field == SettingsField.Name }?.let {
                InlineError(it.message.asString())
                Spacer(Modifier.height(spacing.sm))
            }
            PrimaryAction(
                text = stringResource(Res.string.settings_name_save),
                onClick = save,
                state = if (state.saving == SettingsField.Name) {
                    PrimaryActionState.Sending(stringResource(Res.string.settings_name_saving))
                } else {
                    PrimaryActionState.Idle
                },
            )
        },
    ) {
        val heading = stringResource(Res.string.settings_name_heading)
        ScreenHeader(eyebrow = heading, heading = heading)
        Spacer(Modifier.height(spacing.lg))
        Field(
            value = state.nameDraft,
            onValueChange = { onAction(BondSettingsAction.OnNameChange(it)) },
            label = stringResource(OnboardingRes.string.bond_name_label),
            helper = stringResource(OnboardingRes.string.bond_name_helper),
            error = state.nameError?.asString(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { save() }),
        )
    }
}

/**
 * Bond settings · relationship type. Not drawn in Figma's Bond settings section; it is
 * onboarding 4's layout (341:24) with its cards, because states.md §1a says the cards
 * recur exactly here. A tap selects and saves, then returns to the root; changing it is
 * unilateral and asks nothing (states.md §8). A failed save keeps the person here.
 */
@Composable
fun BondTypeScreen(
    state: BondSettingsState,
    onAction: (BondSettingsAction) -> Unit,
    onBack: () -> Unit,
) {
    val spacing = MoyiTheme.spacing
    val current = state.settings?.type?.let(RelationshipType::of)
    MoyiScaffold(
        onBack = onBack,
        action = state.error?.takeIf { it.field == SettingsField.Type }?.let { error ->
            { InlineError(error.message.asString()) }
        },
    ) {
        val heading = stringResource(OnboardingRes.string.bond_type_heading)
        ScreenHeader(eyebrow = heading, heading = heading)
        Spacer(Modifier.height(spacing.md))
        MoyiText(stringResource(Res.string.settings_type_line), tone = TextTone.Muted)
        Spacer(Modifier.height(spacing.xxl))
        Column(
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            val hint = stringResource(Res.string.settings_type_hint)
            RelationshipType.entries.forEach { type ->
                RelationshipCard(
                    label = stringResource(type.label),
                    selected = type == current,
                    hint = hint,
                    onClick = { if (state.saving == null) onAction(BondSettingsAction.OnTypeSelect(type.bondType)) },
                )
            }
        }
    }
}

private val previewSettings = BondSettings(
    bondId = "preview-bond",
    name = "Amara and Tunde",
    type = BondType.COUPLE,
    status = BondStatus.ACTIVE,
    anchorTimezone = "Africa/Lagos",
    revealTime = TimeOfDay(21, 0),
    strictMode = false,
    version = "\"1\"",
)

@Preview
@Composable
private fun BondSettingsPreview() {
    MoyiTheme(darkTheme = true) {
        BondSettingsScreen(BondSettingsState(load = SettingsLoad.Ready, settings = previewSettings), onAction = {}, onBack = {})
    }
}

@Preview
@Composable
private fun BondSettingsLightPreview() {
    MoyiTheme(darkTheme = false) {
        BondSettingsScreen(BondSettingsState(load = SettingsLoad.Ready, settings = previewSettings), onAction = {}, onBack = {})
    }
}

@Preview
@Composable
private fun BondSettingsFailedPreview() {
    MoyiTheme(darkTheme = true) { BondSettingsScreen(BondSettingsState(load = SettingsLoad.Failed), onAction = {}, onBack = {}) }
}

@Preview
@Composable
private fun BondNamePreview() {
    MoyiTheme(darkTheme = true) {
        BondNameScreen(BondSettingsState(load = SettingsLoad.Ready, settings = previewSettings, nameDraft = "Amara and Tunde"), onAction = {}, onBack = {})
    }
}

@Preview
@Composable
private fun BondTypePreview() {
    MoyiTheme(darkTheme = true) {
        BondTypeScreen(BondSettingsState(load = SettingsLoad.Ready, settings = previewSettings), onAction = {}, onBack = {})
    }
}
