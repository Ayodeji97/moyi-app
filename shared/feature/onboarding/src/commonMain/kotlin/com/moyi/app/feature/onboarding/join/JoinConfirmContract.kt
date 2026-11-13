package com.moyi.app.feature.onboarding.join

import androidx.compose.runtime.Immutable
import com.moyi.app.designsystem.presentation.UiText
import com.moyi.app.feature.onboarding.common.RelationshipType

/**
 * The person's own bond that nobody has joined, which joining will end (owner ruling
 * 2026-11-13). Set when the join flow was opened from it: Today, Bond settings, or "Your bond
 * is ready". [justMade]: opened from "Your bond is ready", the moment after creating it, so
 * nothing can have been written there yet.
 */
@Immutable
data class LeavingBond(val id: String, val name: String, val justMade: Boolean = false)

/** Join · confirm, RESOLVED (310:14): who invited you, to which bond, before the invite is spent. */
@Immutable
data class JoinConfirmState(
    val inviterName: String,
    val bondName: String,
    val type: RelationshipType,
    /** The empty bond Join also ends, said on the screen before Join is pressed; null when there is none. */
    val leaving: LeavingBond? = null,
    val joining: Boolean = false,
    /** Above the action: offline, rate limited, the bond limit, the person's own code, or anything unexplained. */
    val failure: UiText? = null,
)

sealed interface JoinConfirmAction {
    data object OnJoinClick : JoinConfirmAction
    data object OnNotNowClick : JoinConfirmAction
    data object OnBackClick : JoinConfirmAction
}

sealed interface JoinConfirmEvent {
    /** Joined: both land on Day 1 (Today). */
    data object Finish : JoinConfirmEvent

    /** "Not now" and back both return to the code, unspent. */
    data object NavigateBack : JoinConfirmEvent

    /** `404 INVITE_NOT_USABLE` (or a `422`): the code stopped working since it resolved. */
    data object NavigateToCannotBeUsed : JoinConfirmEvent

    /** `403 EMAIL_NOT_VERIFIED`: the account cannot join yet (FR-002). */
    data object NavigateToGate : JoinConfirmEvent
}
