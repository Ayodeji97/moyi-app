package com.moyi.app.feature.bond.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import com.moyi.app.designsystem.MoyiTheme
import com.moyi.app.designsystem.component.Field
import com.moyi.app.designsystem.component.FieldType
import com.moyi.app.designsystem.component.InlineError
import com.moyi.app.designsystem.component.InviteCode
import com.moyi.app.designsystem.component.MoyiScaffold
import com.moyi.app.designsystem.component.MoyiText
import com.moyi.app.designsystem.component.PrimaryAction
import com.moyi.app.designsystem.component.PrimaryActionState
import com.moyi.app.designsystem.component.ScreenHeader
import com.moyi.app.designsystem.component.TextLink
import com.moyi.app.designsystem.component.TextTone
import com.moyi.app.designsystem.presentation.UiText
import com.moyi.app.domain.bond.BondSettings
import com.moyi.app.domain.bond.BondStatus
import com.moyi.app.domain.bond.BondType
import com.moyi.app.domain.bond.Invite
import com.moyi.app.domain.bond.PendingTimezoneChange
import com.moyi.app.feature.bond.resources.Res
import com.moyi.app.feature.bond.resources.settings_invite_cancel
import com.moyi.app.feature.bond.resources.settings_invite_cancelling
import com.moyi.app.feature.bond.resources.settings_invite_create
import com.moyi.app.feature.bond.resources.settings_invite_creating
import com.moyi.app.feature.bond.resources.settings_invite_heading
import com.moyi.app.feature.bond.resources.settings_invite_line
import com.moyi.app.feature.bond.resources.settings_invite_line_undated
import com.moyi.app.feature.bond.resources.settings_invite_none_heading
import com.moyi.app.feature.bond.resources.settings_invite_none_line
import com.moyi.app.feature.bond.resources.settings_invite_share
import com.moyi.app.feature.bond.resources.settings_name_save
import com.moyi.app.feature.bond.resources.settings_name_saving
import com.moyi.app.feature.bond.resources.settings_tz_agree
import com.moyi.app.feature.bond.resources.settings_tz_agreeing
import com.moyi.app.feature.bond.resources.settings_tz_confirm_heading
import com.moyi.app.feature.bond.resources.settings_tz_confirm_heading_unnamed
import com.moyi.app.feature.bond.resources.settings_tz_confirm_line
import com.moyi.app.feature.bond.resources.settings_tz_decline
import com.moyi.app.feature.bond.resources.settings_tz_declining
import com.moyi.app.feature.bond.resources.settings_tz_error_too_soon
import com.moyi.app.feature.bond.resources.settings_tz_heading
import com.moyi.app.feature.bond.resources.settings_tz_list_error
import com.moyi.app.feature.bond.resources.settings_tz_pick_hint
import com.moyi.app.feature.bond.resources.settings_tz_picker_heading
import com.moyi.app.feature.bond.resources.settings_tz_propose
import com.moyi.app.feature.bond.resources.settings_tz_proposed_heading
import com.moyi.app.feature.bond.resources.settings_tz_proposed_line
import com.moyi.app.feature.bond.resources.settings_tz_proposed_line_unnamed
import com.moyi.app.feature.bond.resources.settings_tz_proposing
import com.moyi.app.feature.bond.resources.settings_tz_rules
import com.moyi.app.feature.bond.resources.settings_tz_rules_alone
import com.moyi.app.feature.bond.resources.settings_tz_search_empty
import com.moyi.app.feature.bond.resources.settings_tz_search_label
import com.moyi.app.feature.bond.resources.settings_tz_withdraw
import com.moyi.app.feature.bond.resources.settings_tz_withdrawing
import com.moyi.app.feature.onboarding.resources.field_timezone_helper
import com.moyi.app.feature.onboarding.resources.field_timezone_label
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant
import com.moyi.app.feature.onboarding.resources.Res as OnboardingRes

/**
 * Bond settings · shared time zone (FR-027, states.md §8), one destination with three
 * states, chosen by the pending change ([BondSettingsState.timeZoneStep]):
 *
 * - **Propose** (Figma 397:22 / 398:1538): the heading, the `Field · Select` for the
 *   zone with onboarding's helper naming its city, the constraints paragraph 24 below,
 *   and Propose change. On a bond nobody has joined the paragraph says it changes now,
 *   and the action is Save.
 * - **Proposed** (397:67 / 398:1549): the city asked for, states.md's sentence, and the
 *   secondary Cancel this proposal. No primary: there is nothing else to do but wait.
 * - **Confirm** (397:102 / 398:1560): who proposed what, the consequence, Agree, and
 *   Decline (the same `DELETE`, from the other side).
 *
 * Heading at 96 (D1), every block 24 apart, as the frames draw. An error sits 8 above
 * the action that caused it.
 */
@Composable
fun TimeZoneScreen(
    state: BondSettingsState,
    onAction: (BondSettingsAction) -> Unit,
    onBack: () -> Unit,
) {
    val settings = state.settings ?: return
    when (state.timeZoneStep) {
        TimeZoneStep.Propose -> ProposeZone(state, settings, onAction, onBack)
        TimeZoneStep.Proposed -> ProposedZone(state, settings, onAction, onBack)
        TimeZoneStep.Confirm -> ConfirmZone(state, settings, onAction, onBack)
    }
}

@Composable
private fun ProposeZone(state: BondSettingsState, settings: BondSettings, onAction: (BondSettingsAction) -> Unit, onBack: () -> Unit) {
    val spacing = MoyiTheme.spacing
    val zone = state.zoneDraft ?: settings.anchorTimezone
    val alone = settings.waitingForPartner
    val sending = state.saving == SettingsField.TimeZone
    MoyiScaffold(
        onBack = onBack,
        action = {
            ZoneError(state)
            PrimaryAction(
                text = stringResource(if (alone) Res.string.settings_name_save else Res.string.settings_tz_propose),
                onClick = { onAction(BondSettingsAction.OnProposeClick) },
                state = if (sending) {
                    PrimaryActionState.Sending(stringResource(if (alone) Res.string.settings_name_saving else Res.string.settings_tz_proposing))
                } else {
                    PrimaryActionState.Idle
                },
            )
        },
    ) {
        val heading = stringResource(Res.string.settings_tz_heading)
        ScreenHeader(eyebrow = heading, heading = heading)
        Spacer(Modifier.height(spacing.lg))
        Field(
            value = cityOf(zone),
            onValueChange = {},
            label = stringResource(OnboardingRes.string.field_timezone_label),
            type = FieldType.Select,
            helper = stringResource(OnboardingRes.string.field_timezone_helper, cityOf(zone)),
            error = if (state.zonesFailed) stringResource(Res.string.settings_tz_list_error) else null,
            enabled = !sending,
            onClick = { onAction(BondSettingsAction.OnZoneFieldClick) },
        )
        Spacer(Modifier.height(spacing.lg))
        MoyiText(stringResource(if (alone) Res.string.settings_tz_rules_alone else Res.string.settings_tz_rules), tone = TextTone.Muted)
    }
}

@Composable
private fun ProposedZone(state: BondSettingsState, settings: BondSettings, onAction: (BondSettingsAction) -> Unit, onBack: () -> Unit) {
    val pending = settings.pendingTimezone ?: return
    val spacing = MoyiTheme.spacing
    val withdrawing = state.saving == SettingsField.TimeZone
    MoyiScaffold(
        onBack = onBack,
        action = {
            ZoneError(state)
            TextLink(
                text = stringResource(if (withdrawing) Res.string.settings_tz_withdrawing else Res.string.settings_tz_withdraw),
                onClick = { onAction(BondSettingsAction.OnWithdrawProposalClick) },
                centered = true,
                enabled = state.saving == null,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) {
        val heading = stringResource(Res.string.settings_tz_proposed_heading, cityOf(pending.timezone))
        ScreenHeader(eyebrow = heading, heading = heading)
        Spacer(Modifier.height(spacing.lg))
        MoyiText(
            settings.partnerName?.let { stringResource(Res.string.settings_tz_proposed_line, it) }
                ?: stringResource(Res.string.settings_tz_proposed_line_unnamed),
            tone = TextTone.Muted,
        )
    }
}

@Composable
private fun ConfirmZone(state: BondSettingsState, settings: BondSettings, onAction: (BondSettingsAction) -> Unit, onBack: () -> Unit) {
    val pending = settings.pendingTimezone ?: return
    val spacing = MoyiTheme.spacing
    val city = cityOf(pending.timezone)
    val busy = state.saving == SettingsField.TimeZone
    MoyiScaffold(
        onBack = onBack,
        action = {
            ZoneError(state)
            PrimaryAction(
                text = stringResource(Res.string.settings_tz_agree),
                onClick = { onAction(BondSettingsAction.OnAgreeClick) },
                state = when {
                    busy && !state.zoneSecondaryInFlight -> PrimaryActionState.Sending(stringResource(Res.string.settings_tz_agreeing))
                    busy -> PrimaryActionState.Disabled
                    else -> PrimaryActionState.Idle
                },
            )
            Spacer(Modifier.height(spacing.lg))
            TextLink(
                text = stringResource(if (busy && state.zoneSecondaryInFlight) Res.string.settings_tz_declining else Res.string.settings_tz_decline),
                onClick = { onAction(BondSettingsAction.OnWithdrawProposalClick) },
                centered = true,
                enabled = state.saving == null,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) {
        val heading = settings.partnerName?.let { stringResource(Res.string.settings_tz_confirm_heading, it, city) }
            ?: stringResource(Res.string.settings_tz_confirm_heading_unnamed, city)
        ScreenHeader(eyebrow = heading, heading = heading)
        Spacer(Modifier.height(spacing.lg))
        MoyiText(stringResource(Res.string.settings_tz_confirm_line, city), tone = TextTone.Muted)
    }
}

@Composable
private fun ZoneError(state: BondSettingsState) {
    state.error?.takeIf { it.field == SettingsField.TimeZone }?.let {
        InlineError(it.message.asString())
        Spacer(Modifier.height(MoyiTheme.spacing.sm))
    }
}

/**
 * The zone list (no frame: docs/missing-components.md §22). The most conservative form
 * the existing pieces allow: a heading, a `Field · Text` to search, and one settings row
 * per city with its IANA id as the value, which tells apart cities that share a name.
 * Every region zone the platform knows (kotlinx-datetime); a tap chooses and goes back.
 */
@Composable
fun ZonePickerScreen(
    state: BondSettingsState,
    onAction: (BondSettingsAction) -> Unit,
    onBack: () -> Unit,
) {
    val spacing = MoyiTheme.spacing
    MoyiScaffold(onBack = onBack, scrollContent = false) {
        val heading = stringResource(Res.string.settings_tz_picker_heading)
        ScreenHeader(eyebrow = heading, heading = heading)
        Spacer(Modifier.height(spacing.lg))
        Field(
            value = state.zoneQuery,
            onValueChange = { onAction(BondSettingsAction.OnZoneQueryChange(it)) },
            label = stringResource(Res.string.settings_tz_search_label),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Search),
        )
        Spacer(Modifier.height(spacing.md))
        val zones = state.visibleZones
        if (zones.isEmpty()) {
            MoyiText(stringResource(Res.string.settings_tz_search_empty), tone = TextTone.Muted)
        } else {
            val hint = stringResource(Res.string.settings_tz_pick_hint)
            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                items(zones, key = { it }) { zone ->
                    SettingsRow(
                        label = cityOf(zone),
                        value = zone,
                        kind = RowKind.Acts,
                        onClickLabel = hint,
                        onClick = { onAction(BondSettingsAction.OnZonePicked(zone)) },
                    )
                }
            }
        }
    }
}

/**
 * Bond settings · invite (states.md §2; strings-proposed.md §6b), two states:
 *
 * - **Active** (Figma 416:44 / 416:1877): "Invite", the line with the day it expires,
 *   onboarding's `InviteCode` with copy, then Share invite (the OS sheet, which
 *   proves nothing and goes nowhere) and Cancel this invite. No confirmation: a new
 *   invite is one tap away (strings-proposed.md §6b).
 * - **Revoked** (416:97 / 416:1890): "No active invite", what that means, and Create
 *   a new invite.
 *
 * Heading at 96 (D1), the line 24 below, the code 24 below that; the actions 24 apart.
 */
@Composable
fun InviteScreen(
    state: BondSettingsState,
    onAction: (BondSettingsAction) -> Unit,
    onBack: () -> Unit,
) {
    val settings = state.settings ?: return
    val invite = settings.invite
    val spacing = MoyiTheme.spacing
    val busy = state.saving == SettingsField.Invite
    MoyiScaffold(
        onBack = onBack,
        action = {
            state.error?.takeIf { it.field == SettingsField.Invite }?.let {
                InlineError(it.message.asString())
                Spacer(Modifier.height(spacing.sm))
            }
            if (invite != null) {
                PrimaryAction(
                    text = stringResource(Res.string.settings_invite_share),
                    onClick = { onAction(BondSettingsAction.OnShareInviteClick) },
                    state = if (busy) PrimaryActionState.Disabled else PrimaryActionState.Idle,
                )
                Spacer(Modifier.height(spacing.lg))
                TextLink(
                    text = stringResource(if (busy) Res.string.settings_invite_cancelling else Res.string.settings_invite_cancel),
                    onClick = { onAction(BondSettingsAction.OnCancelInviteClick) },
                    centered = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                // Offered whatever the status: a bond that filled meanwhile answers 409 BOND_FULL, said plainly.
                PrimaryAction(
                    text = stringResource(Res.string.settings_invite_create),
                    onClick = { onAction(BondSettingsAction.OnCreateInviteClick) },
                    state = if (busy) PrimaryActionState.Sending(stringResource(Res.string.settings_invite_creating)) else PrimaryActionState.Idle,
                )
            }
        },
    ) {
        if (invite != null) {
            val heading = stringResource(Res.string.settings_invite_heading)
            ScreenHeader(eyebrow = heading, heading = heading)
            Spacer(Modifier.height(spacing.lg))
            MoyiText(
                invite.expiresAt?.let { stringResource(Res.string.settings_invite_line, localDayAndMonth(it)) }
                    ?: stringResource(Res.string.settings_invite_line_undated),
                tone = TextTone.Muted,
            )
            Spacer(Modifier.height(spacing.lg))
            InviteCode(code = invite.code, copied = state.inviteCopied, onCopy = { onAction(BondSettingsAction.OnCopyInviteClick) })
        } else {
            val heading = stringResource(Res.string.settings_invite_none_heading)
            ScreenHeader(eyebrow = heading, heading = heading)
            Spacer(Modifier.height(spacing.lg))
            MoyiText(stringResource(Res.string.settings_invite_none_line), tone = TextTone.Muted)
        }
    }
}

// ----- previews: every state (tooling only; the device preview build is PreviewBondSettingsRepository) -----

private val previewBond = BondSettings(
    bondId = "preview-bond",
    name = "Amara and Tunde",
    type = BondType.COUPLE,
    status = BondStatus.ACTIVE,
    anchorTimezone = "Africa/Lagos",
    revealTime = null,
    strictMode = false,
    version = "\"1\"",
    partnerName = "Tunde",
)

private val previewPending = PendingTimezoneChange("p1", "Europe/London", proposedByMe = true)

private val previewInvite = Invite("i1", "K7Q2PD", "https://getmoyi.app/i/K7Q2PD", Instant.parse("2026-11-13T10:00:00Z"))

@Composable
private fun ZonePreview(state: BondSettingsState) = MoyiTheme(darkTheme = true) { TimeZoneScreen(state, onAction = {}, onBack = {}) }

@Preview
@Composable
private fun TimeZoneProposePreview() = ZonePreview(BondSettingsState(load = SettingsLoad.Ready, settings = previewBond, zoneDraft = "Europe/London"))

@Preview
@Composable
private fun TimeZoneTooSoonPreview() = ZonePreview(
    BondSettingsState(
        load = SettingsLoad.Ready,
        settings = previewBond,
        zoneDraft = "Europe/London",
        error = SettingsError(SettingsField.TimeZone, UiText.Resource(Res.string.settings_tz_error_too_soon)),
    ),
)

@Preview
@Composable
private fun TimeZoneProposedPreview() = ZonePreview(BondSettingsState(load = SettingsLoad.Ready, settings = previewBond.copy(pendingTimezone = previewPending)))

@Preview
@Composable
private fun TimeZoneConfirmPreview() = ZonePreview(
    BondSettingsState(load = SettingsLoad.Ready, settings = previewBond.copy(pendingTimezone = previewPending.copy(proposedByMe = false))),
)

@Preview
@Composable
private fun TimeZoneConfirmLightPreview() = MoyiTheme(darkTheme = false) {
    TimeZoneScreen(
        BondSettingsState(load = SettingsLoad.Ready, settings = previewBond.copy(pendingTimezone = previewPending.copy(proposedByMe = false))),
        onAction = {},
        onBack = {},
    )
}

@Preview
@Composable
private fun ZonePickerPreview() = MoyiTheme(darkTheme = true) {
    ZonePickerScreen(
        BondSettingsState(load = SettingsLoad.Ready, settings = previewBond, zones = listOf("Africa/Lagos", "Europe/London", "America/New_York")),
        onAction = {},
        onBack = {},
    )
}

@Preview
@Composable
private fun InviteActivePreview() = MoyiTheme(darkTheme = true) {
    InviteScreen(
        BondSettingsState(load = SettingsLoad.Ready, settings = previewBond.copy(status = BondStatus.PENDING_MEMBER, invite = previewInvite)),
        onAction = {},
        onBack = {},
    )
}

@Preview
@Composable
private fun InviteRevokedPreview() = MoyiTheme(darkTheme = true) {
    InviteScreen(BondSettingsState(load = SettingsLoad.Ready, settings = previewBond.copy(status = BondStatus.PENDING_MEMBER)), onAction = {}, onBack = {})
}

@Preview
@Composable
private fun InviteLightPreview() = MoyiTheme(darkTheme = false) {
    InviteScreen(
        BondSettingsState(load = SettingsLoad.Ready, settings = previewBond.copy(status = BondStatus.PENDING_MEMBER, invite = previewInvite)),
        onAction = {},
        onBack = {},
    )
}
