package com.moyi.app.data.repository

import com.moyi.app.api.models.BondDayResponse
import com.moyi.app.api.models.DeletedPartnerEntryResponse
import com.moyi.app.api.models.EntryImageResponse
import com.moyi.app.api.models.EntryResponse
import com.moyi.app.api.models.EntryVoiceResponse
import com.moyi.app.api.models.LockedEntryResponse
import com.moyi.app.api.models.PartnerEntryResponse
import com.moyi.app.api.models.ReactionRequest
import com.moyi.app.api.models.RevealedPartnerEntryResponse
import com.moyi.app.api.models.SubmittedPartnerEntryResponse
import com.moyi.app.api.models.TodayPromptResponse
import com.moyi.app.api.models.TodayResponse
import com.moyi.app.domain.today.BondDayStatus
import com.moyi.app.domain.today.EntryImage
import com.moyi.app.domain.today.EntryMediaStatus
import com.moyi.app.domain.today.EntryStatus
import com.moyi.app.domain.today.EntryVoice
import com.moyi.app.domain.today.MyEntry
import com.moyi.app.domain.today.PartnerEntry
import com.moyi.app.domain.today.Prompt
import com.moyi.app.domain.today.Reaction
import com.moyi.app.domain.today.Today

// TodayResponse, BondDayResponse and the entries declare their fields `required`
// (PR #52 synced the contract that says so), so `bondDay`, its date and its status
// arrive non-null or the body fails to decode. What is still checked here is that
// an entry read in full carries its words (`text` is nullable: a tombstone has none).

internal fun TodayResponse.toToday(): Today? {
    val day = bondDay
    return Today(
        date = day.date.toString(),
        status = day.status.toDomain(),
        myEntry = myEntry?.let { it.toMyEntry() ?: return null },
        partnerEntry = partnerEntry?.let { it.toPartnerEntry() ?: return null },
        // A prompt is an offer, never a requirement: none is no prompt, not a broken day.
        prompt = prompt?.toPrompt(),
    )
}

/** Null when an entry read in full arrives without its words: only a tombstone has none. */
internal fun EntryResponse.toMyEntry(): MyEntry? {
    val status = status.toDomain()
    if (status != EntryStatus.DELETED && text == null) return null
    return MyEntry(
        id = id,
        text = if (status == EntryStatus.DELETED) null else text,
        status = status,
        image = image?.toEntryImage().takeIf { status != EntryStatus.DELETED },
        voice = voice?.toEntryVoice().takeIf { status != EntryStatus.DELETED },
        // The contract: always null on an erased entry. Dropped there all the same.
        reaction = reaction?.toDomain().takeIf { status != EntryStatus.DELETED },
    )
}

/**
 * The photo as the server shows it to the caller. A ready photo without its URL cannot
 * be drawn, so it is read as still on its way, never as a broken image; the contract
 * says a ready one always has it.
 */
internal fun EntryImageResponse.toEntryImage(): EntryImage {
    val shown = when (status) {
        EntryImageResponse.Status.READY -> if (url == null) EntryMediaStatus.Processing else EntryMediaStatus.Ready
        EntryImageResponse.Status.PROCESSING -> EntryMediaStatus.Processing
        EntryImageResponse.Status.NOT_SHARED -> EntryMediaStatus.NotShared
    }
    return EntryImage(
        id = id,
        status = shown,
        url = url.takeIf { shown != EntryMediaStatus.Processing },
        width = width?.takeIf { it > 0 },
        height = height?.takeIf { it > 0 },
        // The contract: false unless READY.
        sensitive = sensitive && shown == EntryMediaStatus.Ready,
    )
}

/**
 * The voice note as the server plays it to the caller, by the photo's rule: a ready one
 * without its URL cannot be played, so it is read as still on its way. A length that is
 * missing or not positive is left out; the player then learns it from the file.
 */
internal fun EntryVoiceResponse.toEntryVoice(): EntryVoice {
    val shown = when (status) {
        EntryVoiceResponse.Status.READY -> if (url == null) EntryMediaStatus.Processing else EntryMediaStatus.Ready
        EntryVoiceResponse.Status.PROCESSING -> EntryMediaStatus.Processing
        EntryVoiceResponse.Status.NOT_SHARED -> EntryMediaStatus.NotShared
    }
    return EntryVoice(
        id = id,
        status = shown,
        url = url.takeIf { shown != EntryMediaStatus.Processing },
        durationMs = durationMs?.takeIf { it > 0 && shown != EntryMediaStatus.Processing },
        sensitive = sensitive && shown == EntryMediaStatus.Ready,
        // The AAC copy of the same note: held back with the Ogg one, and null for an old note.
        urlAac = urlAac?.takeIf { shown != EntryMediaStatus.Processing },
    )
}

/**
 * The other member's entry. `status` picks the generated subclass while decoding
 * (`@JsonClassDiscriminator("status")`), so the shape is already known here; an entry
 * read in full without its words is the one shape that still does not parse. Only a
 * revealed entry carries a reaction.
 */
internal fun PartnerEntryResponse.toPartnerEntry(): PartnerEntry? = when (this) {
    is LockedEntryResponse -> PartnerEntry.Locked
    is SubmittedPartnerEntryResponse -> text?.let { PartnerEntry.Submitted(id, it, image?.toEntryImage(), voice?.toEntryVoice()) }
    is RevealedPartnerEntryResponse -> text?.let {
        PartnerEntry.Revealed(id, it, image?.toEntryImage(), voice?.toEntryVoice(), reaction?.toDomain())
    }
    is DeletedPartnerEntryResponse -> PartnerEntry.Deleted(id)
}

private fun TodayPromptResponse.toPrompt(): Prompt = Prompt(id = id, text = text)

private fun BondDayResponse.Status.toDomain(): BondDayStatus = BondDayStatus.valueOf(name)

private fun EntryResponse.Status.toDomain(): EntryStatus = EntryStatus.valueOf(name)

/** By name: the contract's enum and the domain's are the same five (a test pins it). */
internal fun EntryResponse.Reaction.toDomain(): Reaction = Reaction.valueOf(name)

/** The revealed partner entry's own copy of the same enum, by name likewise. */
internal fun RevealedPartnerEntryResponse.Reaction.toDomain(): Reaction = Reaction.valueOf(name)

/** The request's own enum, by name, for the same reason. */
internal fun Reaction.toRequest(): ReactionRequest = ReactionRequest(ReactionRequest.Type.valueOf(name))
