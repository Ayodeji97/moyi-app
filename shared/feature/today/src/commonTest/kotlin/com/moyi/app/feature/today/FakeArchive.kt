package com.moyi.app.feature.today

import com.moyi.app.domain.today.Reaction
import com.moyi.app.core.ApiErrorCode
import com.moyi.app.core.ApiProblem
import com.moyi.app.core.DataError
import com.moyi.app.core.EmptyResult
import com.moyi.app.core.Result
import com.moyi.app.domain.archive.ArchiveCache
import com.moyi.app.domain.archive.ArchiveDay
import com.moyi.app.domain.archive.ArchivePage
import com.moyi.app.domain.archive.ArchiveRepository
import com.moyi.app.domain.archive.joinPage
import com.moyi.app.domain.today.BondDayStatus
import com.moyi.app.domain.today.EntryStatus
import com.moyi.app.domain.today.MyEntry
import com.moyi.app.domain.today.PartnerEntry
import com.moyi.app.domain.today.Today
import com.moyi.app.domain.today.TodayRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred

/** A revealed day: mine and theirs, both in full. */
fun revealedDay(date: String, mine: String = MINE, theirs: String = THEIRS) = ArchiveDay(
    date = date,
    status = BondDayStatus.REVEALED,
    myEntry = MyEntry("mine-$date", mine, EntryStatus.REVEALED),
    partnerEntry = PartnerEntry.Revealed("theirs-$date", theirs),
)

/**
 * An [ArchiveRepository] for the ViewModel tests: [pages] by cursor (null is the first),
 * [failNext] fails the next `days` call, and [dayAnswer] answers `day`. Records cursors.
 */
class FakeArchiveRepository : ArchiveRepository {
    val pages = mutableMapOf<String?, ArchivePage>()

    /** The `favourites=true` pages, by cursor, as [pages]. */
    val favouritePages = mutableMapOf<String?, ArchivePage>()

    /** Every `days` call as (cursor, favourites). */
    val requests = mutableListOf<Pair<String?, Boolean>>()

    /** The `until` of every `days` call, in the order made. */
    val untils = mutableListOf<String?>()

    /** The pages of a month jump, by (until, cursor); [favouritePages]/[pages] when there is no until. */
    val untilPages = mutableMapOf<Pair<String, String?>, ArchivePage>()

    /** The answer to every favourite call; [markGate] holds it until completed. */
    var markResult: EmptyResult<DataError.Network> = Result.Success(Unit)
    var markGate: CompletableDeferred<Unit>? = null
    val marks = mutableListOf<Pair<String, Boolean>>()
    var failNext = false

    /** No network: every `days` and `search` call fails with NoInternet until set back. */
    var offline = false

    /** Holds the answer to every `days` call until completed: the cached days show meanwhile. */
    var daysGate: CompletableDeferred<Unit>? = null
    /** The answer to `day`; when null, the day as any of the pages here list it, else 404. */
    var dayAnswer: Result<ArchiveDay, DataError.Network>? = null
    val cursors = mutableListOf<String?>()
    val dayCalls = mutableListOf<String>()

    override suspend fun days(bondId: String, cursor: String?, favourites: Boolean, until: String?): Result<ArchivePage, DataError.Network> {
        cursors += cursor
        requests += cursor to favourites
        untils += until
        daysGate?.await()
        if (offline) return Result.Failure(DataError.Network.NoInternet)
        if (failNext) {
            failNext = false
            return Result.Failure(DataError.Network.NoInternet)
        }
        if (until != null) return untilPages[until to cursor]?.let { Result.Success(it) } ?: Result.Failure(NOT_FOUND)
        return (if (favourites) favouritePages else pages)[cursor]?.let { Result.Success(it) } ?: Result.Failure(NOT_FOUND)
    }

    /** Search pages, by (query, cursor). A query with no page answers an empty last page. */
    val searchPages = mutableMapOf<Pair<String, String?>, ArchivePage>()

    /** Every `search` call as (query, cursor), in the order made. */
    val searches = mutableListOf<Pair<String, String?>>()

    /** Holds the answer to a query until completed, so answers can be made to arrive out of order. */
    val searchGates = mutableMapOf<String, CompletableDeferred<Unit>>()

    /** The queries whose call was cancelled while waiting for its answer. */
    val cancelledSearches = mutableListOf<String>()
    var failNextSearch = false

    override suspend fun search(bondId: String, query: String, cursor: String?): Result<ArchivePage, DataError.Network> {
        searches += query to cursor
        try {
            searchGates[query]?.await()
        } catch (cancelled: CancellationException) {
            cancelledSearches += query
            throw cancelled
        }
        if (offline) return Result.Failure(DataError.Network.NoInternet)
        if (failNextSearch) {
            failNextSearch = false
            return Result.Failure(DataError.Network.NoInternet)
        }
        return Result.Success(searchPages[query to cursor] ?: ArchivePage(emptyList(), null))
    }

    override suspend fun setFavourite(entryId: String, favourite: Boolean): EmptyResult<DataError.Network> {
        marks += entryId to favourite
        markGate?.await()
        return markResult
    }

    override suspend fun day(bondId: String, date: String): Result<ArchiveDay, DataError.Network> {
        dayCalls += date
        dayAnswer?.let { return it }
        val listed = (pages.values + favouritePages.values + untilPages.values + searchPages.values).flatMap { it.days }
        return listed.firstOrNull { it.date == date }?.let { Result.Success(it) } ?: Result.Failure(NOT_FOUND)
    }
}

/** Only [deleteEntry] is the Archive's; the rest is never reached from it. */
class FakeEntries : TodayRepository {
    var deleteResult: EmptyResult<DataError.Network> = Result.Success(Unit)
    var gate: CompletableDeferred<Unit>? = null
    val deletes = mutableListOf<String>()

    override suspend fun today(bondId: String): Result<Today, DataError.Network> = error("not the Archive's")
    override suspend fun submitEntry(bondId: String, text: String, promptId: String?, idempotencyKey: String, intendedAt: kotlin.time.Instant?, imageMediaId: String?, voiceMediaId: String?) = error("not the Archive's")
    override suspend fun editEntry(entryId: String, text: String, idempotencyKey: String, imageMediaId: String?, voiceMediaId: String?, removeImage: Boolean, removeVoice: Boolean) = error("not the Archive's")

    override suspend fun deleteEntry(entryId: String): EmptyResult<DataError.Network> {
        deletes += entryId
        gate?.await()
        return deleteResult
    }

    var reactResult: EmptyResult<DataError.Network> = Result.Success(Unit)
    var reactGate: CompletableDeferred<Unit>? = null
    val reactions = mutableListOf<Pair<String, Reaction?>>()

    override suspend fun setReaction(entryId: String, reaction: Reaction?): EmptyResult<DataError.Network> {
        reactions += entryId to reaction
        reactGate?.await()
        return reactResult
    }
}

val NOT_FOUND = DataError.Network.Api(ApiProblem(404, ApiErrorCode.NOT_FOUND))

/**
 * An in-memory [ArchiveCache] for the ViewModel tests, one bond's run, joining pages by
 * the domain's own rule ([joinPage]); the SQL one is tested against SQLite in the data layer.
 */
class FakeArchiveCache : ArchiveCache {
    var run: ArchivePage? = null
    val putPages = mutableListOf<Pair<ArchivePage, String?>>()
    val updates = mutableListOf<ArchiveDay>()
    val marks = mutableListOf<Triple<String, String, Boolean>>()
    val removed = mutableListOf<String>()

    /** As the real cache: no days is nothing cached, unless it is the known-empty archive (no cursor either). */
    override suspend fun read(bondId: String): ArchivePage? = run?.takeIf { it.days.isNotEmpty() || it.nextCursor == null }

    override suspend fun day(bondId: String, date: String): ArchiveDay? = run?.days?.firstOrNull { it.date == date }

    override suspend fun putPage(bondId: String, page: ArchivePage, after: String?) {
        putPages += page to after
        val current = run
        val joined = joinPage(current?.days.orEmpty(), page, after) ?: if (after == null) page.days else return
        val keptOlder = current != null && joined.lastOrNull()?.let { last -> page.days.none { it.date == last.date } } == true
        run = ArchivePage(joined, if (keptOlder) current?.nextCursor else page.nextCursor)
    }

    override suspend fun update(bondId: String, days: List<ArchiveDay>) {
        updates += days
        val current = run ?: return
        run = current.copy(days = current.days.map { old -> days.firstOrNull { it.date == old.date } ?: old })
    }

    override suspend fun setMark(bondId: String, date: String, entryId: String, marked: Boolean) {
        marks += Triple(date, entryId, marked)
        val current = run ?: return
        run = current.copy(
            days = current.days.map { if (it.date == date) it.copy(markedByMe = if (marked) it.markedByMe + entryId else it.markedByMe - entryId) else it },
        )
    }

    val reactions = mutableListOf<Triple<String, String, Reaction?>>()

    override suspend fun setMyReaction(bondId: String, date: String, entryId: String, reaction: Reaction?) {
        reactions += Triple(date, entryId, reaction)
        val current = run ?: return
        run = current.copy(
            days = current.days.map { day ->
                val theirs = day.partnerEntry as? PartnerEntry.Revealed
                if (day.date == date && theirs?.id == entryId) day.copy(partnerEntry = theirs.copy(reaction = reaction)) else day
            },
        )
    }

    override suspend fun remove(bondId: String, date: String) {
        removed += date
        val current = run ?: return
        run = current.copy(days = current.days.filterNot { it.date == date })
    }
}
