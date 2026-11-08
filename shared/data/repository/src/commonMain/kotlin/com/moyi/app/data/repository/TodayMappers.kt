package com.moyi.app.data.repository

import com.moyi.app.api.models.BondDayResponse
import com.moyi.app.api.models.EntryResponse
import com.moyi.app.api.models.TodayPromptResponse
import com.moyi.app.api.models.TodayResponse
import com.moyi.app.api.wire.PartnerEntryWire
import com.moyi.app.domain.today.BondDayStatus
import com.moyi.app.domain.today.EntryStatus
import com.moyi.app.domain.today.MyEntry
import com.moyi.app.domain.today.PartnerEntry
import com.moyi.app.domain.today.Prompt
import com.moyi.app.domain.today.Today

// TodayResponse and BondDayResponse declare no `required` fields either
// (docs/backend-contract-gaps.md, row 13's pattern), so `bondDay`, its date and
// its status arrive nullable. Today routes on all three: missing, the body is
// treated as one that did not parse (null here, Serialization in the caller).
// The entries' own fields ARE required in the contract, so they arrive non-null;
// what is checked there is that an entry read in full carries its words.

internal fun TodayResponse.toToday(): Today? {
    val day = bondDay ?: return null
    return Today(
        date = day.date?.toString() ?: return null,
        status = day.status?.toDomain() ?: return null,
        myEntry = myEntry?.let { it.toMyEntry() ?: return null },
        partnerEntry = partnerEntry?.let { it.toPartnerEntry() ?: return null },
        // A prompt is an offer, never a requirement: half of one is no prompt, not a broken day.
        prompt = prompt?.toPrompt(),
    )
}

/** Null when an entry read in full arrives without its words: only a tombstone has none. */
internal fun EntryResponse.toMyEntry(): MyEntry? {
    val status = status.toDomain()
    if (status != EntryStatus.DELETED && text == null) return null
    return MyEntry(id = id, text = if (status == EntryStatus.DELETED) null else text, status = status)
}

internal fun PartnerEntryWire.toPartnerEntry(): PartnerEntry? = when (this) {
    is PartnerEntryWire.Locked -> PartnerEntry.Locked
    is PartnerEntryWire.Entry -> when (entry.status) {
        EntryResponse.Status.SUBMITTED -> entry.text?.let { PartnerEntry.Submitted(entry.id, it) }
        EntryResponse.Status.REVEALED -> entry.text?.let { PartnerEntry.Revealed(entry.id, it) }
        EntryResponse.Status.DELETED -> PartnerEntry.Deleted(entry.id)
    }
}

private fun TodayPromptResponse.toPrompt(): Prompt? = Prompt(id = id ?: return null, text = text ?: return null)

private fun BondDayResponse.Status.toDomain(): BondDayStatus = BondDayStatus.valueOf(name)

private fun EntryResponse.Status.toDomain(): EntryStatus = EntryStatus.valueOf(name)
