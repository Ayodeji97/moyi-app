package com.moyi.app.domain.archive

/**
 * The Archive's days kept on this device, so opening it draws what was last seen at
 * once and refreshes behind it (doc 13 §5: "paged cache in SQLDelight; older pages
 * fetched on demand and retained"; doc 14: cache-then-network).
 *
 * **One run, no gaps**: the cache holds the days from the newest page the server last
 * answered down to the oldest page kept, for the person signed in and one bond, newest
 * first. The cursor after that run is kept with it, so the next page is asked for exactly
 * where the run ends. Writing another bond's or another person's run replaces it, and the
 * session wipes it all when a session ends or starts.
 *
 * **The server always wins**: a page replaces the cached days of every date it covers,
 * so a day gone on the server (deleted, erased, no longer listed) is gone here too, and a
 * day it answers in another shape (a tombstone, a favourite changed on another phone) is
 * replaced. A page that cannot be joined to the run without a gap starts a new run.
 *
 * **Bounded**: at most [ARCHIVE_CACHE_MAX_DAYS] days; past that, the oldest pages are
 * dropped (they are asked for again when someone scrolls to them).
 *
 * What is written is the caller's to make safe, as with `TodayCache`: the Archive strips
 * the partner's words from every day not allowed to show them before it writes. Search
 * results are never written (the contract says they are not to be stored).
 * Signed out, every read is null and every write does nothing.
 */
interface ArchiveCache {
    /**
     * The cached run of [bondId], newest first, with the cursor that continues after its
     * oldest day ([ArchivePage.nextCursor]: null means the run reaches the archive's end).
     * Null when nothing is cached for this person and bond.
     */
    suspend fun read(bondId: String): ArchivePage?

    /** The cached [date] of [bondId], or null. */
    suspend fun day(bondId: String, date: String): ArchiveDay?

    /**
     * [page], answered for [bondId] with the cursor whose page ended on [after]; null
     * [after] for the newest page. It replaces every cached day in the dates it covers:
     * on or after its oldest day (every date, when it is the last page) and before
     * [after]. A page that does not meet the run is not kept, so the run never has a gap.
     */
    suspend fun putPage(bondId: String, page: ArchivePage, after: String?)

    /**
     * [days], answered for [bondId] outside the run's own paging (a filtered list, a month
     * jump, the day view), each in place of the cached day with its date. A day not
     * cached is not added: it could not be placed in the run without a gap.
     */
    suspend fun update(bondId: String, days: List<ArchiveDay>)

    /** The server confirmed my mark on [entryId] of [date] is [marked]. */
    suspend fun setMark(bondId: String, date: String, entryId: String, marked: Boolean)

    /** [date] is not in the archive any more (`404 DAY_NOT_FOUND`). */
    suspend fun remove(bondId: String, date: String)
}

/**
 * At most this many days are kept: ten pages of twenty, about the last half year of a
 * couple writing daily. Recent days are what someone opening the Archive on a train is
 * most likely to look for; they are read in one query; and at the contract's cap of
 * 256 KB a page (NFR-008) they are never more than 2.5 MB on the device, usually a few
 * hundred KB. Older days are a scroll and a request away.
 */
const val ARCHIVE_CACHE_MAX_DAYS = 200
