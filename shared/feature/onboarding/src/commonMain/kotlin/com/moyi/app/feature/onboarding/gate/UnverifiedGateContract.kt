package com.moyi.app.feature.onboarding.gate

import androidx.compose.runtime.Immutable
import com.moyi.app.designsystem.presentation.UiText

/** Account 5 · unverified gate, BLOCKED (Figma 345:46; states.md §1b). */
@Immutable
data class UnverifiedGateState(
    val email: String,
    val resend: GateResendStatus = GateResendStatus.Ready,
    /** Why the last "Send it again" failed. */
    val resendFailure: UiText? = null,
)

/** The one resend affordance, and its Resent sub-state (states.md §1). */
enum class GateResendStatus { Ready, Sending, Sent }

sealed interface UnverifiedGateAction {
    data object OnResendClick : UnverifiedGateAction

    /** The app came back to the foreground: the link may have been opened meanwhile. */
    data object OnResume : UnverifiedGateAction
}

sealed interface UnverifiedGateEvent {
    /** Verified now: on to relationship type. */
    data object NavigateToOnboarding : UnverifiedGateEvent
}
