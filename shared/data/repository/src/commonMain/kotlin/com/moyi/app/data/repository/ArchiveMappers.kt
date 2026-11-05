package com.moyi.app.data.repository

import com.moyi.app.api.models.DayResponse
import com.moyi.app.api.models.DaysResponse
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
)
