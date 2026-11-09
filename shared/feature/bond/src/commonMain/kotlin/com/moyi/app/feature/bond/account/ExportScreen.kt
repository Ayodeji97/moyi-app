package com.moyi.app.feature.bond.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.moyi.app.designsystem.MoyiTheme
import com.moyi.app.designsystem.component.InlineError
import com.moyi.app.designsystem.component.MoyiScaffold
import com.moyi.app.designsystem.component.MoyiText
import com.moyi.app.designsystem.component.PrimaryAction
import com.moyi.app.designsystem.component.PrimaryActionState
import com.moyi.app.designsystem.component.ScreenHeader
import com.moyi.app.designsystem.component.StateBlock
import com.moyi.app.designsystem.component.TextTone
import com.moyi.app.domain.account.DataExportStatus
import com.moyi.app.feature.bond.resources.Res
import com.moyi.app.feature.bond.resources.export_download
import com.moyi.app.feature.bond.resources.export_expired_heading
import com.moyi.app.feature.bond.resources.export_expired_line
import com.moyi.app.feature.bond.resources.export_failed_heading
import com.moyi.app.feature.bond.resources.export_failed_line
import com.moyi.app.feature.bond.resources.export_heading
import com.moyi.app.feature.bond.resources.export_line
import com.moyi.app.feature.bond.resources.export_preparing_heading
import com.moyi.app.feature.bond.resources.export_preparing_line
import com.moyi.app.feature.bond.resources.export_ready_heading
import com.moyi.app.feature.bond.resources.export_ready_line
import com.moyi.app.feature.bond.resources.export_ready_line_unsized
import com.moyi.app.feature.bond.resources.export_ready_private
import com.moyi.app.feature.bond.resources.export_request
import com.moyi.app.feature.bond.resources.export_requesting
import com.moyi.app.feature.bond.resources.export_size_kb
import com.moyi.app.feature.bond.resources.export_size_mb
import com.moyi.app.feature.bond.resources.settings_load_error_action
import com.moyi.app.feature.bond.resources.settings_load_error_heading
import com.moyi.app.feature.bond.resources.settings_load_error_line
import com.moyi.app.feature.bond.settings.localDayAndMonth
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToLong
import kotlin.time.Instant

/**
 * Export your archive (FR-009, FR-094; states.md §9 `Account 8 · export`; words PROPOSED).
 * Back, the heading at 96, its line, then one action, by [ExportState.status]:
 *
 * - **never asked**, EXPIRED, FAILED: what the archive holds and how it arrives, and
 *   **Prepare my archive**.
 * - **PREPARING**: the drawn acknowledgement. No action and no progress: there is nothing to
 *   watch (§3 refuses spinners). The screen polls quietly while it is open.
 * - **READY**: its size, the day and time the download stops working (24 hours), that the
 *   file is readable by anyone who has it, and **Download**, which opens the signed URL in
 *   the phone's browser.
 */
@Composable
fun ExportScreen(
    state: ExportState,
    onAction: (ExportAction) -> Unit,
    onBack: () -> Unit,
) {
    when (state.load) {
        ExportLoad.Loading -> Box(Modifier.fillMaxSize().background(MoyiTheme.colors.surface))
        ExportLoad.Failed -> MoyiScaffold(onBack = onBack, contentArrangement = Arrangement.Center) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                StateBlock(
                    heading = stringResource(Res.string.settings_load_error_heading),
                    line = stringResource(Res.string.settings_load_error_line),
                    actionLabel = stringResource(Res.string.settings_load_error_action),
                    onAction = { onAction(ExportAction.OnRetryClick) },
                )
            }
        }
        ExportLoad.Ready -> ExportContent(state, onAction, onBack)
    }
}

@Composable
private fun ExportContent(state: ExportState, onAction: (ExportAction) -> Unit, onBack: () -> Unit) {
    val spacing = MoyiTheme.spacing
    val status = state.status
    MoyiScaffold(
        onBack = onBack,
        action = when (status) {
            DataExportStatus.PREPARING -> null
            DataExportStatus.READY -> {
                { PrimaryAction(text = stringResource(Res.string.export_download), onClick = { onAction(ExportAction.OnDownloadClick) }) }
            }
            null, DataExportStatus.EXPIRED, DataExportStatus.FAILED -> {
                {
                    state.error?.let {
                        InlineError(it.asString())
                        Spacer(Modifier.height(spacing.sm))
                    }
                    PrimaryAction(
                        text = stringResource(Res.string.export_request),
                        onClick = { onAction(ExportAction.OnRequestClick) },
                        state = if (state.requesting) PrimaryActionState.Sending(stringResource(Res.string.export_requesting)) else PrimaryActionState.Idle,
                    )
                }
            }
        },
    ) {
        val heading = stringResource(
            when (status) {
                null -> Res.string.export_heading
                DataExportStatus.PREPARING -> Res.string.export_preparing_heading
                DataExportStatus.READY -> Res.string.export_ready_heading
                DataExportStatus.EXPIRED -> Res.string.export_expired_heading
                DataExportStatus.FAILED -> Res.string.export_failed_heading
            },
        )
        ScreenHeader(eyebrow = heading, heading = heading)
        Spacer(Modifier.height(spacing.md))
        when (status) {
            null -> MoyiText(stringResource(Res.string.export_line), tone = TextTone.Muted)
            DataExportStatus.PREPARING -> MoyiText(stringResource(Res.string.export_preparing_line), tone = TextTone.Muted)
            DataExportStatus.READY -> {
                MoyiText(readyLine(state), tone = TextTone.Muted)
                Spacer(Modifier.height(spacing.md))
                MoyiText(stringResource(Res.string.export_ready_private), tone = TextTone.Muted)
            }
            DataExportStatus.EXPIRED -> {
                MoyiText(stringResource(Res.string.export_expired_line), tone = TextTone.Muted)
                Spacer(Modifier.height(spacing.md))
                MoyiText(stringResource(Res.string.export_line), tone = TextTone.Muted)
            }
            DataExportStatus.FAILED -> MoyiText(stringResource(Res.string.export_failed_line), tone = TextTone.Muted)
        }
    }
}

@Composable
private fun readyLine(state: ExportState): String {
    val expiresAt = state.expiresAt ?: return stringResource(Res.string.export_expired_line)
    val day = localDayAndMonth(expiresAt)
    val time = localTime(expiresAt)
    val size = state.sizeBytes
    return if (size == null) {
        stringResource(Res.string.export_ready_line_unsized, day, time)
    } else {
        val sizeText = if (size >= BYTES_PER_MB) {
            stringResource(Res.string.export_size_mb, oneDecimal(size.toDouble() / BYTES_PER_MB))
        } else {
            stringResource(Res.string.export_size_kb, maxOf(1, size / BYTES_PER_KB).toString())
        }
        stringResource(Res.string.export_ready_line, sizeText, day, time)
    }
}

/** The 24-hour clock time [instant] falls on where the device is, as the reveal time is written ("17:30"). */
internal fun localTime(instant: Instant): String {
    val time = instant.toLocalDateTime(TimeZone.currentSystemDefault()).time
    return "${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}"
}

/** 2.14 is "2.1". Megabytes as storage counts them (1,000,000 bytes). */
internal fun oneDecimal(value: Double): String {
    val tenths = (value * 10).roundToLong()
    return "${tenths / 10}.${tenths % 10}"
}

private const val BYTES_PER_MB = 1_000_000L
private const val BYTES_PER_KB = 1_000L

@Preview
@Composable
private fun ExportNeverPreview() {
    MoyiTheme(darkTheme = true) { ExportScreen(ExportState(load = ExportLoad.Ready), onAction = {}, onBack = {}) }
}

@Preview
@Composable
private fun ExportPreparingPreview() {
    MoyiTheme(darkTheme = true) { ExportScreen(ExportState(load = ExportLoad.Ready, status = DataExportStatus.PREPARING), onAction = {}, onBack = {}) }
}

@Preview
@Composable
private fun ExportReadyLightPreview() {
    MoyiTheme(darkTheme = false) {
        ExportScreen(
            ExportState(load = ExportLoad.Ready, status = DataExportStatus.READY, expiresAt = Instant.parse("2026-11-10T17:30:00Z"), sizeBytes = 2_140_000),
            onAction = {},
            onBack = {},
        )
    }
}
