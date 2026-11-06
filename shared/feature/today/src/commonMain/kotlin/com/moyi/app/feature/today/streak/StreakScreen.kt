package com.moyi.app.feature.today.streak

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moyi.app.designsystem.MoyiTheme
import com.moyi.app.designsystem.component.DayCell
import com.moyi.app.designsystem.component.DayCellState
import com.moyi.app.designsystem.component.MoyiScaffold
import com.moyi.app.designsystem.component.MoyiText
import com.moyi.app.designsystem.component.StateBlock
import com.moyi.app.designsystem.component.TextRole
import com.moyi.app.designsystem.presentation.ObserveAsEvents
import com.moyi.app.designsystem.theme.MoyiDimens
import com.moyi.app.domain.streak.Streak
import com.moyi.app.domain.streak.StreakDay
import com.moyi.app.domain.streak.StreakDayStatus
import com.moyi.app.feature.today.common.ScreenTitle
import com.moyi.app.feature.today.common.dayAndMonth
import com.moyi.app.feature.today.common.debugLongPress
import com.moyi.app.feature.today.resources.Res
import com.moyi.app.feature.today.resources.streak_cell_complete
import com.moyi.app.feature.today.resources.streak_cell_none
import com.moyi.app.feature.today.resources.streak_cell_rest
import com.moyi.app.feature.today.resources.streak_cell_today
import com.moyi.app.feature.today.resources.streak_ended
import com.moyi.app.feature.today.resources.streak_ended_strict
import com.moyi.app.feature.today.resources.streak_key_complete
import com.moyi.app.feature.today.resources.streak_key_none
import com.moyi.app.feature.today.resources.streak_key_rest
import com.moyi.app.feature.today.resources.streak_key_today
import com.moyi.app.feature.today.resources.streak_longest
import com.moyi.app.feature.today.resources.streak_now
import com.moyi.app.feature.today.resources.streak_rest_day_used
import com.moyi.app.feature.today.resources.streak_rest_day_used_none
import com.moyi.app.feature.today.resources.streak_rest_days_left
import com.moyi.app.feature.today.resources.streak_rest_days_none
import com.moyi.app.feature.today.resources.streak_strict
import com.moyi.app.feature.today.resources.streak_title
import com.moyi.app.feature.today.resources.streak_window
import com.moyi.app.feature.today.resources.today_error_action
import com.moyi.app.feature.today.resources.today_error_heading
import com.moyi.app.feature.today.resources.today_error_line
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun StreakRoot(
    onBack: () -> Unit,
    debugTools: Boolean,
    viewModel: StreakViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            StreakEvent.NavigateBack -> onBack()
        }
    }
    StreakScreen(state = state, onAction = viewModel::onAction, debugTools = debugTools)
}

/**
 * The Streak screen (Figma 227:3 current, 401:70 rest day used, 401:160 ended, 401:250
 * strict mode; light 281:847, 401:1800, 401:1860, 401:1920). Reassure, never pressure
 * (states.md §7): no points, badges, levels, red or loss language, and no milestone, since
 * no frame draws one.
 *
 * The back target at 24, then, 32 apart: the title, the two counts (`label` over `entry`
 * numerals), "The last four weeks", the calendar (rows of seven DayCells, 8 apart), the key
 * and the one footer line. The title sits at 96, 24 under the back, as on every screen with
 * one (ScreenHeader), where Figma draws 104.
 *
 * @param debugTools `-PmoyiUiPreview=today` only: a long press on the title steps the preview's states.
 */
@Composable
fun StreakScreen(state: StreakState, onAction: (StreakAction) -> Unit, debugTools: Boolean = false) {
    when (state.load) {
        StreakLoad.Loading -> Box(Modifier.fillMaxSize().background(MoyiTheme.colors.surface))
        StreakLoad.Failed -> MoyiScaffold(onBack = { onAction(StreakAction.OnBackClick) }, scrollContent = false) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                StateBlock(
                    heading = stringResource(Res.string.today_error_heading),
                    line = stringResource(Res.string.today_error_line),
                    actionLabel = stringResource(Res.string.today_error_action),
                    onAction = { onAction(StreakAction.OnRetryClick) },
                )
            }
        }
        StreakLoad.Ready -> state.streak?.let { Ready(it, onAction, debugTools) }
    }
}

@Composable
private fun Ready(streak: StreakUi, onAction: (StreakAction) -> Unit, debugTools: Boolean) {
    val spacing = MoyiTheme.spacing
    MoyiScaffold(onBack = { onAction(StreakAction.OnBackClick) }) {
        Column(Modifier.fillMaxWidth()) {
            Spacer(Modifier.height(spacing.lg - MoyiDimens.Scaffold.ItemSpacing))
            ScreenTitle(
                text = stringResource(Res.string.streak_title),
                modifier = Modifier.fillMaxWidth().debugLongPress(if (debugTools) ({ onAction(StreakAction.OnDebugCycle) }) else null),
            )
            Spacer(Modifier.height(spacing.xl))
            Counts(streak.current, streak.longest)
            Spacer(Modifier.height(spacing.xl))
            MoyiText(
                text = stringResource(Res.string.streak_window),
                role = TextRole.Label,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(spacing.xl))
            Calendar(streak.cells)
            Spacer(Modifier.height(spacing.xl))
            Key(streak.showRestDayKey)
            Spacer(Modifier.height(spacing.xl))
            MoyiText(text = footerText(streak.footer), role = TextRole.Body)
        }
    }
}

/** Figma 267:31: "Now" and "Longest", each `label` over an `entry` numeral, 48 apart. One stop each for a screen reader. */
@Composable
private fun Counts(current: Int, longest: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(MoyiTheme.spacing.xxl)) {
        Count(stringResource(Res.string.streak_now), current)
        Count(stringResource(Res.string.streak_longest), longest)
    }
}

@Composable
private fun Count(label: String, value: Int) {
    Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(MoyiTheme.spacing.xs)) {
        MoyiText(text = label, role = TextRole.Label)
        MoyiText(text = value.toString(), role = TextRole.Entry)
    }
}

/**
 * Figma 267:39: rows of seven 48 cells across the width, 8 between rows. A rolling window,
 * not a month: the rows are not weeks and carry no weekday header. A short last row (a young
 * bond) keeps its cells at the same columns.
 */
@Composable
private fun Calendar(cells: List<StreakCell>) {
    Column(verticalArrangement = Arrangement.spacedBy(MoyiTheme.spacing.sm)) {
        cells.chunked(DAYS_PER_ROW).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                row.forEach { cell -> CalendarCell(cell) }
                repeat(DAYS_PER_ROW - row.size) { Spacer(Modifier.size(MoyiDimens.DayCell.Size)) }
            }
        }
    }
}

@Composable
private fun CalendarCell(cell: StreakCell) {
    val day = remember(cell.date) { dayAndMonth(cell.date) }
    val description = stringResource(
        when (cell.state) {
            DayCellState.Complete -> Res.string.streak_cell_complete
            DayCellState.Frozen -> Res.string.streak_cell_rest
            DayCellState.Today -> Res.string.streak_cell_today
            DayCellState.Missed -> Res.string.streak_cell_none
        },
        day,
    )
    DayCell(
        state = cell.state,
        numeral = cell.date.day.toString(),
        modifier = Modifier.clearAndSetSemantics { contentDescription = description },
    )
}

/**
 * Figma 267:100: each swatch beside its own label, 16 between entries, wrapping at large
 * text; "No entry (left blank)" trails on its own line with no swatch (states.md §7). The
 * Rest day entry is absent in Strict mode (401:296).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Key(showRestDay: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(MoyiTheme.spacing.sm)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(MoyiTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(MoyiTheme.spacing.sm),
        ) {
            KeyEntry(DayCellState.Complete, stringResource(Res.string.streak_key_complete))
            if (showRestDay) KeyEntry(DayCellState.Frozen, stringResource(Res.string.streak_key_rest))
            KeyEntry(DayCellState.Today, stringResource(Res.string.streak_key_today))
        }
        MoyiText(text = stringResource(Res.string.streak_key_none), role = TextRole.Label)
    }
}

@Composable
private fun KeyEntry(state: DayCellState, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        DayCell(state = state, numeral = null, size = MoyiDimens.DayCell.KeySize)
        Spacer(Modifier.width(MoyiTheme.spacing.sm))
        MoyiText(text = label, role = TextRole.Label)
    }
}

@Composable
private fun footerText(footer: StreakFooter): String = when (footer) {
    is StreakFooter.Ended -> stringResource(if (footer.strict) Res.string.streak_ended_strict else Res.string.streak_ended)
    StreakFooter.StrictMode -> stringResource(Res.string.streak_strict)
    is StreakFooter.RestDayUsed ->
        if (footer.left == 0) stringResource(Res.string.streak_rest_day_used_none)
        else pluralStringResource(Res.plurals.streak_rest_day_used, footer.left, footer.left)
    is StreakFooter.RestDaysLeft ->
        if (footer.left == 0) stringResource(Res.string.streak_rest_days_none)
        else pluralStringResource(Res.plurals.streak_rest_days_left, footer.left, footer.left)
}

private const val DAYS_PER_ROW = 7

@Preview
@Composable
private fun StreakCurrentPreview() {
    MoyiTheme(darkTheme = true) { StreakScreen(previewState(strict = false), onAction = {}) }
}

@Preview
@Composable
private fun StreakStrictPreview() {
    MoyiTheme(darkTheme = false) { StreakScreen(previewState(strict = true), onAction = {}) }
}

private fun previewState(strict: Boolean): StreakState {
    val days = (1..28).map { day ->
        val status = when (day) {
            28 -> StreakDayStatus.OPEN
            5 -> StreakDayStatus.MISSED
            12 -> StreakDayStatus.FROZEN
            else -> StreakDayStatus.COMPLETE
        }
        StreakDay("2025-08-${day.toString().padStart(2, '0')}", status)
    }
    val streak = Streak(22, 41, if (strict) 0 else 2, 0, strict, 57, "2025-08-27", days)
    return StreakState(StreakLoad.Ready, streak.toStreakUi())
}
