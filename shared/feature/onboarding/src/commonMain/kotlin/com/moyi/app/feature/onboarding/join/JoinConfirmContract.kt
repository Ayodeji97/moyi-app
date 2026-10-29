package com.moyi.app.feature.onboarding.join

import androidx.compose.runtime.Immutable
import com.moyi.app.designsystem.presentation.UiText
import com.moyi.app.feature.onboarding.common.RelationshipType

/** Join · confirm, RESOLVED (310:14): who invited you, to which bond, before the invite is spent. */
@Immutable
data class JoinConfirmState(
    val inviterName: String,
    val bondName: String,
    val type: RelationshipType,
    val joining: Boolean = false,
    /** Above the action: offline, rate limited, the bond limit, or anything unexplained. */
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
