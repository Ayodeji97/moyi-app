package com.moyi.app.feature.bond.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.moyi.app.designsystem.MoyiTheme
import com.moyi.app.designsystem.component.ConsentCheckbox
import com.moyi.app.designsystem.component.InlineError
import com.moyi.app.designsystem.component.MoyiScaffold
import com.moyi.app.designsystem.component.MoyiText
import com.moyi.app.designsystem.component.PrimaryAction
import com.moyi.app.designsystem.component.PrimaryActionState
import com.moyi.app.designsystem.component.ScreenHeader
import com.moyi.app.designsystem.component.TextLink
import com.moyi.app.designsystem.component.TextTone
import com.moyi.app.domain.bond.BondSettings
import com.moyi.app.domain.bond.BondStatus
import com.moyi.app.domain.bond.BondType
import com.moyi.app.domain.bond.PendingDeletionRequest
import com.moyi.app.feature.bond.resources.Res
import com.moyi.app.feature.bond.resources.block_confirm
import com.moyi.app.feature.bond.resources.block_confirming
import com.moyi.app.feature.bond.resources.block_consequence
import com.moyi.app.feature.bond.resources.block_consequence_kept
import com.moyi.app.feature.bond.resources.block_heading
import com.moyi.app.feature.bond.resources.block_line
import com.moyi.app.feature.bond.resources.block_withdraw
import com.moyi.app.feature.bond.resources.close_box_agree_heading
import com.moyi.app.feature.bond.resources.close_box_agree_heading_unnamed
import com.moyi.app.feature.bond.resources.close_box_agree_line
import com.moyi.app.feature.bond.resources.close_box_asked_heading
import com.moyi.app.feature.bond.resources.close_box_asked_line
import com.moyi.app.feature.bond.resources.close_box_asked_line_plain
import com.moyi.app.feature.bond.resources.close_box_cancel
import com.moyi.app.feature.bond.resources.close_box_confirm
import com.moyi.app.feature.bond.resources.close_box_confirming
import com.moyi.app.feature.bond.resources.close_box_cooling_heading
import com.moyi.app.feature.bond.resources.close_box_cooling_heading_undated
import com.moyi.app.feature.bond.resources.close_box_cooling_line
import com.moyi.app.feature.bond.resources.close_box_decline
import com.moyi.app.feature.bond.resources.close_box_declining
import com.moyi.app.feature.bond.resources.close_box_heading
import com.moyi.app.feature.bond.resources.close_box_keep
import com.moyi.app.feature.bond.resources.close_box_keeping
import com.moyi.app.feature.bond.resources.close_box_line
import com.moyi.app.feature.bond.resources.close_box_line_unnamed
import com.moyi.app.feature.bond.resources.close_box_withdraw
import com.moyi.app.feature.bond.resources.close_box_withdrawing
import com.moyi.app.feature.bond.resources.settings_cancel
import com.moyi.app.feature.bond.resources.settings_leave_confirm
import com.moyi.app.feature.bond.resources.settings_leave_confirming
import com.moyi.app.feature.bond.resources.settings_leave_line
import com.moyi.app.feature.bond.resources.settings_leave_title
import com.moyi.app.feature.bond.resources.settings_leave_withdraw
import com.moyi.app.feature.bond.resources.settings_leave_withdraw_unnamed
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

/**
 * Bond settings · leave · CONFIRM (Figma 398:21; states.md §8, §9). A full screen, as
 * Figma draws it, not the platform dialog of figma-deviations.md C2: FR-029a's offer to
 * withdraw the person's entries is a checkbox row, and a platform alert holds none.
 *
 * Back; the heading at 96 (D1); the line 24 below; the withdraw row 24 below that, the
 * whole row toggling, **off** by default (ADR-0035: leaving withdraws only if asked); then
 * Leave (`danger` fill, the primary: no friction on leaving) and Cancel 24 under it.
 */
@Composable
fun LeaveScreen(
    state: BondSettingsState,
    onAction: (BondSettingsAction) -> Unit,
    onBack: () -> Unit,
) {
    val settings = state.settings ?: return
    val spacing = MoyiTheme.spacing
    MoyiScaffold(
        onBack = onBack,
        action = {
            EndingError(state, SettingsField.Leave)
            PrimaryAction(
                text = stringResource(Res.string.settings_leave_confirm),
                onClick = { onAction(BondSettingsAction.OnLeaveConfirm) },
                destructive = true,
                state = if (state.leaving) PrimaryActionState.Sending(stringResource(Res.string.settings_leave_confirming)) else PrimaryActionState.Idle,
            )
            Spacer(Modifier.height(spacing.lg))
            TextLink(
                text = stringResource(Res.string.settings_cancel),
                onClick = onBack,
                centered = true,
                enabled = state.saving == null,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) {
        val heading = stringResource(Res.string.settings_leave_title)
        ScreenHeader(eyebrow = heading, heading = heading)
        Spacer(Modifier.height(spacing.lg))
        MoyiText(stringResource(Res.string.settings_leave_line), tone = TextTone.Muted)
        Spacer(Modifier.height(spacing.lg))
        ConsentCheckbox(
            checked = state.withdrawOnLeave,
            onCheckedChange = { onAction(BondSettingsAction.OnLeaveWithdrawToggle) },
            text = settings.partnerName?.let { stringResource(Res.string.settings_leave_withdraw, it) }
                ?: stringResource(Res.string.settings_leave_withdraw_unnamed),
            enabled = state.saving == null,
        )
    }
}

/**
 * Safety · block · CONFIRM (Figma 612:1827) and its withdraw-off variant (661:1949),
 * one screen: the row decides which. Back; the heading at 96 (D1); the line; the
 * withdraw row, **on** by default (`26` §3.3); the consequence, which follows the row;
 * then Block and leave (`danger`) and Cancel 16 under it, as 612:1838 spaces them.
 *
 * Figma puts "Export your archive" above the confirm. There is no export to go to yet
 * (no endpoint in the contract copy), so it is left out rather than drawn dead, and the
 * consequence drops "Export first if you want to keep them." with it.
 *
 * Reached only while the other member is known by name: the heading names them.
 */
@Composable
fun BlockScreen(
    state: BondSettingsState,
    onAction: (BondSettingsAction) -> Unit,
    onBack: () -> Unit,
) {
    val partner = state.settings?.partnerName ?: return
    val spacing = MoyiTheme.spacing
    MoyiScaffold(
        onBack = onBack,
        action = {
            EndingError(state, SettingsField.Block)
            PrimaryAction(
                text = stringResource(Res.string.block_confirm),
                onClick = { onAction(BondSettingsAction.OnBlockConfirm) },
                destructive = true,
                state = if (state.blocking) PrimaryActionState.Sending(stringResource(Res.string.block_confirming)) else PrimaryActionState.Idle,
            )
            Spacer(Modifier.height(spacing.md))
            TextLink(
                text = stringResource(Res.string.settings_cancel),
                onClick = onBack,
                centered = true,
                enabled = state.saving == null,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) {
        val heading = stringResource(Res.string.block_heading, partner)
        ScreenHeader(eyebrow = heading, heading = heading)
        Spacer(Modifier.height(spacing.lg))
        MoyiText(stringResource(Res.string.block_line, partner), tone = TextTone.Muted)
        Spacer(Modifier.height(spacing.lg))
        ConsentCheckbox(
            checked = state.withdrawOnBlock,
            onCheckedChange = { onAction(BondSettingsAction.OnBlockWithdrawToggle) },
            text = stringResource(Res.string.block_withdraw),
            enabled = state.saving == null,
        )
        Spacer(Modifier.height(spacing.lg))
        MoyiText(
            if (state.withdrawOnBlock) stringResource(Res.string.block_consequence, partner) else stringResource(Res.string.block_consequence_kept),
            tone = TextTone.Muted,
        )
    }
}

/**
 * Close the box (FR-028; states.md §9), one destination whose content follows the bond
 * ([BondSettingsState.closeBoxStep]), as the time zone's does:
 *
 * - **Ask** (Figma 399:38 BOTH MUST AGREE): what closing does, that it needs both and
 *   waits 30 days; Close the box (`danger`) and Cancel 24 under it.
 * - **Asked** (not drawn; the time zone's Proposed layout, 397:67): the ask, when it
 *   lapses, and the secondary Cancel this request. No primary: nothing to do but wait.
 * - **Agree** (not drawn; the time zone's Confirm layout, 397:102): who asked; Close the
 *   box (`danger`, the same `POST`) and Decline (the `DELETE`).
 * - **CoolingOff** (399:1525): the date it closes (`deletionScheduledFor`, "The box closes
 *   on 4 October", a date and never a countdown), and the safe action as the primary
 *   (states.md §9). Its secondary, Export your archive, is left out: there is no export.
 */
@Composable
fun CloseBoxScreen(
    state: BondSettingsState,
    onAction: (BondSettingsAction) -> Unit,
    onBack: () -> Unit,
) {
    val settings = state.settings ?: return
    val spacing = MoyiTheme.spacing
    val busy = state.saving == SettingsField.CloseBox
    val asking = busy && !state.closeBoxSecondaryInFlight
    val withdrawing = busy && state.closeBoxSecondaryInFlight
    val step = state.closeBoxStep
    val partner = settings.partnerName
    MoyiScaffold(
        onBack = onBack,
        action = {
            EndingError(state, SettingsField.CloseBox)
            when (step) {
                CloseBoxStep.Ask, CloseBoxStep.Agree -> {
                    PrimaryAction(
                        text = stringResource(Res.string.close_box_confirm),
                        onClick = { onAction(BondSettingsAction.OnCloseBoxConfirm) },
                        destructive = true,
                        state = when {
                            asking -> PrimaryActionState.Sending(stringResource(Res.string.close_box_confirming))
                            busy -> PrimaryActionState.Disabled
                            else -> PrimaryActionState.Idle
                        },
                    )
                    Spacer(Modifier.height(spacing.lg))
                    if (step == CloseBoxStep.Ask) {
                        TextLink(
                            text = stringResource(Res.string.close_box_cancel),
                            onClick = onBack,
                            centered = true,
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        TextLink(
                            text = stringResource(if (withdrawing) Res.string.close_box_declining else Res.string.close_box_decline),
                            onClick = { onAction(BondSettingsAction.OnCloseBoxWithdraw) },
                            centered = true,
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                CloseBoxStep.Asked -> TextLink(
                    text = stringResource(if (withdrawing) Res.string.close_box_withdrawing else Res.string.close_box_withdraw),
                    onClick = { onAction(BondSettingsAction.OnCloseBoxWithdraw) },
                    centered = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                CloseBoxStep.CoolingOff -> PrimaryAction(
                    text = stringResource(Res.string.close_box_keep),
                    onClick = { onAction(BondSettingsAction.OnCloseBoxWithdraw) },
                    state = if (withdrawing) PrimaryActionState.Sending(stringResource(Res.string.close_box_keeping)) else PrimaryActionState.Idle,
                )
            }
        },
    ) {
        val heading = when (step) {
            CloseBoxStep.Ask -> stringResource(Res.string.close_box_heading)
            CloseBoxStep.Asked -> stringResource(Res.string.close_box_asked_heading)
            CloseBoxStep.Agree -> partner?.let { stringResource(Res.string.close_box_agree_heading, it) }
                ?: stringResource(Res.string.close_box_agree_heading_unnamed)
            CloseBoxStep.CoolingOff -> settings.deletionScheduledFor?.let { stringResource(Res.string.close_box_cooling_heading, localDayAndMonth(it)) }
                ?: stringResource(Res.string.close_box_cooling_heading_undated)
        }
        val line = when (step) {
            CloseBoxStep.Ask -> partner?.let { stringResource(Res.string.close_box_line, it) } ?: stringResource(Res.string.close_box_line_unnamed)
            CloseBoxStep.Asked -> {
                val lapses = settings.pendingDeletion?.expiresAt
                if (partner != null && lapses != null) {
                    stringResource(Res.string.close_box_asked_line, partner, localDayAndMonth(lapses))
                } else {
                    stringResource(Res.string.close_box_asked_line_plain)
                }
            }
            CloseBoxStep.Agree -> stringResource(Res.string.close_box_agree_line)
            CloseBoxStep.CoolingOff -> stringResource(Res.string.close_box_cooling_line)
        }
        ScreenHeader(eyebrow = heading, heading = heading)
        Spacer(Modifier.height(spacing.lg))
        MoyiText(line, tone = TextTone.Muted)
    }
}

/** An ending screen's error, 8 above the action that caused it, as every settings editor does. */
@Composable
private fun EndingError(state: BondSettingsState, field: SettingsField) {
    state.error?.takeIf { it.field == field }?.let {
        InlineError(it.message.asString())
        Spacer(Modifier.height(MoyiTheme.spacing.sm))
    }
}

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

private fun ready(settings: BondSettings = previewBond, withdrawOnBlock: Boolean = true) =
    BondSettingsState(load = SettingsLoad.Ready, settings = settings, withdrawOnBlock = withdrawOnBlock)

@Preview
@Composable
private fun LeavePreview() {
    MoyiTheme(darkTheme = true) { LeaveScreen(ready(), onAction = {}, onBack = {}) }
}

@Preview
@Composable
private fun BlockPreview() {
    MoyiTheme(darkTheme = true) { BlockScreen(ready(), onAction = {}, onBack = {}) }
}

@Preview
@Composable
private fun BlockWithdrawOffLightPreview() {
    MoyiTheme(darkTheme = false) { BlockScreen(ready(withdrawOnBlock = false), onAction = {}, onBack = {}) }
}

@Preview
@Composable
private fun CloseBoxAskPreview() {
    MoyiTheme(darkTheme = true) { CloseBoxScreen(ready(), onAction = {}, onBack = {}) }
}

@Preview
@Composable
private fun CloseBoxAskedPreview() {
    val asked = previewBond.copy(pendingDeletion = PendingDeletionRequest(requestedByMe = true, expiresAt = Instant.parse("2026-11-13T10:00:00Z")))
    MoyiTheme(darkTheme = true) { CloseBoxScreen(ready(asked), onAction = {}, onBack = {}) }
}

@Preview
@Composable
private fun CloseBoxAgreePreview() {
    val theirs = previewBond.copy(pendingDeletion = PendingDeletionRequest(requestedByMe = false))
    MoyiTheme(darkTheme = true) { CloseBoxScreen(ready(theirs), onAction = {}, onBack = {}) }
}

@Preview
@Composable
private fun CloseBoxCoolingOffLightPreview() {
    val cooling = previewBond.copy(status = BondStatus.PENDING_DELETION, deletionScheduledFor = Instant.parse("2026-12-06T10:00:00Z"))
    MoyiTheme(darkTheme = false) { CloseBoxScreen(ready(cooling), onAction = {}, onBack = {}) }
}
