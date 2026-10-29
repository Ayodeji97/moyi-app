package com.moyi.app.feature.onboarding.invite

import androidx.compose.runtime.Immutable

/** Onboarding 6 · invite handoff: SENT (342:48) and copied · CONFIRMED (394:1468). */
@Immutable
data class InviteHandoffState(
    val code: String,
    /** The invite link (`InviteResponse.link`), which the share sheet sends with the code. */
    val link: String,
    val copied: Boolean = false,
)

sealed interface InviteHandoffAction {
    data object OnCopyClick : InviteHandoffAction
    data object OnShareClick : InviteHandoffAction
    data object OnContinueClick : InviteHandoffAction
}

sealed interface InviteHandoffEvent {
    /** Put the code on the clipboard (the Root owns the platform clipboard). */
    data class CopyCode(val code: String) : InviteHandoffEvent

    /** Open the platform share sheet with the link and the code. */
    data class Share(val link: String, val code: String) : InviteHandoffEvent

    /** The end of the creator's onboarding: Today, partner not joined (188:481). */
    data object Finish : InviteHandoffEvent
}
