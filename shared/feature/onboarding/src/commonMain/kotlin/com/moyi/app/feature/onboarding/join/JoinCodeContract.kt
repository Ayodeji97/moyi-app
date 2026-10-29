package com.moyi.app.feature.onboarding.join

import androidx.compose.runtime.Immutable
import com.moyi.app.designsystem.presentation.UiText

/**
 * Join · code from link, PREFILLED (308:4), and manual entry, KEYBOARD UP
 * (309:42). One screen, two drawn layouts: [prefilled] picks the copy and
 * whether it is a root (from a link, nothing behind it).
 */
@Immutable
data class JoinCodeState(
    val prefilled: Boolean,
    val code: String = "",
    val error: UiText? = null,
)

sealed interface JoinCodeAction {
    data class OnCodeChange(val value: String) : JoinCodeAction
    data object OnContinueClick : JoinCodeAction
    data object OnBackClick : JoinCodeAction
}

sealed interface JoinCodeEvent {
    data class NavigateToConfirm(val code: String) : JoinCodeEvent

    /** Expired, revoked, full or never existed: one screen for all four (FR-024). */
    data object NavigateToCannotBeUsed : JoinCodeEvent
    data object NavigateBack : JoinCodeEvent
}
