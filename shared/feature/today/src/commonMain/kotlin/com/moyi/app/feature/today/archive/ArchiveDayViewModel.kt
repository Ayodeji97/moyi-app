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
 * - **Loading**: the day the list already loaded shows at once; otherwise
 *   `GET …/days/{date}`, with the bare surface until it answers, and the Generic
 *   error with Try again if it does not (a `404 DAY_NOT_FOUND` included).
 * - **Delete after reveal**, exactly as on Today (states.md §5, D11): the platform's
 *   confirm, then the call, **not optimistic**: the tombstone shows only after the 204,
 *   in the day view and in the list. A failure keeps the entry and says so; a 404 (already
 *   gone) reads the day again and says nothing. If the day is the one Today shows, Today
 *   is refreshed, so it shows the tombstone too.
 * - **Favourites** (states.md §6): a FavouriteToggle under each revealed entry, mine and
 *   theirs, **absent** before the reveal. **Optimistic**: the mark changes at once, here
 *   and in the list (the shared feed); a failure puts it back and shows the Generic
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
        if (loaded == null || !show(loaded)) viewModelScope.launch { load() }
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

    private suspend fun load() {
        _state.update { it.copy(load = ArchiveLoad.Loading) }
        val bondId = feed.state.value.bondId ?: (loop.state.value as? DailyLoopState.Ready)?.day?.bondId
        val answer = bondId?.let { archive.day(it, date) }
        if (answer !is Result.Success || !show(answer.data)) _state.update { it.copy(load = ArchiveLoad.Failed) }
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
            if (result is Result.Failure) {
                setMark(entryId, marked)
                _state.update { it.copy(markFailed = target) }
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
