package com.moyi.app.feature.today.common

import com.moyi.app.designsystem.component.EntryPhoto
import com.moyi.app.designsystem.component.EntryPhotoLook
import com.moyi.app.designsystem.component.VoiceNote
import com.moyi.app.designsystem.component.VoiceNoteLook
import com.moyi.app.domain.today.EntryImage
import com.moyi.app.domain.today.EntryMediaStatus
import com.moyi.app.domain.today.EntryVoice
import com.moyi.app.domain.today.PartnerEntry

/** How the card draws [this] photo for the reader the server answered. */
internal fun EntryImage.toEntryPhoto(): EntryPhoto = EntryPhoto(
    key = id,
    url = url,
    width = width,
    height = height,
    look = when (status) {
        EntryMediaStatus.Ready -> if (sensitive) EntryPhotoLook.Covered else EntryPhotoLook.Shown
        EntryMediaStatus.Processing -> EntryPhotoLook.OnItsWay
        EntryMediaStatus.NotShared -> EntryPhotoLook.NotShared
    },
)

/** The partner's photo, from exactly the shapes that carry words: a locked entry has none to give (BR-8). */
internal fun PartnerEntry?.image(): EntryImage? = when (this) {
    is PartnerEntry.Submitted -> image
    is PartnerEntry.Revealed -> image
    is PartnerEntry.Deleted, PartnerEntry.Locked, null -> null
}

/** How the card offers [this] voice note to the reader the server answered. */
internal fun EntryVoice.toVoiceNote(): VoiceNote = VoiceNote(
    key = id,
    url = url,
    durationMs = durationMs?.toLong(),
    look = when (status) {
        EntryMediaStatus.Ready -> if (sensitive) VoiceNoteLook.Sensitive else VoiceNoteLook.Playable
        EntryMediaStatus.Processing -> VoiceNoteLook.OnItsWay
        EntryMediaStatus.NotShared -> VoiceNoteLook.NotShared
    },
    urlAac = urlAac,
)

/** The partner's voice note, by [image]'s rule: only from the shapes that carry words (BR-8). */
internal fun PartnerEntry?.voice(): EntryVoice? = when (this) {
    is PartnerEntry.Submitted -> voice
    is PartnerEntry.Revealed -> voice
    is PartnerEntry.Deleted, PartnerEntry.Locked, null -> null
}
