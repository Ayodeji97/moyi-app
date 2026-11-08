package com.moyi.app.feature.today.common

import com.moyi.app.core.DataError
import com.moyi.app.core.Result
import com.moyi.app.domain.bond.Bond
import com.moyi.app.domain.bond.BondRepository
import com.moyi.app.domain.today.CachedDay
import com.moyi.app.domain.today.EntryStatus
import com.moyi.app.domain.today.MyEntry
import com.moyi.app.domain.today.PartnerEntry
import com.moyi.app.domain.today.Today
import com.moyi.app.domain.today.TodayCache
import com.moyi.app.domain.today.TodayRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/**
 * The real [DailyLoop], over `GET /bonds`, the daily-loop endpoints and the local
 * [TodayCache] (doc 13 §5: Today is cache-then-network). Drafts and the outbox are
 * still owed, so an entry is never queued.
 *
 * **Which bond**: the person's first open one in `GET /bonds` (newest first), as
 * the signed-in start chose it. There is no screen for more than one (states.md §2).
 * None open any more → [DailyLoopState.NoOpenBond], and the shell asks the signed-in
 * start again rather than guess.
 *
 * **The cache**: the first load of a session draws the cached day at once, if there
 * is one for this person and it is still today's Bond-day in the bond's own time
 * zone (a day cached last night is never shown as today), then refreshes behind it.
 * With no usable cache it is the bare surface until the answer arrives, as before.
 * Every day the server confirms replaces the cached one; the server always wins. The
 * partner's words are written only in the layouts allowed to show them
 * ([PartnerVisible]); in any other layout they are dropped before the write.
 *
 * **Round trips**: with a bond known (from the cache, or an earlier load), `GET
 * /bonds` and `GET …/today` run side by side (states.md §3: no chained round trips);
 * the bond list is read again because a partner joining, or the bond ending, changes
 * the layout. Only a first launch with nothing cached has to ask for the bond first.
 *
 * Every load and every write runs under one [Mutex], so an answer can never be
 * overwritten by an older one that arrived later, and an optimistic delete is never
 * undone by a refresh that started before it.
 */
class RepositoryDailyLoop(
    private val bonds: BondRepository,
    private val entries: TodayRepository,
    private val cache: TodayCache,
    private val clock: Clock = Clock.System,
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
        // Taken before the lock: a sign-out while this waited for it must not let it publish.
        val startedIn = generation
        mutex.withLock {
            val cachedBondId = if (bond == null) showCached(startedIn) else null
            load(startedIn, cachedBondId)
        }
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
        // Not cached until the server agrees: a cold start must not show a delete that failed.
        publish(currentBond, day.copy(myEntry = null), confirmed = false)
        when (val result = entries.deleteEntry(mine.id)) {
            is Result.Success -> {
                publish(currentBond, day.copy(myEntry = null))
                load()
                DeleteResult.Deleted
            }
            is Result.Failure -> if (result.error.isNotFound()) {
                load()
                DeleteResult.Gone
            } else {
                publish(currentBond, day, confirmed = false)
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

    /**
     * Forgets the day in memory. The cached copy stays: it is the person's own, for
     * the next launch, and the session wipes it when the session ends.
     */
    override fun clear() {
        generation++
        bond = null
        today = null
        _state.value = DailyLoopState.Loading
    }

    /**
     * The cached day, drawn at once if it is this person's and still today's
     * Bond-day. Never over a day already shown, never after a [clear] that came
     * meanwhile. Call with [mutex] held.
     *
     * Returns the cached bond's id either way: even a day too old to show names the
     * bond to ask about, so the first load can still ask for the bonds and the day
     * side by side.
     */
    private suspend fun showCached(startedIn: Int): String? {
        if (startedIn != generation) return null
        val cached = cache.read() ?: return null
        if (startedIn != generation || bond != null) return null
        if (cached.isCurrentBondDay()) publish(cached.bond, cached.today, confirmed = false)
        return cached.bond.id
    }

    /** False when the bond's zone is unknown or not a zone: then no day can be shown as today. */
    private fun CachedDay.isCurrentBondDay(): Boolean {
        val zone = bond.anchorTimezone?.let { runCatching { TimeZone.of(it) }.getOrNull() } ?: return false
        val date = runCatching { LocalDate.parse(today.date) }.getOrNull() ?: return false
        return date == clock.todayIn(zone)
    }

    /**
     * Call with [mutex] held. Publishes nothing once [generation] has moved on from
     * [startedIn]. The day is asked for alongside the bonds when the bond is known,
     * from what is shown or, failing that, [cachedBondId].
     */
    private suspend fun load(startedIn: Int = generation, cachedBondId: String? = null) {
        val knownId = bond?.id ?: cachedBondId
        val (listed, prefetched) = coroutineScope {
            val list = async { bonds.bonds() }
            val day = knownId?.let { async { entries.today(it) } }
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
            cache.clear()
            _state.value = DailyLoopState.NoOpenBond
            return
        }
        val answer = if (prefetched != null && open.id == knownId) prefetched else entries.today(open.id)
        if (startedIn != generation) return
        when (answer) {
            is Result.Failure -> failed()
            is Result.Success -> if (!publish(open, answer.data)) failed()
        }
    }

    /**
     * Shows [today] in [bond]. False when the day cannot be shown honestly
     * ([toDayView]); [state] is left as it was. A [confirmed] day (the server's
     * answer, or a write it accepted) also replaces the cached one.
     */
    private suspend fun publish(bond: Bond, today: Today, confirmed: Boolean = true): Boolean {
        val view = toDayView(bond, today) ?: return false
        this.bond = bond
        this.today = today
        _state.value = DailyLoopState.Ready(view)
        if (confirmed) cache.write(CachedDay(bond.copy(invite = null), today.forCache(view.status)))
        return true
    }

    private suspend fun withMine(entry: MyEntry) {
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

/**
 * [this] day as it may be kept on the device: the partner's words only in the
 * layouts allowed to show them ([PartnerVisible]). Anywhere else an entry that
 * carries words is kept as [PartnerEntry.Locked], which carries none: the layout
 * does not change ([todayStatus] reads the partner's words only on a SOLO day, and
 * that day's layout, Solo-they-wrote, is one that may show them).
 */
internal fun Today.forCache(layout: TodayStatus): Today = when {
    layout in PartnerVisible -> this
    partnerEntry.words() == null -> this
    else -> copy(partnerEntry = PartnerEntry.Locked)
}
