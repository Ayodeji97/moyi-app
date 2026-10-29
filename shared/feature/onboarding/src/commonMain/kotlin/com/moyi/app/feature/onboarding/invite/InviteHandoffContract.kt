package com.moyi.app.feature.onboarding.invite

import androidx.compose.runtime.Immutable

/** Onboarding 6 · invite handoff: SENT (342:48) and copied · CONFIRMED (394:1468). */
@Immutable
data class InviteHandoffState(
    val code: String,
    val copied: Boolean = false,
)

sealed interface InviteHandoffAction {
    data object OnCopyClick : InviteHandoffAction
    data object OnShareClick : InviteHandoffAction
    data object OnContinueClick : InviteHandoffAction
}

sealed interface InviteHandoffEvent {
    /** The end of the creator's onboarding: Today, partner not joined (188:481). */
    data object Finish : InviteHandoffEvent
}
