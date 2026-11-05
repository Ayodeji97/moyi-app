package com.moyi.app.feature.today.archive

import com.moyi.app.core.Result
import com.moyi.app.domain.archive.ArchiveDay
import com.moyi.app.domain.archive.ArchiveRepository
import com.moyi.app.domain.today.EntryStatus
import com.moyi.app.domain.today.PartnerEntry
import com.moyi.app.feature.today.common.DailyLoop
import com.moyi.app.feature.today.common.DailyLoopState
import com.moyi.app.feature.today.common.PartnerVisible
import com.moyi.app.feature.today.common.TodayStatus
import com.moyi.app.feature.today.common.todayStatus
import com.moyi.app.feature.today.common.words
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDate

/**
 * The days the Archive has loaded, shared by the list and the day view (a Koin
 * `single`), so a day opened from the list shows at once, and a delete in the day
 * view leaves its tombstone in the list too, with no second fetch.
 *
 * **Which bond**: the one Today is showing ([DailyLoop]). Archive is reached only
 * from Today's calendar target, so the loop has a day; without one the first load
 * fails, and Try again asks again.
 *
 * **Paging**: newest first, `cursor` passed back unchanged. **Only a null cursor
 * means the end** (the contract): a short page, or one with nothing to draw, is not.
 *
 * Every load runs under one [Mutex]; [clear] moves [generation] on so an answer in
 * flight cannot publish into the next session. The list ViewModel clears it on its
 * way in and out, so no one's words outlive the screen that showed them.
 */
class ArchiveFeed(
    private val loop: DailyLoop,
    private val archive: ArchiveRepository,
) {
    private val _state = MutableStateFlow(FeedState())
    val state: StateFlow<FeedState> = _state.asStateFlow()

    private val mutex = Mutex()
    private var generation = 0

    /**
     * The newest page, replacing whatever was loaded. A failure is [FeedLoad.Failed]
     * (nothing to keep). With [favourites], only the days holding an entry I favourited
     * (`favourites=true`); every later page of this load asks the same way.
     */
    suspend fun loadFirst(favourites: Boolean = false) = loadFirst(FeedState(load = FeedLoad.Loading, favourites = favourites))

    /**
     * The first page of the days matching [query] (`GET …/search`), replacing whatever was
     * loaded; every later page of this load is of the same search. Nothing is matched
     * here: the results are only what the server returned, so the partner's words that
     * are not revealed to me can never be found (they are not on the device either).
     */
    suspend fun search(query: String) = loadFirst(FeedState(load = FeedLoad.Loading, query = query))

    private suspend fun loadFirst(asked: FeedState) {
        _state.value = asked
        mutex.withLock {
            val startedIn = generation
            val bond = (loop.state.value as? DailyLoopState.Ready)?.day
            if (bond == null) {
                _state.value = asked.copy(load = FeedLoad.Failed)
                return
            }
            val answer = archive.page(bond.bondId, asked, cursor = null)
            // Stale: the Archive was left, or another load (a newer query, a chip) has
            // replaced this one while it was on its way. Its answer is dropped.
            if (startedIn != generation || !_state.value.sameLoadAs(asked)) return
            _state.value = when (answer) {
                is Result.Failure -> asked.copy(load = FeedLoad.Failed)
                is Result.Success -> asked.copy(
                    load = FeedLoad.Ready,
                    bondId = bond.bondId,
                    partnerName = bond.partnerName,
                    days = answer.data.days,
                    nextCursor = answer.data.nextCursor,
                    pages = 1,
                )
            }
        }
    }

    /**
     * The page after the last one, appended. Does nothing at the end (a null cursor)
     * or while a page is already on its way. A failure keeps the list, and says so
     * ([FeedState.pageFailed]) until the next try.
     */
    suspend fun loadMore() {
        val before = _state.value
        if (before.load != FeedLoad.Ready || before.nextCursor == null || before.loadingMore) return
        _state.update { it.copy(loadingMore = true, pageFailed = false) }
        mutex.withLock {
            val startedIn = generation
            val current = _state.value
            val cursor = current.nextCursor ?: return
            val bondId = current.bondId ?: return
            val answer = archive.page(bondId, current, cursor)
            if (startedIn != generation || !_state.value.sameLoadAs(current)) return
            _state.update {
                when (answer) {
                    is Result.Failure -> it.copy(loadingMore = false, pageFailed = true)
                    is Result.Success -> it.copy(
                        // A day already shown is never shown twice, whatever a page says.
                        days = it.days + answer.data.days.filter { new -> it.days.none { old -> old.date == new.date } },
                        nextCursor = answer.data.nextCursor,
                        loadingMore = false,
                        pageFailed = false,
                        pages = it.pages + 1,
                    )
                }
            }
        }
    }

    /** The loaded day for [date], if the list has it. */
    fun find(date: String): ArchiveDay? = _state.value.days.firstOrNull { it.date == date }

    /** [day] in place of the loaded day with its date: the tombstone after a delete. */
    fun replace(day: ArchiveDay) {
        _state.update { feed -> feed.copy(days = feed.days.map { if (it.date == day.date) day else it }) }
    }

    /**
     * [markedByMe] in place of the marks on the loaded day for [date]: the optimistic
     * favourite toggle, and its revert. The list redraws from it when you go back.
     */
    fun setMarks(date: String, markedByMe: Set<String>) {
        _state.update { feed -> feed.copy(days = feed.days.map { if (it.date == date) it.copy(markedByMe = markedByMe) else it }) }
    }

    /** Forgets every day: a sign-out, or leaving the Archive. */
    fun clear() {
        generation++
        _state.value = FeedState()
    }
}

/** The filtered list, the search or the plain feed, by what [asked] was loaded with. */
private suspend fun ArchiveRepository.page(bondId: String, asked: FeedState, cursor: String?) =
    asked.query?.let { search(bondId, it, cursor) } ?: days(bondId, cursor, favourites = asked.favourites)

enum class FeedLoad { Loading, Failed, Ready }

/** What the feed holds. Its `toString` names no words: [ArchiveDay]'s entries redact theirs, and the query is redacted. */
data class FeedState(
    val load: FeedLoad = FeedLoad.Loading,
    /** Loaded with `favourites=true`: only days holding an entry I favourited. */
    val favourites: Boolean = false,
    /** Loaded from `GET …/search` with this `q`: only the days the server matched. Null for the feed. */
    val query: String? = null,
    val bondId: String? = null,
    val partnerName: String? = null,
    val days: List<ArchiveDay> = emptyList(),
    val nextCursor: String? = null,
    val loadingMore: Boolean = false,
    val pageFailed: Boolean = false,
    /** Pages loaded so far: the list asks again near its end each time this moves. */
    val pages: Int = 0,
) {
    /** The same list as [other] was asked for: same filter, same query. */
    fun sameLoadAs(other: FeedState): Boolean = favourites == other.favourites && query == other.query

    // The query is a person's own words: never in a log, like the entries.
    override fun toString(): String =
        "FeedState(load=$load, favourites=$favourites, query=${query?.let { "(redacted)" }}, bondId=$bondId, partnerName=$partnerName, " +
            "days=$days, nextCursor=$nextCursor, loadingMore=$loadingMore, pageFailed=$pageFailed, pages=$pages)"
}

/**
 * A day as the Archive draws it, **from its entries, never its status** (the
 * contract). Built by [toArchiveDayUi], which applies Today's rules ([todayStatus],
 * [PartnerVisible]), so the partner's words are held only when the server sent them
 * in full and the day may show them (REVEALED or SOLO). A locked entry is not even
 * named: Archive, like Today, shows no partner status (states.md §3).
 */
data class ArchiveDayUi(
    val date: LocalDate,
    /** Mine, live; null when I wrote nothing that day or deleted it. */
    val myEntry: String?,
    /** I deleted mine after the reveal: its tombstone, labelled "From you". */
    val myTombstone: Boolean,
    val myEntryId: String?,
    /** The partner's words, only where the day may show them. */
    val partnerEntry: String?,
    /** My live entry on a revealed day: "Delete what you wrote" (with its confirm). */
    val canDelete: Boolean,
    /** The id of the partner's entry, when its words are held. */
    val partnerEntryId: String? = null,
    /**
     * **My** mark on my own entry: the FavouriteToggle under it. Null when the toggle is
     * **absent** (states.md §6: absent, not disabled): no live entry, or not revealed.
     */
    val myMarkOnMine: Boolean? = null,
    /** **My** mark on the partner's entry. Null when absent: not revealed to me, or no words. */
    val myMarkOnTheirs: Boolean? = null,
) {
    /**
     * The day card's small filled bookmark: the day holds an entry **I** favourited.
     * There is no field for the partner's favourites: states.md §6 says nothing may
     * disclose or let you infer them, so the state cannot even hold one.
     */
    val holdsMyFavourite: Boolean get() = myMarkOnMine == true || myMarkOnTheirs == true

    override fun toString(): String =
        "ArchiveDayUi(date=$date, myEntry=${myEntry?.let { "(redacted)" }}, myTombstone=$myTombstone, " +
            "myEntryId=$myEntryId, partnerEntry=${partnerEntry?.let { "(redacted)" }}, canDelete=$canDelete, " +
            "partnerEntryId=$partnerEntryId, myMarkOnMine=$myMarkOnMine, myMarkOnTheirs=$myMarkOnTheirs)"
}

/**
 * The days the list draws: every drawable day, or, when the feed was loaded with
 * `favourites=true`, only those that still hold my favourite. Filtering here as well
 * as on the server is what drops a day the moment its last mark is removed in the day
 * view, without asking again.
 */
internal fun FeedState.visibleDays(): List<ArchiveDayUi> =
    days.mapNotNull { it.toArchiveDayUi() }.let { all -> if (favourites) all.filter { it.holdsMyFavourite } else all }

/** Null when there is nothing to draw (no entry of mine, no words of theirs), or the date is not ISO. */
internal fun ArchiveDay.toArchiveDayUi(): ArchiveDayUi? {
    val date = runCatching { LocalDate.parse(date) }.getOrNull() ?: return null
    // A past day: the bond's waiting-for-a-member layout does not apply to it.
    val layout = todayStatus(status, myEntry, partnerEntry, waitingForPartnerToJoin = false)
    val entry = myEntry
    val mine = entry?.takeIf { it.isLive }?.text
    val tombstone = entry != null && !entry.isLive
    val theirs = partnerEntry.words()?.takeIf { layout in PartnerVisible }
    if (mine == null && !tombstone && theirs == null) return null
    // states.md §6: only after the reveal, and the contract marks only an entry it shows
    // as REVEALED; a solo day's unlocked entry is still SUBMITTED, so it has no toggle.
    val revealed = layout == TodayStatus.Revealed
    val theirEntry = partnerEntry as? PartnerEntry.Revealed
    return ArchiveDayUi(
        date = date,
        myEntry = mine,
        myTombstone = tombstone,
        myEntryId = entry?.id,
        partnerEntry = theirs,
        canDelete = revealed,
        partnerEntryId = partnerEntry.idIfWords().takeIf { theirs != null },
        // Each entry by its own status: theirs stays markable after I deleted mine.
        myMarkOnMine = entry?.takeIf { mine != null && it.status == EntryStatus.REVEALED }?.let { it.id in markedByMe },
        myMarkOnTheirs = theirEntry?.takeIf { theirs != null }?.let { it.id in markedByMe },
    )
}

private fun PartnerEntry?.idIfWords(): String? = when (this) {
    is PartnerEntry.Revealed -> id
    is PartnerEntry.Submitted -> id
    else -> null
}
