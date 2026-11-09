package com.moyi.app.data.repository

import com.moyi.app.api.models.DayResponse
import com.moyi.app.api.models.DaysResponse
import com.moyi.app.api.models.EntryResponse
import com.moyi.app.api.models.PartnerEntryResponse
import com.moyi.app.api.models.RevealedPartnerEntryResponse
import com.moyi.app.api.models.SubmittedPartnerEntryResponse
import com.moyi.app.domain.archive.ArchiveDay
import com.moyi.app.domain.archive.ArchivePage
import com.moyi.app.domain.today.BondDayStatus

// DayResponse and DaysResponse declare no `required` fields (row 13's pattern), so
// everything arrives nullable. A day without its date or status is a body that did
// not parse; so is a page whose items are missing, or any one malformed day in it:
// a partial page would let the next cursor skip the day that failed.
// The entries reuse Today's mappers, so the partner's words are read exactly as
// Today reads them (a locked entry carries none).

internal fun DaysResponse.toArchivePage(): ArchivePage? {
    val days = items ?: return null
    return ArchivePage(
        days = days.map { it.toArchiveDay() ?: return null },
        nextCursor = nextCursor,
    )
}

internal fun DayResponse.toArchiveDay(): ArchiveDay? = ArchiveDay(
    date = date?.toString() ?: return null,
    status = status?.let { BondDayStatus.valueOf(it.name) } ?: return null,
    myEntry = myEntry?.let { it.toMyEntry() ?: return null },
    partnerEntry = partnerEntry?.let { it.toPartnerEntry() ?: return null },
    markedByMe = setOfNotNull(myEntry?.markedId(), partnerEntry?.markedId()),
)

/**
 * The entry's id when the caller has favourited it. `favourited` is the caller's own
 * bookmark only (the PUT: "the other member is never shown it, in any response"); a
 * tombstone is left out, as `favourites=true` leaves it out ("and can still read").
 */
private fun EntryResponse.markedId(): String? = id.takeIf { favourited && status != EntryResponse.Status.DELETED }

/** The same for the other member's entry: a locked one has no id to mark, a tombstone is left out. */
private fun PartnerEntryResponse.markedId(): String? = when (this) {
    is SubmittedPartnerEntryResponse -> id.takeIf { favourited }
    is RevealedPartnerEntryResponse -> id.takeIf { favourited }
    else -> null
}
