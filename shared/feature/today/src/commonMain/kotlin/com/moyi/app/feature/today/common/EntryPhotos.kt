package com.moyi.app.feature.today.common

import com.moyi.app.designsystem.component.EntryPhoto
import com.moyi.app.designsystem.component.EntryPhotoLook
import com.moyi.app.domain.today.EntryImage
import com.moyi.app.domain.today.EntryImageStatus
import com.moyi.app.domain.today.PartnerEntry

/** How the card draws [this] photo for the reader the server answered. */
internal fun EntryImage.toEntryPhoto(): EntryPhoto = EntryPhoto(
    key = id,
    url = url,
    width = width,
    height = height,
    look = when (status) {
        EntryImageStatus.Ready -> if (sensitive) EntryPhotoLook.Covered else EntryPhotoLook.Shown
        EntryImageStatus.Processing -> EntryPhotoLook.OnItsWay
        EntryImageStatus.NotShared -> EntryPhotoLook.NotShared
    },
)

/** The partner's photo, from exactly the shapes that carry words: a locked entry has none to give (BR-8). */
internal fun PartnerEntry?.image(): EntryImage? = when (this) {
    is PartnerEntry.Submitted -> image
    is PartnerEntry.Revealed -> image
    is PartnerEntry.Deleted, PartnerEntry.Locked, null -> null
}
