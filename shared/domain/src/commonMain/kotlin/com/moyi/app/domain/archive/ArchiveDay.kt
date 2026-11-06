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
