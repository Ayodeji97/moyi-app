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

    /** "Type the code" (PROPOSED, docs/missing-components.md §63): the verify screen, where the emailed code is typed. */
    data object OnTypeCodeClick : UnverifiedGateAction

    /** The app came back to the foreground: the link may have been opened meanwhile. */
    data object OnResume : UnverifiedGateAction

    /** The account target, top right (PROPOSED, docs/missing-components.md §57): Account, from the gate. */
    data object OnAccountClick : UnverifiedGateAction
}

sealed interface UnverifiedGateEvent {
    /** Verified now: on to relationship type. */
    data object NavigateToOnboarding : UnverifiedGateEvent

    /** The verify screen for [email], signed in: the 6-digit code from the email is typed there. */
    data class NavigateToVerify(val email: String) : UnverifiedGateEvent

    /** Account: export or delete, which needs no bond and no verified address. */
    data object NavigateToAccount : UnverifiedGateEvent
}
