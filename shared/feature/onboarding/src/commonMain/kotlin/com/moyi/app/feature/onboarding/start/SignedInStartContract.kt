package com.moyi.app.feature.onboarding.start

/**
 * Where a signed-in person starts. Not a drawn screen: while it decides it is
 * the blank `surface` of launch (states.md §0; no spinner), and if it cannot
 * decide it is the Generic error (Figma 384:341, states.md §10).
 */
enum class SignedInStartState {
    Checking,

    /** `GET /me` or `GET /bonds` failed: the Generic error with "Try again". */
    Failed,
}

sealed interface SignedInStartAction {
    data object OnRetryClick : SignedInStartAction

    /** Get help, under Try again on the Generic error. */
    data object OnGetHelpClick : SignedInStartAction
}

sealed interface SignedInStartEvent {
    /** In an open bond, waiting for the partner or not, or a day cached from one: Today (states.md §3). */
    data object NavigateToToday : SignedInStartEvent

    /**
     * In no open bond, and the newest bond has ended: Bond · archived (states.md §9), whoever
     * ended it and however. [partnerName] is the other member's, or null when unknown.
     */
    data class NavigateToArchived(val partnerName: String?) : SignedInStartEvent

    /** Verified, in no open bond: relationship type, the start of onboarding. */
    data object NavigateToOnboarding : SignedInStartEvent

    /** Not verified, in no open bond: the gate (FR-002). */
    data object NavigateToGate : SignedInStartEvent

    /** Support, pushed over the Generic error; its way back returns here. */
    data object NavigateToSupport : SignedInStartEvent
}
