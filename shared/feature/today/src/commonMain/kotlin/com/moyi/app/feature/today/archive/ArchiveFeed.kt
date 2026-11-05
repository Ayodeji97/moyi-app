package com.moyi.app.feature.today.archive

import com.moyi.app.core.Result
import com.moyi.app.domain.archive.ArchiveDay
import com.moyi.app.domain.archive.ArchiveRepository
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

    /** The newest page, replacing whatever was loaded. A failure is [FeedLoad.Failed] (nothing to keep). */
    suspend fun loadFirst() {
        _state.value = FeedState(load = FeedLoad.Loading)
        mutex.withLock {
            val startedIn = generation
            val bond = (loop.state.value as? DailyLoopState.Ready)?.day
            if (bond == null) {
                _state.value = FeedState(load = FeedLoad.Failed)
                return
            }
            val answer = archive.days(bond.bondId)
            if (startedIn != generation) return
            _state.value = when (answer) {
                is Result.Failure -> FeedState(load = FeedLoad.Failed)
                is Result.Success -> FeedState(
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
            val answer = archive.days(bondId, cursor)
            if (startedIn != generation) return
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

    /** Forgets every day: a sign-out, or leaving the Archive. */
    fun clear() {
        generation++
        _state.value = FeedState()
    }
}

enum class FeedLoad { Loading, Failed, Ready }

/** What the feed holds. Its `toString` names no words: [ArchiveDay]'s entries redact theirs. */
data class FeedState(
    val load: FeedLoad = FeedLoad.Loading,
    val bondId: String? = null,
    val partnerName: String? = null,
    val days: List<ArchiveDay> = emptyList(),
    val nextCursor: String? = null,
    val loadingMore: Boolean = false,
    val pageFailed: Boolean = false,
    /** Pages loaded so far: the list asks again near its end each time this moves. */
    val pages: Int = 0,
)

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
) {
    override fun toString(): String =
        "ArchiveDayUi(date=$date, myEntry=${myEntry?.let { "(redacted)" }}, myTombstone=$myTombstone, " +
            "myEntryId=$myEntryId, partnerEntry=${partnerEntry?.let { "(redacted)" }}, canDelete=$canDelete)"
}

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
    return ArchiveDayUi(
        date = date,
        myEntry = mine,
        myTombstone = tombstone,
        myEntryId = entry?.id,
        partnerEntry = theirs,
        canDelete = layout == TodayStatus.Revealed,
    )
}
