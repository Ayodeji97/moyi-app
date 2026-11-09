package com.moyi.app.feature.bond.report

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.moyi.app.designsystem.MoyiTheme
import com.moyi.app.designsystem.component.Field
import com.moyi.app.designsystem.component.FieldType
import com.moyi.app.designsystem.component.InlineError
import com.moyi.app.designsystem.component.MoyiScaffold
import com.moyi.app.designsystem.component.MoyiText
import com.moyi.app.designsystem.component.PrimaryAction
import com.moyi.app.designsystem.component.PrimaryActionState
import com.moyi.app.designsystem.component.ScreenHeader
import com.moyi.app.designsystem.component.TextLink
import com.moyi.app.designsystem.component.TextTone
import com.moyi.app.designsystem.presentation.UiText
import com.moyi.app.domain.report.ReportReason
import com.moyi.app.feature.bond.resources.Res
import com.moyi.app.feature.bond.resources.report_detail_helper
import com.moyi.app.feature.bond.resources.report_detail_label
import com.moyi.app.feature.bond.resources.report_error_detail_long
import com.moyi.app.feature.bond.resources.report_error_failed
import com.moyi.app.feature.bond.resources.report_error_reason_missing
import com.moyi.app.feature.bond.resources.report_eyebrow
import com.moyi.app.feature.bond.resources.report_heading
import com.moyi.app.feature.bond.resources.report_reason_harassment
import com.moyi.app.feature.bond.resources.report_reason_label
import com.moyi.app.feature.bond.resources.report_reason_minor
import com.moyi.app.feature.bond.resources.report_reason_non_consensual
import com.moyi.app.feature.bond.resources.report_reason_none
import com.moyi.app.feature.bond.resources.report_reason_other
import com.moyi.app.feature.bond.resources.report_reason_pick_hint
import com.moyi.app.feature.bond.resources.report_reason_violence
import com.moyi.app.feature.bond.resources.report_retry
import com.moyi.app.feature.bond.resources.report_send
import com.moyi.app.feature.bond.resources.report_sending
import com.moyi.app.feature.bond.resources.report_sent_back_day
import com.moyi.app.feature.bond.resources.report_sent_back_settings
import com.moyi.app.feature.bond.resources.report_sent_heading
import com.moyi.app.feature.bond.resources.report_sent_line
import com.moyi.app.feature.bond.resources.report_sent_line_unnamed
import com.moyi.app.feature.bond.resources.report_sent_media_line
import com.moyi.app.feature.bond.resources.report_target_entry
import com.moyi.app.feature.bond.resources.report_target_heading
import com.moyi.app.feature.bond.resources.report_target_member_unnamed
import com.moyi.app.feature.bond.settings.RowKind
import com.moyi.app.feature.bond.settings.SettingsRow
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * `Safety · report · choose target  ·  IDLE` (Figma 607:1795): the heading, then two rows,
 * this day's entry and the member, each 56 and underlined (it goes somewhere). Reached
 * only from a day view that shows the partner's entry; the rows reuse Bond settings' row.
 */
@Composable
fun ReportTargetScreen(state: ReportState, onAction: (ReportAction) -> Unit, onBack: () -> Unit) {
    MoyiScaffold(onBack = onBack) {
        ScreenHeader(eyebrow = stringResource(Res.string.report_eyebrow), heading = stringResource(Res.string.report_target_heading))
        Spacer(Modifier.height(MoyiTheme.spacing.lg))
        if (state.canReportEntry) {
            SettingsRow(
                label = stringResource(Res.string.report_target_entry),
                kind = RowKind.Navigates,
                onClick = { onAction(ReportAction.OnSubjectChosen(ReportSubject.Entry)) },
            )
        }
        SettingsRow(
            label = state.partnerName ?: stringResource(Res.string.report_target_member_unnamed),
            kind = RowKind.Navigates,
            onClick = { onAction(ReportAction.OnSubjectChosen(ReportSubject.Member)) },
        )
    }
}

/**
 * `Safety · report · compose  ·  IDLE` (599:1782) and `· failed · INPUT KEPT` (608:1841):
 * the heading, the reason (`Field` Select, opening the picker), the detail (`Field`
 * Multiline) with the line that the other member is not told, and Send report pinned at
 * the bottom. A failure is the error line 8 above Send, with everything typed kept.
 */
@Composable
fun ReportComposeScreen(state: ReportState, onAction: (ReportAction) -> Unit, onBack: () -> Unit) {
    val spacing = MoyiTheme.spacing
    MoyiScaffold(
        onBack = onBack,
        action = {
            state.error?.let {
                InlineError(it.asString())
                Spacer(Modifier.height(spacing.sm))
            }
            PrimaryAction(
                text = stringResource(if (state.retryable) Res.string.report_retry else Res.string.report_send),
                onClick = { onAction(ReportAction.OnSendClick) },
                state = if (state.sending) PrimaryActionState.Sending(stringResource(Res.string.report_sending)) else PrimaryActionState.Idle,
            )
        },
    ) {
        ScreenHeader(eyebrow = stringResource(Res.string.report_eyebrow), heading = stringResource(Res.string.report_heading))
        Spacer(Modifier.height(spacing.lg))
        Field(
            value = state.reason?.let { stringResource(it.label()) } ?: stringResource(Res.string.report_reason_none),
            onValueChange = {},
            label = stringResource(Res.string.report_reason_label),
            type = FieldType.Select,
            error = if (state.reasonMissing) stringResource(Res.string.report_error_reason_missing) else null,
            enabled = !state.sending,
            onClick = { onAction(ReportAction.OnReasonFieldClick) },
        )
        Spacer(Modifier.height(spacing.lg))
        Field(
            value = state.detail,
            onValueChange = { onAction(ReportAction.OnDetailChange(it)) },
            label = stringResource(Res.string.report_detail_label),
            type = FieldType.Multiline,
            helper = stringResource(Res.string.report_detail_helper),
            error = if (state.detailTooLong) stringResource(Res.string.report_error_detail_long) else null,
            enabled = !state.sending,
        )
    }
}

/**
 * The reason picker the `Field` Select opens (not drawn: missing-components §53). The
 * contract's five reasons as rows; a tap chooses one and goes back to the form, whose
 * field then shows it. The shape of the time-zone picker's list, with no mark of its own.
 */
@Composable
fun ReportReasonScreen(state: ReportState, onAction: (ReportAction) -> Unit, onBack: () -> Unit) {
    val hint = stringResource(Res.string.report_reason_pick_hint)
    MoyiScaffold(onBack = onBack) {
        val heading = stringResource(Res.string.report_reason_label)
        ScreenHeader(eyebrow = stringResource(Res.string.report_eyebrow), heading = heading)
        Spacer(Modifier.height(MoyiTheme.spacing.lg))
        state.reasons.forEach { reason ->
            SettingsRow(
                label = stringResource(reason.label()),
                kind = RowKind.Acts,
                onClickLabel = hint,
                onClick = { onAction(ReportAction.OnReasonPicked(reason)) },
            )
        }
    }
}

/**
 * `Safety · report · sent  ·  ACKNOWLEDGED` (608:1811): a root, so the eyebrow shows and
 * there is no back (SKILL.md: an acknowledgement carries one labelled action that already
 * returns the reader). The heading, the line that the other member is not told, and, for
 * an entry, what happens to its photo and voice note. The one action goes back to where
 * the report started. No congratulation (`26` §2.2).
 */
@Composable
fun ReportSentScreen(state: ReportState, reportedEntry: Boolean, onAction: (ReportAction) -> Unit) {
    val spacing = MoyiTheme.spacing
    MoyiScaffold(
        action = {
            TextLink(
                text = stringResource(if (state.fromSettings) Res.string.report_sent_back_settings else Res.string.report_sent_back_day),
                onClick = { onAction(ReportAction.OnDoneClick) },
                centered = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) {
        ScreenHeader(eyebrow = stringResource(Res.string.report_eyebrow), heading = stringResource(Res.string.report_sent_heading))
        Spacer(Modifier.height(spacing.md))
        MoyiText(
            state.partnerName?.let { stringResource(Res.string.report_sent_line, it) } ?: stringResource(Res.string.report_sent_line_unnamed),
            tone = TextTone.Muted,
        )
        if (reportedEntry) {
            Spacer(Modifier.height(spacing.md))
            MoyiText(stringResource(Res.string.report_sent_media_line), tone = TextTone.Muted)
        }
    }
}

/** Each reason's words (all PROPOSED but two, strings.xml). */
internal fun ReportReason.label(): StringResource = when (this) {
    ReportReason.HARASSMENT -> Res.string.report_reason_harassment
    ReportReason.NON_CONSENSUAL -> Res.string.report_reason_non_consensual
    ReportReason.VIOLENCE_OR_SELF_HARM -> Res.string.report_reason_violence
    ReportReason.MINOR_SEXUAL_CONTENT -> Res.string.report_reason_minor
    ReportReason.OTHER -> Res.string.report_reason_other
}

// ---- Previews ----

private val previewState = ReportState(partnerName = "Tunde", canReportEntry = true, subject = ReportSubject.Entry)

@Preview
@Composable
private fun ReportTargetPreview() {
    MoyiTheme(darkTheme = true) { ReportTargetScreen(previewState, onAction = {}, onBack = {}) }
}

@Preview
@Composable
private fun ReportComposePreview() {
    MoyiTheme(darkTheme = true) { ReportComposeScreen(previewState.copy(reason = ReportReason.HARASSMENT), onAction = {}, onBack = {}) }
}

@Preview
@Composable
private fun ReportComposeFailedPreview() {
    MoyiTheme(darkTheme = false) {
        ReportComposeScreen(
            previewState.copy(
                reason = ReportReason.OTHER,
                detail = "They said it again.",
                error = UiText.Resource(Res.string.report_error_failed),
                retryable = true,
            ),
            onAction = {},
            onBack = {},
        )
    }
}

@Preview
@Composable
private fun ReportReasonPreview() {
    MoyiTheme(darkTheme = true) { ReportReasonScreen(previewState.copy(reason = ReportReason.OTHER), onAction = {}, onBack = {}) }
}

@Preview
@Composable
private fun ReportSentPreview() {
    MoyiTheme(darkTheme = false) { ReportSentScreen(previewState, reportedEntry = true, onAction = {}) }
}
