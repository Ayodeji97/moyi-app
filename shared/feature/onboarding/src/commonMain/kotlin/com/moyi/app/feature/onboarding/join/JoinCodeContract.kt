package com.moyi.app.feature.onboarding.join

import androidx.compose.runtime.Immutable
import com.moyi.app.designsystem.presentation.UiText
import com.moyi.app.domain.bond.InvitePreview

/**
 * Join · code from link, PREFILLED (308:4), and manual entry, KEYBOARD UP
 * (309:42). One screen, two drawn layouts: [prefilled] picks the copy and
 * whether it is a root (from a link, nothing behind it).
 */
@Immutable
data class JoinCodeState(
    val prefilled: Boolean,
    val code: String = "",
    /** Under the field: the code's shape (states.md §2), or a `422` from the server. */
    val error: UiText? = null,
    /** `GET /invites/{code}` in flight: PrimaryAction Sending, "Checking". */
    val checking: Boolean = false,
    /** Above the action, the code kept: offline, rate limited, or anything unexplained. */
    val failure: UiText? = null,
)

sealed interface JoinCodeAction {
    data class OnCodeChange(val value: String) : JoinCodeAction
    data object OnContinueClick : JoinCodeAction
    data object OnBackClick : JoinCodeAction
}

sealed interface JoinCodeEvent {
    /** Resolved: who invited the person and to what, so confirm need not ask again. */
    data class NavigateToConfirm(val code: String, val preview: InvitePreview) : JoinCodeEvent

    /** Expired, revoked, full or never existed: one screen for all four (FR-024). */
    data object NavigateToCannotBeUsed : JoinCodeEvent
    data object NavigateBack : JoinCodeEvent
}
