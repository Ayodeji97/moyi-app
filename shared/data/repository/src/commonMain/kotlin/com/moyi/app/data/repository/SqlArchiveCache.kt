package com.moyi.app.data.repository

import com.moyi.app.data.database.Archive_day
import com.moyi.app.data.database.LocalDatabase
import com.moyi.app.data.database.MoyiDatabase
import com.moyi.app.domain.archive.ARCHIVE_CACHE_MAX_DAYS
import com.moyi.app.domain.archive.ArchiveCache
import com.moyi.app.domain.archive.ArchiveDay
import com.moyi.app.domain.archive.ArchivePage
import com.moyi.app.domain.auth.SessionState
import com.moyi.app.domain.today.BondDayStatus
import com.moyi.app.domain.today.EntryStatus
import com.moyi.app.domain.today.MyEntry
import com.moyi.app.domain.today.PartnerEntry
import kotlinx.coroutines.flow.StateFlow

/**
 * [ArchiveCache] over the local database's `archive_day` table, keyed by the signed-in
 * person's id ([session]) and the bond. Signed out, or still deciding, there is no
 * person: reads are null and writes do nothing.
 *
 * Every write is one transaction, so the run is never seen half replaced. Dates are ISO
 * (`YYYY-MM-DD`), so comparing them as text orders them as dates.
 *
 * A row this build cannot read back (an enum name it does not know, a shape with a
 * missing field) makes the whole run no cache at all, never a guess: the Archive then
 * loads as it did before there was a cache.
 */
class SqlArchiveCache(
    private val database: LocalDatabase,
    private val session: StateFlow<SessionState>,
) : ArchiveCache {
    private val userId: String? get() = (session.value as? SessionState.SignedIn)?.user?.id

    override suspend fun read(bondId: String): ArchivePage? {
        val user = userId ?: return null
        val rows = database.io { archiveDayQueries.run(user, bondId).executeAsList() }
        if (rows.isEmpty()) return null
        return ArchivePage(
            days = rows.map { it.toArchiveDay() ?: return null },
            nextCursor = rows.last().page_cursor,
        )
    }

    override suspend fun day(bondId: String, date: String): ArchiveDay? {
        val user = userId ?: return null
        return database.io { archiveDayQueries.forDate(user, bondId, date).executeAsOneOrNull() }?.toArchiveDay()
    }

    override suspend fun putPage(bondId: String, page: ArchivePage, after: String?) {
        val user = userId ?: return
        database.io {
            transaction {
                // One run in the table: another person's, or another bond's, goes.
                archiveDayQueries.deleteOthers(user, bondId)
                val joined = if (after == null) replaceNewest(user, bondId, page) else replaceOlder(user, bondId, page, after)
                if (!joined) return@transaction
                page.days.forEach { archiveDayQueries.upsert(it.toRow(user, bondId, page.nextCursor)) }
                evict(user, bondId)
            }
        }
    }

    override suspend fun update(bondId: String, days: List<ArchiveDay>) {
        val user = userId ?: return
        database.io {
            transaction {
                days.forEach { day ->
                    val cached = archiveDayQueries.forDate(user, bondId, day.date).executeAsOneOrNull() ?: return@forEach
                    archiveDayQueries.upsert(day.toRow(user, bondId, cached.page_cursor))
                }
            }
        }
    }

    override suspend fun setMark(bondId: String, date: String, entryId: String, marked: Boolean) {
        val user = userId ?: return
        val flag = if (marked) 1L else 0L
        database.io {
            transaction {
                // Whichever of the day's entries has the id: mine or theirs.
                archiveDayQueries.setMyMark(flag, user, bondId, date, entryId)
                archiveDayQueries.setPartnerMark(flag, user, bondId, date, entryId)
            }
        }
    }

    override suspend fun remove(bondId: String, date: String) {
        val user = userId ?: return
        database.io { archiveDayQueries.deleteDay(user, bondId, date) }
    }

    /**
     * The newest page covers every date from its oldest day up (every date, when it is the
     * last page). The older cached days stay only when the page reaches down into the run:
     * if its oldest day is newer than the newest cached one, more than a page of days came
     * since, and the days between are known to neither, so the old run cannot be joined.
     */
    private fun MoyiDatabase.replaceNewest(user: String, bondId: String, page: ArchivePage): Boolean {
        val oldest = page.days.minOfOrNull { it.date }
        val newestCached = archiveDayQueries.newestDate(user, bondId).executeAsOne().newest
        if (page.nextCursor == null || oldest == null || newestCached == null || oldest > newestCached) {
            archiveDayQueries.deleteRun(user, bondId)
        } else {
            archiveDayQueries.deleteFrom(user, bondId, oldest)
        }
        return true
    }

    /**
     * A later page covers the dates before [after] (where the page before it ended) down to
     * its own oldest day (every older date, when it is the last page). It is kept only when
     * it meets the run: [after] is on or after the run's oldest day. Otherwise (nothing
     * cached, or the run was cut shorter since) keeping it would leave a gap.
     */
    private fun MoyiDatabase.replaceOlder(user: String, bondId: String, page: ArchivePage, after: String): Boolean {
        val runOldest = archiveDayQueries.oldestDate(user, bondId).executeAsOne().oldest ?: return false
        if (after < runOldest) return false
        val oldest = page.days.minOfOrNull { it.date }
        if (oldest == null || page.nextCursor == null) {
            archiveDayQueries.deleteBefore(user, bondId, after)
        } else {
            archiveDayQueries.deleteBetween(user, bondId, oldest, after)
        }
        if (page.days.isEmpty()) {
            // Nothing to insert, but the run now continues from this page's cursor (or ends).
            val tail = archiveDayQueries.tail(user, bondId).executeAsOneOrNull()
            if (tail != null) archiveDayQueries.moveTail(page.nextCursor, user, bondId, tail.page_cursor)
        }
        return true
    }

    /** Whole pages, oldest first, until the run is within the cap: a page's cursor stays true. */
    private fun MoyiDatabase.evict(user: String, bondId: String) {
        while (archiveDayQueries.count(user, bondId).executeAsOne() > ARCHIVE_CACHE_MAX_DAYS) {
            archiveDayQueries.deleteOldestPage(user, bondId)
        }
    }
}

internal fun ArchiveDay.toRow(userId: String, bondId: String, pageCursor: String?): Archive_day {
    val partner = partnerEntry
    val partnerId = when (partner) {
        is PartnerEntry.Submitted -> partner.id
        is PartnerEntry.Revealed -> partner.id
        is PartnerEntry.Deleted -> partner.id
        PartnerEntry.Locked, null -> null
    }
    val mine = myEntry
    return Archive_day(
        user_id = userId,
        bond_id = bondId,
        day_date = date,
        day_status = status.name,
        my_entry_id = mine?.id,
        my_entry_text = mine?.text,
        my_entry_status = mine?.status?.name,
        my_entry_marked = if (mine != null && mine.id in markedByMe) 1L else 0L,
        partner_entry_shape = when (partner) {
            is PartnerEntry.Submitted -> SHAPE_SUBMITTED
            is PartnerEntry.Revealed -> SHAPE_REVEALED
            is PartnerEntry.Deleted -> SHAPE_DELETED
            PartnerEntry.Locked -> SHAPE_LOCKED
            null -> null
        },
        partner_entry_id = partnerId,
        partner_entry_text = when (partner) {
            is PartnerEntry.Submitted -> partner.text
            is PartnerEntry.Revealed -> partner.text
            is PartnerEntry.Deleted, PartnerEntry.Locked, null -> null
        },
        partner_entry_marked = if (partnerId != null && partnerId in markedByMe) 1L else 0L,
        page_cursor = pageCursor,
    )
}

internal fun Archive_day.toArchiveDay(): ArchiveDay? {
    val myEntry = my_entry_id?.let { id ->
        MyEntry(id = id, text = my_entry_text, status = enumByName<EntryStatus>(my_entry_status) ?: return null)
    }
    val partnerEntry = when (partner_entry_shape) {
        null -> null
        SHAPE_SUBMITTED -> PartnerEntry.Submitted(partner_entry_id ?: return null, partner_entry_text ?: return null)
        SHAPE_REVEALED -> PartnerEntry.Revealed(partner_entry_id ?: return null, partner_entry_text ?: return null)
        SHAPE_DELETED -> PartnerEntry.Deleted(partner_entry_id ?: return null)
        SHAPE_LOCKED -> PartnerEntry.Locked
        else -> return null
    }
    return ArchiveDay(
        date = day_date,
        status = enumByName<BondDayStatus>(day_status) ?: return null,
        myEntry = myEntry,
        partnerEntry = partnerEntry,
        markedByMe = setOfNotNull(
            my_entry_id?.takeIf { my_entry_marked == 1L },
            partner_entry_id?.takeIf { partner_entry_marked == 1L },
        ),
    )
}

private inline fun <reified E : Enum<E>> enumByName(name: String?): E? = enumValues<E>().firstOrNull { it.name == name }

private const val SHAPE_SUBMITTED = "SUBMITTED"
private const val SHAPE_REVEALED = "REVEALED"
private const val SHAPE_DELETED = "DELETED"
private const val SHAPE_LOCKED = "LOCKED"
