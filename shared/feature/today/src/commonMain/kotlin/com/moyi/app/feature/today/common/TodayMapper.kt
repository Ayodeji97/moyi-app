package com.moyi.app.feature.today.common

import com.moyi.app.domain.bond.Bond
import com.moyi.app.domain.bond.BondStatus
import com.moyi.app.domain.today.BondDayStatus
import com.moyi.app.domain.today.MyEntry
import com.moyi.app.domain.today.PartnerEntry
import com.moyi.app.domain.today.Today
import com.moyi.app.domain.today.reactable
import kotlinx.datetime.LocalDate

/**
 * The layout for [day], from the server's status and **my** entry. The partner's
 * entry is read only on a SOLO day, where it decides whose lone entry is shown; on
 * every other day the partner's writing must not change the layout (states.md §3:
 * "the partner wrote, I haven't" is exactly [TodayStatus.NotWritten]).
 */
internal fun todayStatus(
    day: BondDayStatus,
    myEntry: MyEntry?,
    partnerEntry: PartnerEntry?,
    waitingForPartnerToJoin: Boolean,
): TodayStatus {
    val mineLive = myEntry?.isLive == true
    return when (day) {
        BondDayStatus.OPEN, BondDayStatus.PARTIAL, BondDayStatus.PENDING_REVEAL, BondDayStatus.SUSPENDED -> when {
            !mineLive -> TodayStatus.NotWritten
            waitingForPartnerToJoin -> TodayStatus.PartnerNotJoined
            else -> TodayStatus.Waiting
        }
        BondDayStatus.REVEALED -> when {
            mineLive -> TodayStatus.Revealed
            myEntry != null -> TodayStatus.Tombstone
            // Revealed with nothing of mine cannot happen; the layout that shows the least stands in.
            else -> TodayStatus.NotWritten
        }
        BondDayStatus.SOLO -> when {
            mineLive -> TodayStatus.SoloYouWrote
            partnerEntry.words() != null -> TodayStatus.SoloTheyWrote
            else -> TodayStatus.NotWritten
        }
        // No frame for either (owed). A closed day with my lone entry is factually solo;
        // with none, the not-written layout shows nothing about anyone.
        BondDayStatus.EMPTY, BondDayStatus.FROZEN -> if (mineLive) TodayStatus.SoloYouWrote else TodayStatus.NotWritten
    }
}

/**
 * [today] in [bond], ready for the screens. Null when it cannot be shown honestly:
 * a date that is not ISO, or a layout that names the partner in a bond whose
 * member list names nobody. The caller treats that as a day that did not load,
 * never as a guess.
 */
internal fun toDayView(bond: Bond, today: Today): DayView? {
    val date = runCatching { LocalDate.parse(today.date) }.getOrNull() ?: return null
    val status = todayStatus(
        day = today.status,
        myEntry = today.myEntry,
        partnerEntry = today.partnerEntry,
        waitingForPartnerToJoin = bond.status == BondStatus.PENDING_MEMBER,
    )
    if (status in NamesThePartner && bond.partnerName == null) return null
    // Mine to react to only where their words are shown and the entry is revealed (ADR-0037),
    // in a bond that still takes writes: the cooling-off refuses a reaction (`409 BOND_ARCHIVED`).
    val reactable = today.partnerEntry.reactable()?.takeIf { status in PartnerVisible }
    return DayView(
        bondId = bond.id,
        bondStatus = bond.status,
        date = date,
        status = status,
        partnerName = bond.partnerName,
        prompt = today.prompt,
        myEntryId = today.myEntry?.id,
        myEntry = today.myEntry?.takeIf { it.isLive }?.text,
        partnerEntry = today.partnerEntry.words()?.takeIf { status in PartnerVisible },
        myPhoto = today.myEntry?.takeIf { it.isLive }?.image?.toEntryPhoto(),
        partnerPhoto = today.partnerEntry.image()?.takeIf { status in PartnerVisible }?.toEntryPhoto(),
        myVoice = today.myEntry?.takeIf { it.isLive }?.voice?.toVoiceNote(),
        partnerVoice = today.partnerEntry.voice()?.takeIf { status in PartnerVisible }?.toVoiceNote(),
        canReact = reactable != null && bond.status.takesWrites,
        myReaction = reactable?.reaction,
        partnerReaction = today.myEntry?.takeIf { it.isLive }?.reaction,
    )
}

/** The only layouts that may hold the partner's words: the day is open, or it closed SOLO with theirs. */
internal val PartnerVisible: Set<TodayStatus> = setOf(TodayStatus.Revealed, TodayStatus.Tombstone, TodayStatus.SoloTheyWrote)

/** Layouts whose copy names the partner ("You'll see Tunde's…", "From Tunde", the delete line). */
private val NamesThePartner = setOf(TodayStatus.Waiting, TodayStatus.Revealed, TodayStatus.Tombstone, TodayStatus.SoloTheyWrote)

/** The partner's words, when the shape carries any: a locked, removed or deleted entry has none. */
internal fun PartnerEntry?.words(): String? = when (this) {
    is PartnerEntry.Submitted -> text
    is PartnerEntry.Revealed -> text
    is PartnerEntry.Deleted, PartnerEntry.Locked, null -> null
}
