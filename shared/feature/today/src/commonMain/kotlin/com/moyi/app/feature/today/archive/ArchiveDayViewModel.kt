package com.moyi.app.feature.today.archive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moyi.app.core.DataError
import com.moyi.app.core.Result
import com.moyi.app.domain.archive.ArchiveDay
import com.moyi.app.domain.archive.ArchiveRepository
import com.moyi.app.domain.today.EntryStatus
import com.moyi.app.domain.today.TodayRepository
import com.moyi.app.feature.today.common.DailyLoop
import com.moyi.app.feature.today.common.DailyLoopState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One day of the Archive, [date] in ISO form.
 *
 * - **Loading, cache-then-network**: the day the list already loaded shows at once, or
 *   else the cached day ([ArchiveFeed.cached]); then `GET …/days/{date}` is always asked,
 *   and its answer replaces what is shown and what is cached (the server wins). With
 *   nothing to show, the bare surface until it answers, and the Generic error with Try
 *   again if it does not. Offline over a shown day, nothing changes and nothing is said.
 *   A `404 DAY_NOT_FOUND` means the day is not in the archive any more: it leaves the
 *   list and the cache, and the day view shows the Generic error.
 * - **Delete after reveal**, exactly as on Today (states.md §5, D11): the platform's
 *   confirm, then the call, **not optimistic**: the tombstone shows only after the 204,
 *   in the day view, in the list and in the cache. A failure keeps the entry and says so; a 404 (already
 *   gone) reads the day again and says nothing. If the day is the one Today shows, Today
 *   is refreshed, so it shows the tombstone too.
 * - **Favourites** (states.md §6): a FavouriteToggle under each revealed entry, mine and
 *   theirs, **absent** before the reveal. **Optimistic**: the mark changes at once, here
 *   and in the list (the shared feed); the cache only once the server confirms it. A
 *   refresh answering while a mark is on its way keeps that mark as tapped. A failure puts it back and shows the Generic
 *   failure line under that toggle. A tap while that entry's call is in flight is
 *   ignored, so one tap is one call and the calls for an entry can never cross.
 */
class ArchiveDayViewModel(
    private val date: String,
    private val feed: ArchiveFeed,
    private val archive: ArchiveRepository,
    private val entries: TodayRepository,
    private val loop: DailyLoop,
) : ViewModel() {
    private val _state = MutableStateFlow(ArchiveDayState())
    val state: StateFlow<ArchiveDayState> = _state.asStateFlow()

    private val _events = Channel<ArchiveDayEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /** The day behind [state], kept to build the tombstone from. */
    private var day: ArchiveDay? = null

    /** The entries whose favourite call has not answered yet. */
    private val marking = mutableSetOf<String>()

    init {
        val loaded = feed.find(date)
        val shown = loaded != null && show(loaded)
        viewModelScope.launch {
            if (!shown) feed.cached(date)?.let { show(it) }
            load()
        }
    }

    fun onAction(action: ArchiveDayAction) {
        when (action) {
            ArchiveDayAction.OnBackClick -> viewModelScope.launch { _events.send(ArchiveDayEvent.NavigateBack) }
            ArchiveDayAction.OnRetryClick -> viewModelScope.launch { load() }
            ArchiveDayAction.OnDeleteClick -> if (!_state.value.deleting && _state.value.day?.canDelete == true) {
                _state.update { it.copy(confirmingDelete = true, actionFailed = false) }
            }
            ArchiveDayAction.OnDeleteDismiss -> _state.update { it.copy(confirmingDelete = false) }
            ArchiveDayAction.OnDeleteConfirm -> delete()
            is ArchiveDayAction.OnFavouriteClick -> toggleFavourite(action.target)
        }
    }

    /** Asks for the day; a day already shown stays shown until the answer replaces it. */
    private suspend fun load() {
        val showing = _state.value.load == ArchiveLoad.Ready
        if (!showing) _state.update { it.copy(load = ArchiveLoad.Loading) }
        val bondId = feed.state.value.bondId ?: (loop.state.value as? DailyLoopState.Ready)?.day?.bondId
        val answer = bondId?.let { archive.day(it, date) }
        when {
            answer is Result.Success -> {
                // A delete on its way decides the day itself, from its own answer.
                if (_state.value.deleting) return
                val fresh = answer.data
                feed.keep(fresh)
                val shownAs = fresh.withMarksInFlight()
                if (show(shownAs)) feed.replace(shownAs) else _state.update { it.copy(load = ArchiveLoad.Failed) }
            }
            answer is Result.Failure && answer.error.isNotFound() -> {
                day = null
                feed.forget(date)
                _state.update { it.copy(load = ArchiveLoad.Failed, day = null) }
            }
            !showing -> _state.update { it.copy(load = ArchiveLoad.Failed) }
        }
    }

    /** [this], with my marks on the entries whose call is still on its way as I tapped them. */
    private fun ArchiveDay.withMarksInFlight(): ArchiveDay {
        val shown = day ?: return this
        if (marking.isEmpty()) return this
        return copy(markedByMe = (markedByMe - marking) + (shown.markedByMe intersect marking))
    }

    /** False when the day has nothing to draw: the caller shows the error rather than a blank day. */
    private fun show(loaded: ArchiveDay): Boolean {
        val ui = loaded.toArchiveDayUi() ?: return false
        day = loaded
        _state.update {
            it.copy(
                load = ArchiveLoad.Ready,
                date = ui.date,
                day = ui,
                partnerName = feed.state.value.partnerName ?: (loop.state.value as? DailyLoopState.Ready)?.day?.partnerName,
            )
        }
        return true
    }

    private fun delete() {
        val current = day ?: return
        val mine = current.myEntry?.takeIf { it.isLive } ?: return
        if (_state.value.deleting || _state.value.day?.canDelete != true) return
        _state.update { it.copy(confirmingDelete = false, deleting = true, actionFailed = false) }
        viewModelScope.launch {
            when (val result = entries.deleteEntry(mine.id)) {
                is Result.Success -> {
                    // From the latest day, so a favourite toggled meanwhile is kept; the
                    // tombstone itself is no longer a favourite (it cannot be read).
                    val latest = day ?: current
                    val tombstoned = latest.copy(
                        myEntry = mine.copy(text = null, status = EntryStatus.DELETED),
                        markedByMe = latest.markedByMe - mine.id,
                    )
                    show(tombstoned)
                    feed.replace(tombstoned)
                    feed.keep(tombstoned)
                    _state.update { it.copy(deleting = false) }
                    refreshTodayIfShowing()
                }
                is Result.Failure -> if (result.error.isNotFound()) {
                    _state.update { it.copy(deleting = false) }
                    load()
                } else {
                    _state.update { it.copy(deleting = false, actionFailed = true) }
                }
            }
        }
    }

    private fun toggleFavourite(target: FavouriteTarget) {
        val ui = _state.value.day ?: return
        val (entryId, marked) = when (target) {
            FavouriteTarget.Mine -> ui.myEntryId to ui.myMarkOnMine
            FavouriteTarget.Theirs -> ui.partnerEntryId to ui.myMarkOnTheirs
        }
        // Absent toggle (before the reveal, a tombstone): nothing to do. In flight: ignored.
        if (entryId == null || marked == null || entryId in marking) return
        marking += entryId
        setMark(entryId, !marked)
        _state.update { it.copy(markFailed = null) }
        viewModelScope.launch {
            val result = archive.setFavourite(entryId, favourite = !marked)
            marking -= entryId
            when (result) {
                is Result.Success -> feed.keepMark(date, entryId, !marked)
                is Result.Failure -> {
                    setMark(entryId, marked)
                    _state.update { it.copy(markFailed = target) }
                }
            }
        }
    }

    /** My mark on [entryId], in the day view and in the list behind it. */
    private fun setMark(entryId: String, marked: Boolean) {
        val current = day ?: return
        val updated = current.copy(markedByMe = if (marked) current.markedByMe + entryId else current.markedByMe - entryId)
        show(updated)
        feed.setMarks(updated.date, updated.markedByMe)
    }

    private suspend fun refreshTodayIfShowing() {
        val today = (loop.state.value as? DailyLoopState.Ready)?.day ?: return
        if (today.date.toString() == date) loop.refresh()
    }

    private fun DataError.Network.isNotFound(): Boolean = this is DataError.Network.Api && problem.status == 404
}
