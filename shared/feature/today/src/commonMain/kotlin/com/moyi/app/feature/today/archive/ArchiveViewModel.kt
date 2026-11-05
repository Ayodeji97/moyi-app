package com.moyi.app.feature.today.archive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
 * It owns the feed's lifetime: cleared on the way in and on the way out (onCleared),
 * so no one's days outlive the Archive that showed them.
 */
class ArchiveViewModel(
    private val feed: ArchiveFeed,
) : ViewModel() {

    /** DEBUG ONLY: the layout the preview's long press has stepped to, over the real one. */
    private val debugLayout = MutableStateFlow<DebugLayout?>(null)

    init {
        feed.clear()
    }

    val state: StateFlow<ArchiveState> = combine(feed.state, debugLayout, ::toState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), toState(feed.state.value, debugLayout.value))

    private val _events = Channel<ArchiveEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch { loadFirst() }
    }

    fun onAction(action: ArchiveAction) {
        when (action) {
            ArchiveAction.OnBackClick -> send(ArchiveEvent.NavigateBack)
            ArchiveAction.OnRetryClick -> viewModelScope.launch { loadFirst() }
            ArchiveAction.OnLoadMore, ArchiveAction.OnRetryPageClick -> viewModelScope.launch { loadMore() }
            is ArchiveAction.OnDayClick -> send(ArchiveEvent.NavigateToDay(action.date))
            ArchiveAction.OnWriteClick -> send(ArchiveEvent.NavigateToWrite)
            ArchiveAction.OnDebugCycle -> debugLayout.value = when (debugLayout.value) {
                null -> DebugLayout.Empty
                DebugLayout.Empty -> DebugLayout.Failed
                DebugLayout.Failed -> null
            }
        }
    }

    private suspend fun loadFirst() {
        feed.loadFirst()
        skipUndrawable()
    }

    private suspend fun loadMore() {
        feed.loadMore()
        skipUndrawable()
    }

    /**
     * Asks again while nothing loaded can be drawn and the cursor says there is more,
     * so a page of tombstones-only-for-someone-else is never mistaken for the end.
     * Bounded: a server that never ends cannot spin the app.
     */
    private suspend fun skipUndrawable() {
        repeat(MAX_SILENT_PAGES) {
            val now = feed.state.value
            if (now.pageFailed || now.nextCursor == null || now.days.any { it.toArchiveDayUi() != null }) return
            feed.loadMore()
        }
    }

    private fun toState(feed: FeedState, debug: DebugLayout?): ArchiveState {
        when (debug) {
            DebugLayout.Empty -> return ArchiveState(load = ArchiveLoad.Ready)
            DebugLayout.Failed -> return ArchiveState(load = ArchiveLoad.Failed)
            null -> Unit
        }
        return when (feed.load) {
            FeedLoad.Loading -> ArchiveState(load = ArchiveLoad.Loading)
            FeedLoad.Failed -> ArchiveState(load = ArchiveLoad.Failed)
            FeedLoad.Ready -> {
                val days = feed.days.mapNotNull { it.toArchiveDayUi() }
                // Still looking for a first day to draw: not empty yet, so still the surface.
                if (days.isEmpty() && feed.nextCursor != null && !feed.pageFailed) {
                    ArchiveState(load = ArchiveLoad.Loading)
                } else if (days.isEmpty() && feed.pageFailed) {
                    ArchiveState(load = ArchiveLoad.Failed)
                } else {
                    ArchiveState(
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

    private companion object {
        const val MAX_SILENT_PAGES = 10
    }
}
