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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moyi.app.designsystem.MoyiTheme
import com.moyi.app.designsystem.component.Chip
import com.moyi.app.designsystem.component.ChipRow
import com.moyi.app.designsystem.component.Field
import com.moyi.app.designsystem.component.InlineError
import com.moyi.app.designsystem.component.MoyiScaffold
import com.moyi.app.designsystem.component.ArchiveDayRow
import com.moyi.app.designsystem.component.ArchiveRowLine
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
import com.moyi.app.feature.today.common.weekdayShort
import com.moyi.app.feature.today.common.dayInSentence
import com.moyi.app.feature.today.resources.Res
import com.moyi.app.feature.today.resources.archive_day_favourited
import com.moyi.app.feature.today.resources.archive_row_author_partner_unknown
import com.moyi.app.feature.today.resources.archive_row_author_you
import com.moyi.app.feature.today.resources.archive_row_favourite
import com.moyi.app.feature.today.resources.archive_row_photo
import com.moyi.app.feature.today.resources.archive_row_photo_and_voice
import com.moyi.app.feature.today.resources.archive_row_spoken_entry
import com.moyi.app.feature.today.resources.archive_row_spoken_photo
import com.moyi.app.feature.today.resources.archive_row_spoken_voice
import com.moyi.app.feature.today.resources.archive_row_voice
import com.moyi.app.feature.today.resources.archive_empty_heading
import com.moyi.app.feature.today.resources.archive_empty_line
import com.moyi.app.feature.today.resources.archive_favourites_empty_heading
import com.moyi.app.feature.today.resources.archive_favourites_empty_line
import com.moyi.app.feature.today.resources.archive_favourites_empty_privacy
import com.moyi.app.feature.today.resources.archive_filter_all
import com.moyi.app.feature.today.resources.archive_filter_favourites
import com.moyi.app.feature.today.resources.archive_filter_search
import com.moyi.app.feature.today.resources.archive_month_jump
import com.moyi.app.feature.today.resources.archive_month_newest
import com.moyi.app.feature.today.resources.archive_month_picker_title
import com.moyi.app.feature.today.resources.archive_month_row_description
import com.moyi.app.feature.today.resources.archive_search_helper
import com.moyi.app.feature.today.resources.archive_search_label
import com.moyi.app.feature.today.resources.archive_search_none_heading
import com.moyi.app.feature.today.resources.archive_search_none_line
import com.moyi.app.feature.today.resources.archive_streak_link
import com.moyi.app.feature.today.resources.archive_title
import com.moyi.app.feature.today.resources.delete_cancel
import com.moyi.app.feature.today.resources.today_action_failed
import com.moyi.app.feature.today.resources.today_error_action
import com.moyi.app.feature.today.resources.today_error_heading
import com.moyi.app.feature.today.resources.today_error_line
import com.moyi.app.feature.today.resources.today_tombstone_line
import com.moyi.app.feature.today.resources.today_write_action
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ArchiveRoot(
    onBack: () -> Unit,
    onOpenDay: (date: LocalDate) -> Unit,
    onWrite: () -> Unit,
    onOpenStreak: () -> Unit,
    debugTools: Boolean,
    viewModel: ArchiveViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            ArchiveEvent.NavigateBack -> onBack()
            is ArchiveEvent.NavigateToDay -> onOpenDay(event.date)
            ArchiveEvent.NavigateToWrite -> onWrite()
            ArchiveEvent.NavigateToStreak -> onOpenStreak()
        }
    }
    ArchiveScreen(state = state, onAction = viewModel::onAction, debugTools = debugTools)
}

/**
 * The Archive (Figma 223:3, empty 223:25). The back target at y 24, the title
 * "Your days" at 96 (24 under the back, as on every screen with one), then the list.
 *
 * Then the All days / Favourites / Search chips (when there is anything to filter) and the list.
 * With Search chosen (Figma 508:1509, 511:1518, 511:1691), the search field takes the top
 * of the navigation block, where the month row will be (states.md §6: they swap in the
 * same slot), and the chips stay under it as the way back.
 *
 * On All days and Favourites the month row (states.md §6, route 1) takes that slot: it
 * names the month at the top of the list, and "Jump to" opens the month picker. It is
 * states.md's 56 tall, where Figma draws 80, so the chips are at 240 and the list at 312,
 * 24 above Figma's 264 and 336. Month headings stay in the list.
 *
 * "Your streak" in the title row opens the Streak screen.
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
        // The title row (Figma 430:1434): the title, and "Your streak" on the trailing side
        // (430:1435, a 48 target), sentence case per D14 where Figma draws label metrics.
        Row(Modifier.fillMaxWidth().height(MoyiDimens.TouchTarget), verticalAlignment = Alignment.CenterVertically) {
            ScreenTitle(
                text = stringResource(Res.string.archive_title),
                modifier = Modifier
                    .weight(1f)
                    .debugLongPress(if (debugTools) ({ onAction(ArchiveAction.OnDebugCycle) }) else null),
            )
            TextLink(
                text = stringResource(Res.string.archive_streak_link),
                onClick = { onAction(ArchiveAction.OnStreakClick) },
            )
        }
        Spacer(Modifier.height(MoyiTheme.spacing.lg))
        // The field and the month row share the navigation block's top slot (states.md §6).
        if (state.showFilters && state.filter == ArchiveFilter.Search) {
            SearchField(state.query, state.focusSearch, onAction)
            Spacer(Modifier.height(MoyiTheme.spacing.md))
        } else if (state.showMonthRow && state.visibleMonth != null) {
            MonthRow(state.visibleMonth, onJump = { onAction(ArchiveAction.OnJumpClick) })
            Spacer(Modifier.height(MoyiTheme.spacing.md))
        }
        if (state.showFilters) {
            Filters(state.filter, state.showSearch, onSelect = { onAction(ArchiveAction.OnFilterClick(it)) })
            Spacer(Modifier.height(MoyiTheme.spacing.lg))
        }
        if (state.jumped) {
            TextLink(
                text = stringResource(Res.string.archive_month_newest),
                onClick = { onAction(ArchiveAction.OnNewestClick) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(MoyiTheme.spacing.md))
        }
        if (state.showMonthPicker) {
            MonthPicker(
                months = state.pickerMonths,
                onPick = { onAction(ArchiveAction.OnMonthChosen(it)) },
                onDismiss = { onAction(ArchiveAction.OnMonthPickerDismiss) },
            )
        }
        when {
            state.load == ArchiveLoad.Loading -> Spacer(Modifier.fillMaxWidth().weight(1f))
            state.load == ArchiveLoad.Failed && state.filter == ArchiveFilter.Search ->
                SearchFailed(onRetry = { onAction(ArchiveAction.OnRetryClick) })
            state.load == ArchiveLoad.Failed -> Failed(onRetry = { onAction(ArchiveAction.OnRetryClick) })
            state.noResults -> NoResults()
            state.isEmpty && state.filter == ArchiveFilter.Favourites -> NoFavourites()
            state.isEmpty -> Empty(onWrite = { onAction(ArchiveAction.OnWriteClick) })
            else -> DayList(state, onAction)
        }
    }
}

/**
 * The month row (Figma 257:9, light 281:757): the calendar glyph at x 16, the month at 48
 * in `body`, and "Jump to" (a TextLink, sentence case per D14) on the trailing side, in a
 * row with the `outline` hairline. **56 tall, as states.md §6 sizes it**; Figma's frame is 80.
 *
 * One stop for a screen reader: "Showing September 2026. Jump to a month.", a button that
 * opens the picker. The link inside is the touch target.
 */
@Composable
private fun MonthRow(month: LocalDate, onJump: () -> Unit) {
    val colors = MoyiTheme.colors
    val name = remember(month) { monthAndYear(month) }
    val description = stringResource(Res.string.archive_month_row_description, name)
    Row(
        Modifier
            .fillMaxWidth()
            .height(MonthRowHeight)
            .border(MoyiDimens.EntryCard.Stroke, colors.outline, MoyiTheme.shapes.radius)
            .padding(horizontal = MoyiTheme.spacing.md)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                onClick { onJump(); true }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(MoyiIcons.Calendar, contentDescription = null, tint = colors.ink, modifier = Modifier.size(MoyiDimens.IconGlyph))
        Spacer(Modifier.width(MoyiTheme.spacing.sm))
        MoyiText(text = name, modifier = Modifier.weight(1f), maxLines = 1, ellipsis = true)
        TextLink(text = stringResource(Res.string.archive_month_jump), onClick = onJump)
    }
}

/**
 * The month picker. **The design system draws none** (docs/missing-components.md §17), so
 * this is the platform's simple dialog, not a component of ours: Material 3's `AlertDialog`
 * in Rosewood (MoyiTheme maps its roles to the tokens), a list of months, newest first, and
 * Cancel. Tapping a month chooses it and closes the dialog. The same Compose dialog on iOS.
 */
@Composable
private fun MonthPicker(months: List<LocalDate>, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val colors = MoyiTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.delete_cancel), color = colors.ink) }
        },
        title = { Text(stringResource(Res.string.archive_month_picker_title)) },
        text = {
            LazyColumn {
                items(months, key = { it.toString() }) { month ->
                    val name = remember(month) { monthAndYear(month) }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = MoyiDimens.TouchTarget)
                            .clickable(role = Role.Button) { onPick(month) },
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(name, style = MoyiTheme.type.body, color = colors.ink)
                    }
                }
            }
        },
        containerColor = colors.surfaceRaised,
        titleContentColor = colors.ink,
        textContentColor = colors.ink,
    )
}

/**
 * `Field · Text` with its visible label and helper (states.md §6: placeholder-as-label is
 * not allowed here either). Figma 508:1561: 358 x 115 at the top of the navigation block,
 * the chips 16 under it. Choosing Search focuses it and raises the keyboard once; coming
 * back from a day view keeps the query and does not raise the keyboard again.
 */
@Composable
private fun SearchField(query: String, focus: Boolean, onAction: (ArchiveAction) -> Unit) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(focus) {
        if (focus) {
            focusRequester.requestFocus()
            keyboard?.show()
            onAction(ArchiveAction.OnSearchFocused)
        }
    }
    Field(
        value = query,
        onValueChange = { onAction(ArchiveAction.OnQueryChange(it)) },
        label = stringResource(Res.string.archive_search_label),
        helper = stringResource(Res.string.archive_search_helper),
        modifier = Modifier.focusRequester(focusRequester),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        // The query is already being searched as it rests; the key only puts the keyboard away.
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
    )
}

/**
 * No results (Figma 511:1691): the failing query stays in the field, and this message
 * block replaces the list. No back affordance of its own: the chips are the way back.
 */
@Composable
private fun ColumnScope.NoResults() {
    Column(Modifier.fillMaxWidth().weight(1f)) {
        StateBlock(
            heading = stringResource(Res.string.archive_search_none_heading),
            line = stringResource(Res.string.archive_search_none_line),
        )
    }
}

/** A search that failed: the field and chips stay, with the Generic failure line and Try again. */
@Composable
private fun ColumnScope.SearchFailed(onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().weight(1f)) {
        InlineError(stringResource(Res.string.today_action_failed))
        TextLink(
            text = stringResource(Res.string.today_error_action),
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Figma's filters row (440:1475, 508:1522): All days, Favourites and Search, 8 apart.
 * Search is left out while the archive is empty (states.md §6). 16 under the month row,
 * or under the search field when Search is chosen (they share the slot above).
 */
@Composable
private fun Filters(selected: ArchiveFilter, showSearch: Boolean, onSelect: (ArchiveFilter) -> Unit) {
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
        if (showSearch || selected == ArchiveFilter.Search) {
            Chip(
                text = stringResource(Res.string.archive_filter_search),
                selected = selected == ArchiveFilter.Search,
                onClick = { onSelect(ArchiveFilter.Search) },
            )
        }
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
    // The month of each item, in the list's order, so the row can name the topmost one's.
    val itemMonths = remember(state.months) {
        state.months.flatMap { month -> List(month.days.size + 1) { month.month } }
    }
    LaunchedEffect(itemMonths) {
        snapshotFlow { itemMonths.getOrNull(listState.firstVisibleItemIndex) }
            .filterNotNull()
            .distinctUntilChanged()
            .collect { onAction(ArchiveAction.OnVisibleMonthChange(it)) }
    }
    // After a jump, or the way back from one: the top of the new list, once.
    LaunchedEffect(state.scrollToTop) {
        if (state.scrollToTop) {
            listState.scrollToItem(0)
            onAction(ArchiveAction.OnScrolledToTop)
        }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth().weight(1f),
    ) {
        state.months.forEach { month ->
            item(key = "month-${month.month.year}-${month.month.month}", contentType = HEADING_TYPE) {
                MonthHeading(month.month)
            }
            items(month.days, key = { it.date.toString() }, contentType = { ROW_TYPE }) { day ->
                DayRow(day, state.partnerName.orEmpty(), onClick = { onAction(ArchiveAction.OnDayClick(day.date)) })
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
 * as a heading. Interpreted: Figma names the month only in the month row; this keeps
 * the grouping visible further down the list, and in Search, where there is no row.
 */
@Composable
private fun MonthHeading(month: LocalDate) {
    val name = remember(month) { monthAndYear(month) }
    MoyiText(
        text = name,
        role = TextRole.Label,
        modifier = Modifier.padding(top = MoyiTheme.spacing.md).semantics { heading() },
    )
}

/**
 * One day as a compact row (owner ruling 2026-11-11, replacing Figma's DayCard 223:9): the
 * date on the left, the first line of each entry, small chips for media and my favourite.
 * [ArchiveDayRow] draws it; this builds what it shows from the day, once per day
 * (`remember(day)`), so scrolling does no work beyond drawing.
 *
 * The partner's block is drawn only when the day holds their words; a locked entry is
 * not even named. My deleted entry is its tombstone, as on Today. Photos and voice notes
 * are no longer drawn in the list; they are chips here and play in the day view.
 *
 * R3: a row that draws the partner's words protects the screen from capture while it is
 * composed, as EntryCard Partner does ([ArchiveRowModel.showsPartnerWords]).
 */
@Composable
private fun DayRow(day: ArchiveDayUi, partnerName: String, onClick: () -> Unit) {
    val row = remember(day) { day.toRowModel() }
    val dayNumber = remember(day.date) { day.date.day.toString() }
    val weekday = remember(day.date) { weekdayShort(day.date) }
    val you = stringResource(Res.string.archive_row_author_you)
    val partner = partnerName.ifBlank { stringResource(Res.string.archive_row_author_partner_unknown) }
    val photo = stringResource(Res.string.archive_row_photo)
    val voice = stringResource(Res.string.archive_row_voice)
    val photoAndVoice = stringResource(Res.string.archive_row_photo_and_voice)
    val deleted = stringResource(Res.string.today_tombstone_line)
    val lines = row.lines.map { line ->
        // The label names the person briefly: a partner's first word (a long display name would
        // push the words off the row). The spoken description below keeps the full name.
        val author = if (line.author == RowAuthor.You) you else partner.substringBefore(' ')
        when (val body = line.body) {
            is RowBody.Words -> ArchiveRowLine(author, body.line)
            is RowBody.MediaOnly -> ArchiveRowLine(
                author,
                when {
                    body.photo && body.voice -> photoAndVoice
                    body.voice -> voice
                    else -> photo
                },
                muted = true,
            )
            RowBody.Deleted -> ArchiveRowLine(author, deleted, muted = true)
        }
    }
    val chips = buildList {
        if (row.photoChip) add(photo)
        if (row.voiceChip) add(voice)
        if (row.favourite) add(stringResource(Res.string.archive_row_favourite))
    }
    val extras = buildList {
        if (row.photoChip) add(stringResource(Res.string.archive_row_spoken_photo))
        if (row.voiceChip) add(stringResource(Res.string.archive_row_spoken_voice))
        if (row.favourite) add(stringResource(Res.string.archive_day_favourited))
    }
    val spoken = row.lines.zip(lines).map { (line, drawn) ->
        stringResource(Res.string.archive_row_spoken_entry, if (line.author == RowAuthor.You) you else partner, drawn.text)
    }
    val description = rowDescription(dayInSentence(day.date), spoken, extras)
    ArchiveDayRow(
        dayNumber = dayNumber,
        weekday = weekday,
        lines = lines,
        chips = chips,
        description = description,
        onClick = onClick,
        protectFromCapture = row.showsPartnerWords,
    )
}

/** states.md §6: "the 56 pt month-jump row" (Figma's frame 257:9 is 80; see archive-check.md). */
private val MonthRowHeight = 56.dp

/** Ask for the next page this many items before the end. Not a design value. */
private const val LOAD_AHEAD = 3

/** Lazy-list content types: rows and month headings reuse their own compositions. */
private const val ROW_TYPE = "day"
private const val HEADING_TYPE = "month"

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
            ArchiveState(
                load = ArchiveLoad.Ready,
                months = previewDays(),
                partnerName = FakeCopy.PARTNER_NAME,
                showFilters = true,
                showSearch = true,
                showMonthRow = true,
                visibleMonth = LocalDate(2025, 9, 1),
            ),
            onAction = {},
        )
    }
}

@Preview
@Composable
private fun ArchiveJumpedPreview() {
    MoyiTheme(darkTheme = false) {
        ArchiveScreen(
            ArchiveState(
                load = ArchiveLoad.Ready,
                months = previewDays().drop(1),
                partnerName = FakeCopy.PARTNER_NAME,
                showFilters = true,
                showSearch = true,
                showMonthRow = true,
                visibleMonth = LocalDate(2025, 8, 1),
                jumped = true,
            ),
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

@Preview
@Composable
private fun ArchiveSearchResultsPreview() {
    MoyiTheme(darkTheme = true) {
        ArchiveScreen(
            ArchiveState(
                load = ArchiveLoad.Ready,
                months = previewDays(),
                partnerName = FakeCopy.PARTNER_NAME,
                filter = ArchiveFilter.Search,
                showFilters = true,
                showSearch = true,
                query = "flat",
            ),
            onAction = {},
        )
    }
}

@Preview
@Composable
private fun ArchiveSearchNoResultsPreview() {
    MoyiTheme(darkTheme = false) {
        ArchiveScreen(
            ArchiveState(load = ArchiveLoad.Ready, filter = ArchiveFilter.Search, showFilters = true, showSearch = true, query = "umbrella"),
            onAction = {},
        )
    }
}
