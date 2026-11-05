package com.moyi.app.feature.today.archive

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moyi.app.designsystem.MoyiTheme
import com.moyi.app.designsystem.component.InlineError
import com.moyi.app.designsystem.component.MoyiScaffold
import com.moyi.app.designsystem.component.MoyiText
import com.moyi.app.designsystem.component.PrimaryAction
import com.moyi.app.designsystem.component.StateBlock
import com.moyi.app.designsystem.component.TextLink
import com.moyi.app.designsystem.component.TextRole
import com.moyi.app.designsystem.component.TextTone
import com.moyi.app.designsystem.presentation.ObserveAsEvents
import com.moyi.app.designsystem.theme.MoyiDimens
import com.moyi.app.feature.today.common.FakeCopy
import com.moyi.app.feature.today.common.ScreenTitle
import com.moyi.app.feature.today.common.debugLongPress
import com.moyi.app.feature.today.common.monthAndYear
import com.moyi.app.feature.today.common.weekdayAndDay
import com.moyi.app.feature.today.resources.Res
import com.moyi.app.feature.today.resources.archive_empty_heading
import com.moyi.app.feature.today.resources.archive_empty_line
import com.moyi.app.feature.today.resources.archive_title
import com.moyi.app.feature.today.resources.today_action_failed
import com.moyi.app.feature.today.resources.today_author_partner
import com.moyi.app.feature.today.resources.today_author_you
import com.moyi.app.feature.today.resources.today_error_action
import com.moyi.app.feature.today.resources.today_error_heading
import com.moyi.app.feature.today.resources.today_error_line
import com.moyi.app.feature.today.resources.today_tombstone_line
import com.moyi.app.feature.today.resources.today_write_action
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ArchiveRoot(
    onBack: () -> Unit,
    onOpenDay: (date: LocalDate) -> Unit,
    onWrite: () -> Unit,
    debugTools: Boolean,
    viewModel: ArchiveViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            ArchiveEvent.NavigateBack -> onBack()
            is ArchiveEvent.NavigateToDay -> onOpenDay(event.date)
            ArchiveEvent.NavigateToWrite -> onWrite()
        }
    }
    ArchiveScreen(state = state, onAction = viewModel::onAction, debugTools = debugTools)
}

/**
 * The Archive (Figma 223:3, empty 223:25). The back target at y 24, the title
 * "Your days" at 96 (24 under the back, as on every screen with one), then the list.
 *
 * Owed and **not drawn** (docs/figma/archive-check.md): the month row with "Jump to",
 * the All days / Favourites / Search chips, the favourite bookmarks and the "Your
 * streak" link. Without the navigation block the list starts at 168, not Figma's 336,
 * and each month is named by a heading in the list instead of by the month row.
 *
 * @param debugTools `-PmoyiUiPreview=today` only: a long press on the title steps list, empty, error.
 */
@Composable
fun ArchiveScreen(
    state: ArchiveState,
    onAction: (ArchiveAction) -> Unit,
    debugTools: Boolean = false,
) {
    if (state.load == ArchiveLoad.Loading) {
        Box(Modifier.fillMaxSize().background(MoyiTheme.colors.surface))
        return
    }
    MoyiScaffold(onBack = { onAction(ArchiveAction.OnBackClick) }, scrollContent = false) {
        Spacer(Modifier.height(MoyiTheme.spacing.lg - MoyiDimens.Scaffold.ItemSpacing))
        ScreenTitle(
            text = stringResource(Res.string.archive_title),
            modifier = Modifier
                .fillMaxWidth()
                .height(MoyiDimens.TouchTarget)
                .debugLongPress(if (debugTools) ({ onAction(ArchiveAction.OnDebugCycle) }) else null),
        )
        Spacer(Modifier.height(MoyiTheme.spacing.lg))
        when {
            state.load == ArchiveLoad.Failed -> Failed(onRetry = { onAction(ArchiveAction.OnRetryClick) })
            state.isEmpty -> Empty(onWrite = { onAction(ArchiveAction.OnWriteClick) })
            else -> DayList(state, onAction)
        }
    }
}

@Composable
private fun ColumnScope.Failed(onRetry: () -> Unit) {
    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
        StateBlock(
            heading = stringResource(Res.string.today_error_heading),
            line = stringResource(Res.string.today_error_line),
            actionLabel = stringResource(Res.string.today_error_action),
            onAction = onRetry,
        )
    }
}

/**
 * 223:25. Quiet and forward-looking (states.md §6): the StateBlock with the two lines
 * of the table, and Write, the one thing that will put a day here. The ghost day drawn
 * above it (265:15) is not a design-system component, so it is owed, not improvised.
 */
@Composable
private fun ColumnScope.Empty(onWrite: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().weight(1f),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StateBlock(
            heading = stringResource(Res.string.archive_empty_heading),
            line = stringResource(Res.string.archive_empty_line),
        )
        Spacer(Modifier.height(MoyiTheme.spacing.lg))
        PrimaryAction(text = stringResource(Res.string.today_write_action), onClick = onWrite)
    }
}

@Composable
private fun ColumnScope.DayList(state: ArchiveState, onAction: (ArchiveAction) -> Unit) {
    val listState = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            last >= info.totalItemsCount - LOAD_AHEAD
        }
    }
    // Keyed on the page count too: a page that drew nothing new asks again.
    LaunchedEffect(nearEnd, state.canLoadMore, state.pages) {
        if (nearEnd && state.canLoadMore) onAction(ArchiveAction.OnLoadMore)
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth().weight(1f),
        verticalArrangement = Arrangement.spacedBy(MoyiTheme.spacing.md),
    ) {
        state.months.forEach { month ->
            item(key = "month-${month.month.year}-${month.month.month}") {
                MonthHeading(month.month)
            }
            items(month.days, key = { it.date.toString() }) { day ->
                DayCard(day, state.partnerName.orEmpty(), onClick = { onAction(ArchiveAction.OnDayClick(day.date)) })
            }
        }
        if (state.pageFailed) {
            item(key = "page-failed") {
                Column(Modifier.fillMaxWidth()) {
                    InlineError(stringResource(Res.string.today_action_failed))
                    TextLink(
                        text = stringResource(Res.string.today_error_action),
                        onClick = { onAction(ArchiveAction.OnRetryPageClick) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/**
 * The month a group of days belongs to, "September 2026", in `label` and announced
 * as a heading. Interpreted: Figma names the month only in the month-jump row, which
 * is owed; this keeps the grouping visible without it.
 */
@Composable
private fun MonthHeading(month: LocalDate) {
    val name = remember(month) { monthAndYear(month) }
    MoyiText(text = name, role = TextRole.Label, modifier = Modifier.semantics { heading() })
}

/**
 * One day (Figma DayCard 223:9, 358 x 259): `surfaceRaised` with the `outline`
 * hairline and radius 12, padding 16; the day ("Tuesday 2") in `label`, then up to
 * two blocks 16 apart, each an author label over the words in `title` (tokens.md §2:
 * "also the preview-entry size in Archive"), **cut to two lines with an ellipsis**.
 * No chevron (states.md §6). The whole card opens the day view.
 *
 * The partner's block is drawn only when the day holds their words; a locked entry is
 * not even named. My deleted entry is its tombstone, "From you", as on Today.
 *
 * TODO(#17): R3. The partner's words here are not protected from screen capture on this
 *  branch; when #17 merges, give this card the protection EntryCard Partner gets there.
 */
@Composable
private fun DayCard(day: ArchiveDayUi, partnerName: String, onClick: () -> Unit) {
    val colors = MoyiTheme.colors
    val shapes = MoyiTheme.shapes
    val dayName = remember(day.date) { weekdayAndDay(day.date) }
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.surfaceRaised, shapes.radius)
            .border(MoyiDimens.EntryCard.Stroke, colors.outline, shapes.radius)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(MoyiTheme.spacing.md)
            // One stop for a screen reader: the day, then each entry.
            .semantics(mergeDescendants = true) {},
    ) {
        MoyiText(text = dayName, role = TextRole.Label)
        day.partnerEntry?.let { words ->
            Spacer(Modifier.height(MoyiTheme.spacing.md))
            CardEntry(stringResource(Res.string.today_author_partner, partnerName), words)
        }
        when {
            day.myEntry != null -> {
                Spacer(Modifier.height(MoyiTheme.spacing.md))
                CardEntry(stringResource(Res.string.today_author_you), day.myEntry)
            }
            day.myTombstone -> {
                Spacer(Modifier.height(MoyiTheme.spacing.md))
                MoyiText(text = stringResource(Res.string.today_author_you), role = TextRole.Label)
                Spacer(Modifier.height(MoyiTheme.spacing.xs))
                MoyiText(text = stringResource(Res.string.today_tombstone_line), tone = TextTone.Muted)
            }
        }
    }
}

@Composable
private fun CardEntry(author: String, words: String) {
    MoyiText(text = author, role = TextRole.Label)
    Spacer(Modifier.height(MoyiTheme.spacing.xs))
    MoyiText(text = words, role = TextRole.Title, maxLines = 2, ellipsis = true)
}

/** Ask for the next page this many items before the end. Not a design value. */
private const val LOAD_AHEAD = 3

// ---- Previews ----

private fun previewDays(): List<ArchiveMonth> {
    val days = listOf(
        ArchiveDayUi(FakeCopy.DATE, FakeCopy.MY_ENTRY, false, "e1", FakeCopy.PARTNER_ENTRY, canDelete = true),
        ArchiveDayUi(FakeCopy.SOLO_DATE, null, true, "e2", FakeCopy.PARTNER_ENTRY, canDelete = false),
        ArchiveDayUi(LocalDate(2025, 8, 30), FakeCopy.MY_ENTRY, false, "e3", null, canDelete = false),
    )
    return days.groupBy { it.date.month }.map { (_, d) -> ArchiveMonth(d.first().date, d) }
}

@Preview
@Composable
private fun ArchiveListPreview() {
    MoyiTheme(darkTheme = true) {
        ArchiveScreen(ArchiveState(load = ArchiveLoad.Ready, months = previewDays(), partnerName = FakeCopy.PARTNER_NAME), onAction = {})
    }
}

@Preview
@Composable
private fun ArchiveEmptyPreview() {
    MoyiTheme(darkTheme = true) { ArchiveScreen(ArchiveState(load = ArchiveLoad.Ready), onAction = {}) }
}

@Preview
@Composable
private fun ArchiveListLightPreview() {
    MoyiTheme(darkTheme = false) {
        ArchiveScreen(
            ArchiveState(load = ArchiveLoad.Ready, months = previewDays(), partnerName = FakeCopy.PARTNER_NAME, pageFailed = true),
            onAction = {},
        )
    }
}
