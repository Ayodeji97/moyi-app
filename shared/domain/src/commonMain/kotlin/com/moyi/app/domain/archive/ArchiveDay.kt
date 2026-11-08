package com.moyi.app.domain.archive

import com.moyi.app.domain.today.BondDayStatus
import com.moyi.app.domain.today.MyEntry
import com.moyi.app.domain.today.PartnerEntry

/**
 * One day of the archive, as `GET /bonds/{bondId}/days` lists it and
 * `GET /bonds/{bondId}/days/{date}` returns it, for the person asking.
 *
 * **Draw a day from its entries, not its [status]** (the contract says so on both
 * operations): a day whose only entry its author deleted is still listed, for that
 * author, with the tombstone as [myEntry] and `status: EMPTY`.
 *
 * [date] is the Bond-local ISO date, a string for the same reason as `Today.date`.
 * The generated `toString` is safe: [MyEntry] and [PartnerEntry] redact their words.
 */
data class ArchiveDay(
    val date: String,
    val status: BondDayStatus,
    /** Mine, in full or as its tombstone; null when I wrote nothing that day. */
    val myEntry: MyEntry?,
    /** The other member's, in whatever shape the server let me see. A locked one carries no words. */
    val partnerEntry: PartnerEntry?,
    /**
     * The ids of this day's entries that **I** have favourited (FR-093, states.md §6),
     * mine or theirs, from each entry's `favourited`, which the server only ever sets
     * for the caller's own bookmarks. A tombstone is never in it.
     *
     * **There is deliberately no way to say the partner favourited something**: a
     * favourite is private to whoever marked it, so nothing here can carry theirs.
     */
    val markedByMe: Set<String> = emptySet(),
)

/**
 * One page of the archive, newest first. **Only a null [nextCursor] means the end**:
 * a page can be shorter than the limit, and still not be the last.
 */
data class ArchivePage(
    val days: List<ArchiveDay>,
    /** Opaque: passed back unchanged for the page after this one. */
    val nextCursor: String?,
)

/**
 * [run] (newest first, with no gaps) with [page] joined in, **the server winning**: the
 * page replaces every day of [run] in the dates it covers. Null when it cannot be joined
 * without a gap.
 *
 * Paging is by keyset: a cursor means "the days strictly before the day the page before
 * ended on" (the contract's `nextCursor`, kept opaque here; only [after], that day, is
 * known). So a page covers the dates **before [after]** (every date, for the newest page:
 * [after] null) **down to its own oldest day**, or every older date when it is the last
 * page (a null cursor). A day of [run] in that span that the page does not list is gone
 * on the server: deleted, erased, or no longer listed for this person.
 *
 * The newest page keeps the older days of [run] only when it reaches down into them: when
 * its oldest day is newer than every day of [run], more than a page of days came since,
 * and the days between are known to neither, so [run] is dropped. A later page joins only
 * when [after] is on or after [run]'s oldest day.
 */
fun joinPage(run: List<ArchiveDay>, page: ArchivePage, after: String?): List<ArchiveDay>? {
    val oldest = page.days.minOfOrNull { it.date }
    val last = page.nextCursor == null
    val kept = if (after == null) {
        val newestKept = run.maxOfOrNull { it.date }
        when {
            last || oldest == null || newestKept == null || oldest > newestKept -> emptyList()
            else -> run.filter { it.date < oldest }
        }
    } else {
        val runOldest = run.minOfOrNull { it.date } ?: return null
        if (after < runOldest) return null
        run.filter { day ->
            when {
                day.date >= after -> true
                last || oldest == null -> false
                else -> day.date < oldest
            }
        }
    }
    return (page.days + kept).distinctBy { it.date }.sortedByDescending { it.date }
}
