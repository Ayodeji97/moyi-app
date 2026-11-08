package com.moyi.app.feature.today.archive

import com.moyi.app.core.Result
import com.moyi.app.designsystem.component.EntryPhoto
import com.moyi.app.designsystem.component.VoiceNote
import com.moyi.app.domain.archive.ArchiveCache
import com.moyi.app.domain.archive.ArchiveDay
import com.moyi.app.domain.archive.ArchivePage
import com.moyi.app.domain.archive.ArchiveRepository
import com.moyi.app.domain.archive.joinPage
import com.moyi.app.domain.today.EntryStatus
import com.moyi.app.domain.today.PartnerEntry
import com.moyi.app.feature.today.common.DailyLoop
import com.moyi.app.feature.today.common.DailyLoopState
import com.moyi.app.feature.today.common.PartnerVisible
import com.moyi.app.feature.today.common.TodayStatus
import com.moyi.app.feature.today.common.image
import com.moyi.app.feature.today.common.toEntryPhoto
import com.moyi.app.feature.today.common.toVoiceNote
import com.moyi.app.feature.today.common.voice
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
 * **Cache-then-network** (doc 13 §5, doc 14): the plain list (no filter, no search, no
 * month jump) is drawn from [cache] at once, then the newest page is asked for and joined
 * in, the server winning ([joinPage]). Offline, the cached days stay, with no error; a
 * known-empty archive is its empty state. Favourites and a month jump ask the server first
 * and, when it cannot answer, draw the cached days they match ([fromCache]); a search never
 * does. The
 * cursor then walks down from the newest page, so each cached page is asked for again as
 * the list reaches it, and replaced, before older days are added; a page that fails shows
 * the failure at the end of the list only, never over the days above it. Every page the
 * server answers is written to [cache] (the plain list's pages in place; the other lists'
 * days only over days already cached); search results never are (the contract says they
 * are not to be stored). Before anything is written, the partner's words are stripped from
 * every day not allowed to show them ([cacheable], BR-1).
 *
 * Every load runs under one [Mutex]; [clear] moves [generation] on so an answer in
 * flight cannot publish into the next session, nor be written to the cache. The list
 * ViewModel clears it on its way in and out, so no one's words outlive the screen that
 * showed them (the cache is the session's, and the session wipes it).
 */
class ArchiveFeed(
    private val loop: DailyLoop,
    private val archive: ArchiveRepository,
    private val cache: ArchiveCache,
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
    suspend fun loadFirst(favourites: Boolean = false, until: LocalDate? = null) =
        loadFirst(FeedState(load = FeedLoad.Loading, favourites = favourites, until = until))

    /** The current Bond-day, the newest a month jump may reach (no month after it). Null without a day. */
    val bondDay: LocalDate? get() = (loop.state.value as? DailyLoopState.Ready)?.day?.date

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
            // Stale: the Archive was left, or another load (a newer query, a chip) has
            // replaced this one while it was on its way. Its answer is dropped.
            fun stale() = startedIn != generation || !_state.value.sameLoadAs(asked)
            // An empty cached run is a known-empty archive: its empty state, offline too.
            val cached = if (asked.plain) cache.read(bond.bondId) else null
            if (stale()) return
            if (cached != null) {
                _state.value = asked.copy(
                    load = FeedLoad.Ready,
                    bondId = bond.bondId,
                    partnerName = bond.partnerName,
                    days = cached.days,
                    nextCursor = cached.nextCursor,
                    pageEnd = cached.days.lastOrNull()?.date,
                    pages = 1,
                )
            }
            val answer = archive.page(bond.bondId, asked, cursor = null)
            if (stale()) return
            when (answer) {
                // With cached days on screen, a failed refresh changes nothing: no error over good days.
                is Result.Failure -> if (cached == null) {
                    val fallback = fromCache(bond.bondId, asked)
                    if (stale()) return
                    _state.value = if (fallback == null) {
                        asked.copy(load = FeedLoad.Failed)
                    } else {
                        asked.copy(
                            load = FeedLoad.Ready,
                            bondId = bond.bondId,
                            partnerName = bond.partnerName,
                            days = fallback.days,
                            nextCursor = fallback.nextCursor,
                            pages = 1,
                        )
                    }
                }
                is Result.Success -> {
                    val page = answer.data
                    _state.update {
                        asked.copy(
                            load = FeedLoad.Ready,
                            bondId = bond.bondId,
                            partnerName = bond.partnerName,
                            days = if (asked.plain) joinPage(it.days, page, after = null) ?: page.days else page.days,
                            nextCursor = page.nextCursor,
                            pageEnd = page.days.lastOrNull()?.date,
                            loadingMore = it.loadingMore,
                            pages = it.pages + 1,
                        )
                    }
                    keep(bond.bondId, asked, page, after = null)
                }
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
            val after = current.pageEnd
            val answer = archive.page(bondId, current, cursor)
            if (startedIn != generation || !_state.value.sameLoadAs(current)) return
            _state.update {
                when (answer) {
                    is Result.Failure -> it.copy(loadingMore = false, pageFailed = true)
                    is Result.Success -> it.copy(
                        // The plain list joins the page in place (it may be a cached page asked
                        // again); the others append it. A day already shown is never shown twice.
                        days = (if (it.plain && after != null) joinPage(it.days, answer.data, after) else null)
                            ?: (it.days + answer.data.days.filter { new -> it.days.none { old -> old.date == new.date } }),
                        nextCursor = answer.data.nextCursor,
                        pageEnd = answer.data.days.lastOrNull()?.date ?: after,
                        loadingMore = false,
                        pageFailed = false,
                        pages = it.pages + 1,
                    )
                }
            }
            if (answer is Result.Success) keep(bondId, current, answer.data, after)
        }
    }

    /**
     * Favourites or a month jump the server could not answer, from the cached run (the
     * owner's ruling, 2026-11-08): the cached days the filter would match (holding a mark of
     * mine; on or before `until`), with the cursor that continues after the run. The run has
     * no gaps, so these are every such day in the dates it covers, and the next page is asked
     * for past it as usual: offline that page fails at the end of the list only. A month older
     * than the run draws nothing with a cursor, so the list asks past it and shows the failure
     * it showed before. Null for a search (the contract: its results are never stored, so it
     * is online only) and for the plain list (drawn from the cache before it asks), and when
     * nothing is cached.
     */
    private suspend fun fromCache(bondId: String, asked: FeedState): ArchivePage? {
        if (asked.query != null || asked.plain) return null
        val run = cache.read(bondId) ?: return null
        val until = asked.until?.toString()
        val days = run.days.filter { day ->
            (until == null || day.date <= until) && (!asked.favourites || day.markedByMe.isNotEmpty())
        }
        return ArchivePage(days = days, nextCursor = run.nextCursor)
    }

    /**
     * Writes what the server answered to the cache: the plain list's page in place, the
     * other lists' days only over days already cached, and a search's never.
     */
    private suspend fun keep(bondId: String, asked: FeedState, page: ArchivePage, after: String?) {
        val safe = page.copy(days = page.days.map { it.cacheable() })
        when {
            asked.query != null -> Unit
            asked.plain -> cache.putPage(bondId, safe, after)
            else -> cache.update(bondId, safe.days)
        }
    }

    /** The cached [date], for the day view to draw before it asks. Null when not cached. */
    suspend fun cached(date: String): ArchiveDay? = currentBondId()?.let { cache.day(it, date) }

    /** [day], as the server just answered it, over the cached day with its date (if cached). */
    suspend fun keep(day: ArchiveDay) {
        val bondId = currentBondId() ?: return
        cache.update(bondId, listOf(day.cacheable()))
    }

    /** The server confirmed my mark on [entryId] of [date]: the cache follows. */
    suspend fun keepMark(date: String, entryId: String, marked: Boolean) {
        val bondId = currentBondId() ?: return
        cache.setMark(bondId, date, entryId, marked)
    }

    /** [date] is not in the archive any more (`404`): gone from the list and the cache. */
    suspend fun forget(date: String) {
        _state.update { feed -> feed.copy(days = feed.days.filterNot { it.date == date }) }
        val bondId = currentBondId() ?: return
        cache.remove(bondId, date)
    }

    private fun currentBondId(): String? = _state.value.bondId ?: (loop.state.value as? DailyLoopState.Ready)?.day?.bondId

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

/**
 * The filtered list, the search or the plain feed, by what [asked] was loaded with. A month
 * jump's `until` goes with every page of it, the cursor too (the contract: both apply).
 */
private suspend fun ArchiveRepository.page(bondId: String, asked: FeedState, cursor: String?) =
    asked.query?.let { search(bondId, it, cursor) }
        ?: days(bondId, cursor, favourites = asked.favourites, until = asked.until?.toString())

enum class FeedLoad { Loading, Failed, Ready }

/** What the feed holds. Its `toString` names no words: [ArchiveDay]'s entries redact theirs, and the query is redacted. */
data class FeedState(
    val load: FeedLoad = FeedLoad.Loading,
    /** Loaded with `favourites=true`: only days holding an entry I favourited. */
    val favourites: Boolean = false,
    /** Loaded from `GET …/search` with this `q`: only the days the server matched. Null for the feed. */
    val query: String? = null,
    /** Loaded from a month jump: the list starts at this date or the nearest earlier day. Null for the newest. */
    val until: LocalDate? = null,
    val bondId: String? = null,
    val partnerName: String? = null,
    val days: List<ArchiveDay> = emptyList(),
    val nextCursor: String? = null,
    /**
     * The plain list's: the oldest day of the last page answered (or of the cached run, before
     * any), where the next page starts. The next page covers the dates before it ([joinPage]).
     */
    val pageEnd: String? = null,
    val loadingMore: Boolean = false,
    val pageFailed: Boolean = false,
    /** Pages loaded so far: the list asks again near its end each time this moves. */
    val pages: Int = 0,
) {
    /** The same list as [other] was asked for: same filter, same query, same month jump. */
    /** The plain list: no filter, no search, no month jump. The only one drawn from the cache, and kept in it page by page. */
    val plain: Boolean get() = !favourites && query == null && until == null

    fun sameLoadAs(other: FeedState): Boolean = favourites == other.favourites && query == other.query && until == other.until

    // The query is a person's own words: never in a log, like the entries.
    override fun toString(): String =
        "FeedState(load=$load, favourites=$favourites, query=${query?.let { "(redacted)" }}, until=$until, bondId=$bondId, partnerName=$partnerName, " +
            "days=$days, nextCursor=$nextCursor, pageEnd=$pageEnd, loadingMore=$loadingMore, pageFailed=$pageFailed, pages=$pages)"
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
    /** The photo on my live entry. */
    val myPhoto: EntryPhoto? = null,
    /** The partner's photo, only where [partnerEntry] is held. */
    val partnerPhoto: EntryPhoto? = null,
    /** The voice note on my live entry. */
    val myVoice: VoiceNote? = null,
    /** The partner's voice note, only where [partnerEntry] is held. */
    val partnerVoice: VoiceNote? = null,
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
            "myPhoto=$myPhoto, partnerPhoto=$partnerPhoto, myVoice=$myVoice, partnerVoice=$partnerVoice, partnerEntryId=$partnerEntryId, myMarkOnMine=$myMarkOnMine, myMarkOnTheirs=$myMarkOnTheirs)"
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
        myPhoto = entry?.takeIf { mine != null }?.image?.toEntryPhoto(),
        partnerPhoto = partnerEntry.image()?.takeIf { theirs != null }?.toEntryPhoto(),
        myVoice = entry?.takeIf { mine != null }?.voice?.toVoiceNote(),
        partnerVoice = partnerEntry.voice()?.takeIf { theirs != null }?.toVoiceNote(),
        canDelete = revealed,
        partnerEntryId = partnerEntry.idIfWords().takeIf { theirs != null },
        // Each entry by its own status: theirs stays markable after I deleted mine.
        myMarkOnMine = entry?.takeIf { mine != null && it.status == EntryStatus.REVEALED }?.let { it.id in markedByMe },
        myMarkOnTheirs = theirEntry?.takeIf { theirs != null }?.let { it.id in markedByMe },
    )
}

/**
 * [this] as it may be kept on the device (BR-1): the partner's words only on a day that may
 * show them, by the same rule the list draws by ([toArchiveDayUi]). The server sends words
 * only where they may be read, so this changes nothing today; it is here so that a day
 * the Archive would not show is never on the disk either. Without words, their entry is
 * kept as [PartnerEntry.Locked], and so has no mark.
 */
internal fun ArchiveDay.cacheable(): ArchiveDay {
    val words = partnerEntry.idIfWords() ?: return this
    if (toArchiveDayUi()?.partnerEntry != null) return this
    return copy(partnerEntry = PartnerEntry.Locked, markedByMe = markedByMe - words)
}

private fun PartnerEntry?.idIfWords(): String? = when (this) {
    is PartnerEntry.Revealed -> id
    is PartnerEntry.Submitted -> id
    else -> null
}
