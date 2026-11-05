package com.moyi.app.feature.today.archive

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
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
import com.moyi.app.designsystem.component.Chip
import com.moyi.app.designsystem.component.ChipRow
import com.moyi.app.designsystem.component.InlineError
import com.moyi.app.designsystem.component.MoyiScaffold
import com.moyi.app.designsystem.component.MoyiText
import com.moyi.app.designsystem.component.PrimaryAction
import com.moyi.app.designsystem.component.StateBlock
import com.moyi.app.designsystem.component.TextLink
import com.moyi.app.designsystem.component.TextRole
import com.moyi.app.designsystem.component.TextTone
import com.moyi.app.designsystem.icon.MoyiIcons
import com.moyi.app.designsystem.presentation.ObserveAsEvents
import com.moyi.app.designsystem.theme.MoyiDimens
import com.moyi.app.feature.today.common.FakeCopy
import com.moyi.app.feature.today.common.ScreenTitle
import com.moyi.app.feature.today.common.debugLongPress
import com.moyi.app.feature.today.common.monthAndYear
import com.moyi.app.feature.today.common.weekdayAndDay
import com.moyi.app.feature.today.resources.Res
import com.moyi.app.feature.today.resources.archive_day_favourited
import com.moyi.app.feature.today.resources.archive_empty_heading
import com.moyi.app.feature.today.resources.archive_favourites_empty_heading
import com.moyi.app.feature.today.resources.archive_favourites_empty_line
import com.moyi.app.feature.today.resources.archive_favourites_empty_privacy
import com.moyi.app.feature.today.resources.archive_filter_all
import com.moyi.app.feature.today.resources.archive_filter_favourites
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
 * Then the All days / Favourites chips (when there is anything to filter) and the list.
 *
 * Owed and **not drawn** (docs/figma/archive-check.md): the month row with "Jump to",
 * the Search chip and the "Your streak" link. Without the month row the chips start at
 * 168 and the list at 240, not Figma's 264 and 336, and each month is named by a
 * heading in the list instead of by the month row.
 *
 * @param debugTools `-PmoyiUiPreview=today` only: a long press on the title steps list, empty, error.
 */
@Composable
fun ArchiveScreen(
    state: ArchiveState,
    onAction: (ArchiveAction) -> Unit,
    debugTools: Boolean = false,
) {
    // The bare surface on the way in; once a chip has been chosen, the chrome stays and
    // only the list area waits (still no spinner).
    if (state.load == ArchiveLoad.Loading && !state.showFilters) {
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
        if (state.showFilters) {
            Filters(state.filter, onSelect = { onAction(ArchiveAction.OnFilterClick(it)) })
            Spacer(Modifier.height(MoyiTheme.spacing.lg))
        }
        when {
            state.load == ArchiveLoad.Loading -> Spacer(Modifier.fillMaxWidth().weight(1f))
            state.load == ArchiveLoad.Failed -> Failed(onRetry = { onAction(ArchiveAction.OnRetryClick) })
            state.isEmpty && state.filter == ArchiveFilter.Favourites -> NoFavourites()
            state.isEmpty -> Empty(onWrite = { onAction(ArchiveAction.OnWriteClick) })
            else -> DayList(state, onAction)
        }
    }
}

/**
 * Figma's filters row (440:1475): All days and Favourites, 8 apart. The third chip,
 * Search, is the next slice's and is not drawn. Figma puts the row under the month
 * jump (owed), so it sits where the month row would start, 24 under the title.
 */
@Composable
private fun Filters(selected: ArchiveFilter, onSelect: (ArchiveFilter) -> Unit) {
    ChipRow(Modifier.fillMaxWidth()) {
        Chip(
            text = stringResource(Res.string.archive_filter_all),
            selected = selected == ArchiveFilter.AllDays,
            onClick = { onSelect(ArchiveFilter.AllDays) },
        )
        Chip(
            text = stringResource(Res.string.archive_filter_favourites),
            selected = selected == ArchiveFilter.Favourites,
            onClick = { onSelect(ArchiveFilter.Favourites) },
        )
    }
}

/**
 * Favourites, empty (Figma 440:1570). Quiet, top-aligned where the list would start,
 * and it **states the privacy fact** (states.md §6): the StateBlock's heading and line,
 * then "Only you can see your favourites." at the StateBlock's own inset.
 */
@Composable
private fun ColumnScope.NoFavourites() {
    Column(Modifier.fillMaxWidth().weight(1f)) {
        StateBlock(
            heading = stringResource(Res.string.archive_favourites_empty_heading),
            line = stringResource(Res.string.archive_favourites_empty_line),
        )
        MoyiText(
            text = stringResource(Res.string.archive_favourites_empty_privacy),
            tone = TextTone.Muted,
            modifier = Modifier.padding(horizontal = MoyiDimens.StateBlock.Padding),
        )
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
 * No chevron (states.md §6). The whole card opens the day view. A small filled
 * bookmark on the trailing edge of the date row when the day holds **my** favourite
 * (440:1510, 24 x 24).
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
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            MoyiText(text = dayName, role = TextRole.Label, modifier = Modifier.weight(1f))
            // states.md §6: the only thing ever on a card's trailing edge, and only for
            // a day holding an entry *I* favourited. The partner's marks are never drawn.
            if (day.holdsMyFavourite) {
                Icon(
                    imageVector = MoyiIcons.BookmarkFilled,
                    contentDescription = stringResource(Res.string.archive_day_favourited),
                    tint = colors.ink,
                    modifier = Modifier.size(MoyiDimens.IconGlyph),
                )
            }
        }
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
        ArchiveDayUi(
            FakeCopy.DATE, FakeCopy.MY_ENTRY, false, "e1", FakeCopy.PARTNER_ENTRY, canDelete = true,
            partnerEntryId = "t1", myMarkOnMine = false, myMarkOnTheirs = true,
        ),
        ArchiveDayUi(FakeCopy.SOLO_DATE, null, true, "e2", FakeCopy.PARTNER_ENTRY, canDelete = false),
        ArchiveDayUi(LocalDate(2025, 8, 30), FakeCopy.MY_ENTRY, false, "e3", null, canDelete = false),
    )
    return days.groupBy { it.date.month }.map { (_, d) -> ArchiveMonth(d.first().date, d) }
}

@Preview
@Composable
private fun ArchiveListPreview() {
    MoyiTheme(darkTheme = true) {
        ArchiveScreen(
            ArchiveState(load = ArchiveLoad.Ready, months = previewDays(), partnerName = FakeCopy.PARTNER_NAME, showFilters = true),
            onAction = {},
        )
    }
}

@Preview
@Composable
private fun ArchiveFavouritesEmptyPreview() {
    MoyiTheme(darkTheme = false) {
        ArchiveScreen(ArchiveState(load = ArchiveLoad.Ready, filter = ArchiveFilter.Favourites, showFilters = true), onAction = {})
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
