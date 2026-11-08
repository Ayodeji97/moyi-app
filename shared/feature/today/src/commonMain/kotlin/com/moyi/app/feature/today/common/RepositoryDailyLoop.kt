package com.moyi.app.feature.today.common

import com.moyi.app.core.DataError
import com.moyi.app.core.Result
import com.moyi.app.domain.bond.Bond
import com.moyi.app.domain.bond.BondRepository
import com.moyi.app.domain.today.EntryStatus
import com.moyi.app.domain.today.MyEntry
import com.moyi.app.domain.today.Today
import com.moyi.app.domain.today.TodayRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The real [DailyLoop], over `GET /bonds` and the daily-loop endpoints. Online only:
 * the cache, drafts and outbox of doc 13 §5 are owed (SQLDelight is not in the
 * project), so the first load shows the bare surface until the answer arrives, and
 * an entry is never queued.
 *
 * **Which bond**: the person's first open one in `GET /bonds` (newest first), as
 * the signed-in start chose it. There is no screen for more than one (states.md §2).
 * None open any more → [DailyLoopState.NoOpenBond], and the shell asks the signed-in
 * start again rather than guess.
 *
 * **Round trips**: the first load needs the bond's id, so it is `GET /bonds`, then
 * `GET …/today`. Every refresh after that runs both side by side (states.md §3: no
 * chained round trips); the bond list is read again because a partner joining, or the
 * bond ending, changes the layout.
 *
 * Every load and every write runs under one [Mutex], so an answer can never be
 * overwritten by an older one that arrived later, and an optimistic delete is never
 * undone by a refresh that started before it.
 */
class RepositoryDailyLoop(
    private val bonds: BondRepository,
    private val entries: TodayRepository,
) : DailyLoop {
    private val _state = MutableStateFlow<DailyLoopState>(DailyLoopState.Loading)
    override val state: StateFlow<DailyLoopState> = _state.asStateFlow()

    private val mutex = Mutex()

    /** The bond and day behind [state]'s Ready, kept to compute optimistic changes from. */
    private var bond: Bond? = null
    private var today: Today? = null

    /** Moved on by [clear], so a load that was in flight then cannot publish into the next person's session. */
    private var generation = 0

    override suspend fun refresh() {
        // Retrying after a failed first load: the surface again, never a spinner (as SignedInStart does).
        if (_state.value == DailyLoopState.Failed) _state.value = DailyLoopState.Loading
        mutex.withLock { load() }
    }

    override suspend fun submit(text: String, promptId: String?, idempotencyKey: String): WriteResult = mutex.withLock {
        val bondId = bond?.id ?: return@withLock WriteResult.Failed
        when (val result = entries.submitEntry(bondId, text, promptId, idempotencyKey)) {
            is Result.Success -> {
                // Shown at once, so a refresh that fails next cannot put "Write" back under a sent entry.
                withMine(result.data)
                load()
                WriteResult.Saved
            }
            is Result.Failure -> {
                // 409 ENTRY_ALREADY_EXISTS (written from the other phone, or a lost answer) and
                // 404 (the bond went) both mean the day is not what this screen thinks.
                if (result.error.isConflictOrGone()) load()
                WriteResult.Failed
            }
        }
    }

    override suspend fun edit(text: String, idempotencyKey: String): WriteResult = mutex.withLock {
        val entryId = today?.myEntry?.takeIf { it.isLive }?.id ?: return@withLock WriteResult.Failed
        when (val result = entries.editEntry(entryId, text, idempotencyKey)) {
            is Result.Success -> {
                withMine(result.data)
                load()
                WriteResult.Saved
            }
            is Result.Failure -> {
                // 409 ENTRY_IMMUTABLE (the day opened while editing) or BOND_ARCHIVED: no state is
                // drawn for either (states.md §5a), so the composer shows Save failed and Today
                // underneath catches up.
                if (result.error.isConflictOrGone()) load()
                WriteResult.Failed
            }
        }
    }

    override suspend fun deleteBeforeReveal(): DeleteResult = mutex.withLock {
        val day = today ?: return@withLock DeleteResult.Failed
        val currentBond = bond ?: return@withLock DeleteResult.Failed
        val mine = day.myEntry?.takeIf { it.isLive } ?: return@withLock DeleteResult.Failed
        // Optimistic: not irreversible yet (states.md §4), so Today is not-written at once.
        publish(currentBond, day.copy(myEntry = null))
        when (val result = entries.deleteEntry(mine.id)) {
            is Result.Success -> {
                load()
                DeleteResult.Deleted
            }
            is Result.Failure -> if (result.error.isNotFound()) {
                load()
                DeleteResult.Gone
            } else {
                publish(currentBond, day)
                DeleteResult.Failed
            }
        }
    }

    override suspend fun deleteAfterReveal(): DeleteResult = mutex.withLock {
        val day = today ?: return@withLock DeleteResult.Failed
        val currentBond = bond ?: return@withLock DeleteResult.Failed
        val mine = day.myEntry?.takeIf { it.isLive } ?: return@withLock DeleteResult.Failed
        when (val result = entries.deleteEntry(mine.id)) {
            is Result.Success -> {
                // The tombstone, only now that the server has answered 204.
                publish(currentBond, day.copy(myEntry = mine.copy(text = null, status = EntryStatus.DELETED)))
                load()
                DeleteResult.Deleted
            }
            is Result.Failure -> if (result.error.isNotFound()) {
                load()
                DeleteResult.Gone
            } else {
                DeleteResult.Failed
            }
        }
    }

    override fun clear() {
        generation++
        bond = null
        today = null
        _state.value = DailyLoopState.Loading
    }

    /** Call with [mutex] held. */
    private suspend fun load() {
        val startedIn = generation
        val known = bond
        val (listed, prefetched) = coroutineScope {
            val list = async { bonds.bonds() }
            val day = known?.let { async { entries.today(it.id) } }
            list.await() to day?.await()
        }
        if (startedIn != generation) return
        val open = when (listed) {
            is Result.Failure -> return failed()
            is Result.Success -> listed.data.firstOrNull { it.isOpen }
        }
        if (open == null) {
            bond = null
            today = null
            _state.value = DailyLoopState.NoOpenBond
            return
        }
        val answer = if (prefetched != null && open.id == known?.id) prefetched else entries.today(open.id)
        if (startedIn != generation) return
        when (answer) {
            is Result.Failure -> failed()
            is Result.Success -> if (!publish(open, answer.data)) failed()
        }
    }

    /** False when the day cannot be shown honestly ([toDayView]); [state] is left as it was. */
    private fun publish(bond: Bond, today: Today): Boolean {
        val view = toDayView(bond, today) ?: return false
        this.bond = bond
        this.today = today
        _state.value = DailyLoopState.Ready(view)
        return true
    }

    private fun withMine(entry: MyEntry) {
        val currentBond = bond ?: return
        val day = today ?: return
        publish(currentBond, day.copy(myEntry = entry))
    }

    /** A failed refresh keeps what is shown (states.md §10: no "may be out of date" banner); a failed first load is the error. */
    private fun failed() {
        if (_state.value !is DailyLoopState.Ready) _state.value = DailyLoopState.Failed
    }

    private fun DataError.Network.isNotFound(): Boolean = this is DataError.Network.Api && problem.status == 404

    private fun DataError.Network.isConflictOrGone(): Boolean =
        this is DataError.Network.Api && (problem.status == 409 || problem.status == 404)
}
