package com.moyi.app.feature.onboarding.join

import androidx.compose.runtime.Immutable
import com.moyi.app.feature.onboarding.common.RelationshipType

/** Join · confirm, RESOLVED (310:14): who invited you, to which bond, before the invite is spent. */
@Immutable
data class JoinConfirmState(
    val inviterName: String,
    val bondName: String,
    val type: RelationshipType,
    val joining: Boolean = false,
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
}
