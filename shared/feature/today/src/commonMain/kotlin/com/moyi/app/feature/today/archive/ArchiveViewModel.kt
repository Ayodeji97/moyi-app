package com.moyi.app.feature.today.archive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moyi.app.domain.archive.SEARCH_MAX_LENGTH
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * How long the field must rest before a query is sent. 300 ms is about the gap between
 * keystrokes of someone typing steadily, so a word is sent once, when it pauses, rather
 * than once per letter; and it is short enough that the results feel like an answer to
 * what was typed, not a separate step. Whole-word matching (the contract) means a
 * half-typed word finds little, so asking mid-word is wasted and only moves the list.
 */
val SEARCH_DEBOUNCE: Duration = 300.milliseconds

/**
 * The Archive list, over the shared [ArchiveFeed].
 *
 * - **Loading**: the bare surface until the first page answers; a failure is the
 *   Generic error with Try again.
 * - **Paging**: the screen asks near the end of the list ([ArchiveAction.OnLoadMore]);
 *   a failed page keeps the list and offers Try again at its end.
 * - **Nothing to draw yet is not empty**: while the days loaded draw nothing and the
 *   cursor says there is more, the next page is asked for at once.
 *
 * - **Search** (states.md §6, route 3): the field over the unfiltered list until a query
 *   is entered; then the query is sent once it rests for [SEARCH_DEBOUNCE], below the
 *   contract's minimum (one character once trimmed) the unfiltered list. **A newer query
 *   cancels the one in flight** (`collectLatest`), and the feed drops any answer that is
 *   not for the query it now holds, so a stale response never overwrites a newer one.
 *   The chips leave search; clearing the field returns to the unfiltered list.
 *
 * - **Month jump** (states.md §6, route 1): the row names the month of the topmost visible
 *   day group. Choosing a month reloads the list with `until` = its last day, in the
 *   current filter (`favourites=true` too), and scrolls to the top; later pages pass the
 *   cursor with the same `until`. The picker offers no month after the current Bond-day
 *   and none before the oldest day loaded so far (the client knows of no earlier one).
 *   Choosing the Bond-day's own month, or "Back to the newest days", is the newest list.
 *   A chip, or Search, starts from the newest days again.
 *
 * It owns the feed's lifetime: cleared on the way in and on the way out (onCleared),
 * so no one's days outlive the Archive that showed them.
 */
class ArchiveViewModel(
    private val feed: ArchiveFeed,
) : ViewModel() {

    /** DEBUG ONLY: the layout the preview's long press has stepped to, over the real one. */
    private val debugLayout = MutableStateFlow<DebugLayout?>(null)

    /** A chip has been tapped: from then on the chips stay, through loading and failure. */
    private val filterChosen = MutableStateFlow(false)

    /** Search is the chosen chip, what its field holds, and whether it still wants focus. */
    private val search = MutableStateFlow(SearchInput())

    /** Whether the unfiltered archive holds a day: the Search chip is hidden when it does not. */
    private val archiveHasDays = MutableStateFlow(false)

    /** The month row: what is in view, the picker, the oldest day seen, and a pending scroll. */
    private val monthJump = MutableStateFlow(MonthJump())

    /** Sends the field's query while Search is chosen; cancelled on leaving it. */
    private var searchJob: Job? = null

    init {
        feed.clear()
    }

    val state: StateFlow<ArchiveState> =
        combine(feed.state, debugLayout, filterChosen, search, combine(archiveHasDays, monthJump, ::Pair)) { feed, debug, chosen, input, (hasDays, jump) ->
            toState(feed, debug, chosen, input, hasDays, jump)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            toState(feed.state.value, debugLayout.value, filterChosen.value, search.value, archiveHasDays.value, monthJump.value),
        )

    private val _events = Channel<ArchiveEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch { loadFirst() }
        // Whenever the loaded days draw nothing and the cursor says there is more, ask
        // for more: after a page that drew nothing, and after the day view removed the
        // last favourite the Favourites list was showing.
        viewModelScope.launch {
            feed.state.map { it.needsAnotherPage() }.distinctUntilChanged().collect { needed ->
                if (needed) skipUndrawable()
            }
        }
        // Only the unfiltered list, read to its first drawable day or its end, says
        // whether the archive is empty.
        viewModelScope.launch {
            feed.state.collect { feed ->
                if (feed.load != FeedLoad.Ready || feed.favourites || feed.query != null || feed.until != null) return@collect
                when {
                    feed.visibleDays().isNotEmpty() -> archiveHasDays.value = true
                    feed.nextCursor == null && !feed.pageFailed -> archiveHasDays.value = false
                }
            }
        }
        // The oldest day the server has shown in this Archive: the picker reaches back to its month.
        viewModelScope.launch {
            feed.state.collect { feed ->
                val oldest = feed.days.mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() }.minOrNull() ?: return@collect
                monthJump.update { jump -> if (jump.oldestSeen == null || oldest < jump.oldestSeen) jump.copy(oldestSeen = oldest) else jump }
            }
        }
    }

    fun onAction(action: ArchiveAction) {
        when (action) {
            ArchiveAction.OnBackClick -> send(ArchiveEvent.NavigateBack)
            ArchiveAction.OnRetryClick -> viewModelScope.launch {
                val current = feed.state.value
                current.query?.let { feed.search(it) } ?: loadFirst(current.favourites, current.until)
            }
            ArchiveAction.OnLoadMore, ArchiveAction.OnRetryPageClick -> viewModelScope.launch { loadMore() }
            is ArchiveAction.OnDayClick -> send(ArchiveEvent.NavigateToDay(action.date))
            is ArchiveAction.OnFilterClick -> {
                filterChosen.value = true
                if (action.filter == ArchiveFilter.Search) {
                    startSearch()
                    return
                }
                stopSearch()
                val favourites = action.filter == ArchiveFilter.Favourites
                val current = feed.state.value
                if (current.favourites != favourites || current.query != null || current.load == FeedLoad.Failed) {
                    monthJump.update { it.copy(visible = null) }
                    viewModelScope.launch { loadFirst(favourites, until = null) }
                }
            }
            ArchiveAction.OnJumpClick -> if (!search.value.active) monthJump.update { it.copy(pickerOpen = true) }
            ArchiveAction.OnMonthPickerDismiss -> monthJump.update { it.copy(pickerOpen = false) }
            is ArchiveAction.OnMonthChosen -> chooseMonth(action.month.firstOfMonth())
            ArchiveAction.OnNewestClick -> jumpTo(until = null)
            is ArchiveAction.OnVisibleMonthChange -> monthJump.update { it.copy(visible = action.month.firstOfMonth()) }
            ArchiveAction.OnScrolledToTop -> monthJump.update { it.copy(scrollToTop = false) }
            is ArchiveAction.OnQueryChange -> if (search.value.active) {
                search.update { it.copy(query = action.query.take(SEARCH_MAX_LENGTH)) }
            }
            ArchiveAction.OnSearchFocused -> search.update { it.copy(focus = false) }
            ArchiveAction.OnWriteClick -> send(ArchiveEvent.NavigateToWrite)
            ArchiveAction.OnDebugCycle -> debugLayout.value = when (debugLayout.value) {
                null -> DebugLayout.Empty
                DebugLayout.Empty -> DebugLayout.Failed
                DebugLayout.Failed -> null
            }
        }
    }

    /**
     * Search chosen: the field, empty and focused, over the unfiltered list. Then every
     * query that rests for [SEARCH_DEBOUNCE] and differs from the last one is sent, and
     * `collectLatest` cancels the request still in flight for the previous one.
     */
    @OptIn(FlowPreview::class)
    private fun startSearch() {
        if (search.value.active) return
        search.value = SearchInput(active = true, focus = true)
        searchJob = viewModelScope.launch {
            search
                .map { it.query.trim() }
                // An emptied field goes back to the list at once; only a query waits.
                .debounce { term -> if (term.isEmpty()) Duration.ZERO else SEARCH_DEBOUNCE }
                .distinctUntilChanged()
                .collectLatest { term -> if (term.isEmpty()) showUnfiltered() else feed.search(term) }
        }
    }

    /**
     * Only a month the picker offers: none after the current Bond-day's, none before the
     * oldest day loaded. The Bond-day's own month is the newest list, with no `until`.
     */
    private fun chooseMonth(month: LocalDate) {
        monthJump.update { it.copy(pickerOpen = false) }
        if (month !in pickerMonths(monthJump.value)) return
        val newest = feed.bondDay?.firstOfMonth()
        jumpTo(until = if (month == newest) null else month.plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1)))
    }

    /** Reloads the list from [until] (null: the newest days), in the current filter, and scrolls it to the top. */
    private fun jumpTo(until: LocalDate?) {
        filterChosen.value = true
        monthJump.update { it.copy(visible = null, scrollToTop = true) }
        val favourites = feed.state.value.favourites
        viewModelScope.launch { loadFirst(favourites, until) }
    }

    /** Newest first, as the first of each month: the Bond-day's month back to the oldest seen. */
    private fun pickerMonths(jump: MonthJump): List<LocalDate> {
        val newest = (feed.bondDay ?: return emptyList()).firstOfMonth()
        val oldest = jump.oldestSeen?.firstOfMonth()?.takeIf { it <= newest } ?: newest
        return generateSequence(newest) { it.minus(DatePeriod(months = 1)) }.takeWhile { it >= oldest }.toList()
    }

    private fun stopSearch() {
        searchJob?.cancel()
        searchJob = null
        search.value = SearchInput()
    }

    /** Below the minimum: the unfiltered list, loaded again only if it is not what the feed holds. */
    private suspend fun showUnfiltered() {
        val current = feed.state.value
        if (current.query != null || current.favourites || current.until != null || current.load == FeedLoad.Failed) {
            loadFirst(favourites = false, until = null)
        }
    }

    private suspend fun loadFirst(favourites: Boolean = feed.state.value.favourites, until: LocalDate? = feed.state.value.until) {
        feed.loadFirst(favourites, until)
    }

    private suspend fun loadMore() {
        feed.loadMore()
    }

    /**
     * Asks again while nothing loaded can be drawn and the cursor says there is more,
     * so a page of tombstones-only-for-someone-else is never mistaken for the end. With
     * `favourites=true` a page can be empty with a cursor (the contract), and is asked past
     * the same way: only a null cursor is the end. Bounded: a server that never ends cannot spin the app.
     */
    private suspend fun skipUndrawable() {
        repeat(MAX_SILENT_PAGES) {
            if (!feed.state.value.needsAnotherPage()) return
            feed.loadMore()
        }
    }

    private fun FeedState.needsAnotherPage(): Boolean =
        load == FeedLoad.Ready && nextCursor != null && !loadingMore && !pageFailed && visibleDays().isEmpty()

    private fun toState(feed: FeedState, debug: DebugLayout?, chosen: Boolean, input: SearchInput, hasDays: Boolean, jump: MonthJump): ArchiveState {
        val base = baseState(feed, debug, chosen || input.active)
        val months = base.months.map { it.month.firstOfMonth() }
        // The row says what is at the top of the list: what the screen reported, while it is
        // still in the list, else the first group, else (nothing drawn yet) the month jumped to,
        // else the Bond-day's month.
        val visible = jump.visible?.takeIf { it in months } ?: months.firstOrNull() ?: feed.until?.firstOfMonth() ?: this.feed.bondDay?.firstOfMonth()
        val monthRow = base.showFilters && !input.active && hasDays && visible != null
        return base.copy(
            showMonthRow = monthRow,
            visibleMonth = visible.takeIf { monthRow },
            showMonthPicker = monthRow && jump.pickerOpen,
            pickerMonths = if (monthRow) pickerMonths(jump) else emptyList(),
            jumped = monthRow && feed.until != null,
            scrollToTop = jump.scrollToTop,
            filter = when {
                input.active -> ArchiveFilter.Search
                feed.favourites -> ArchiveFilter.Favourites
                else -> ArchiveFilter.AllDays
            },
            // The chips stay throughout search: they are the way back (states.md §6).
            showFilters = base.showFilters || input.active,
            showSearch = hasDays,
            query = input.query,
            focusSearch = input.focus,
        )
    }

    private fun baseState(feed: FeedState, debug: DebugLayout?, chosen: Boolean): ArchiveState {
        val filter = if (feed.favourites) ArchiveFilter.Favourites else ArchiveFilter.AllDays
        val chrome = chosen || feed.favourites
        when (debug) {
            DebugLayout.Empty -> return ArchiveState(load = ArchiveLoad.Ready, filter = filter, showFilters = feed.favourites)
            DebugLayout.Failed -> return ArchiveState(load = ArchiveLoad.Failed, filter = filter, showFilters = chrome)
            null -> Unit
        }
        return when (feed.load) {
            FeedLoad.Loading -> ArchiveState(load = ArchiveLoad.Loading, filter = filter, showFilters = chrome)
            FeedLoad.Failed -> ArchiveState(load = ArchiveLoad.Failed, filter = filter, showFilters = chrome)
            FeedLoad.Ready -> {
                val days = feed.visibleDays()
                // Still looking for a first day to draw: not empty yet, so still the surface.
                if (days.isEmpty() && feed.nextCursor != null && !feed.pageFailed) {
                    ArchiveState(load = ArchiveLoad.Loading, filter = filter, showFilters = chrome)
                } else if (days.isEmpty() && feed.pageFailed) {
                    ArchiveState(load = ArchiveLoad.Failed, filter = filter, showFilters = chrome)
                } else {
                    ArchiveState(
                        filter = filter,
                        showFilters = chrome || days.isNotEmpty(),
                        load = ArchiveLoad.Ready,
                        months = days
                            .groupBy { it.date.year to it.date.month }
                            .map { (_, inMonth) -> ArchiveMonth(month = inMonth.first().date, days = inMonth) },
                        partnerName = feed.partnerName,
                        canLoadMore = feed.nextCursor != null && !feed.loadingMore && !feed.pageFailed,
                        pageFailed = feed.pageFailed,
                        pages = feed.pages,
                    )
                }
            }
        }
    }

    private fun send(event: ArchiveEvent) {
        viewModelScope.launch { _events.send(event) }
    }

    override fun onCleared() {
        feed.clear()
    }

    private enum class DebugLayout { Empty, Failed }

    private data class MonthJump(
        val visible: LocalDate? = null,
        val pickerOpen: Boolean = false,
        val oldestSeen: LocalDate? = null,
        val scrollToTop: Boolean = false,
    )

    private data class SearchInput(val active: Boolean = false, val query: String = "", val focus: Boolean = false) {
        override fun toString(): String = "SearchInput(active=$active, focus=$focus)"
    }

    private companion object {
        const val MAX_SILENT_PAGES = 10
    }
}

private fun LocalDate.firstOfMonth(): LocalDate = LocalDate(year, month, 1)
