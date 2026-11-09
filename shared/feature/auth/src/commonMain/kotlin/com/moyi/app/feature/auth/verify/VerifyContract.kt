package com.moyi.app.feature.auth.verify

import androidx.compose.runtime.Immutable
import com.moyi.app.designsystem.presentation.UiText
import com.moyi.app.feature.auth.common.FieldInput
import com.moyi.app.feature.auth.common.SubmitStatus

/**
 * Verify email (Onboarding 3, Figma 341:17), plus the code field (not in Figma,
 * docs/missing-components.md §58): six digits typed from the email, or the whole link pasted.
 */
@Immutable
data class VerifyState(
    val email: String,
    /** Only ever zero to six ASCII digits; a pasted link is sent, never shown (parseCodeInput). */
    val code: FieldInput = FieldInput(),
    val mode: VerifyMode = VerifyMode.Waiting,
    val resend: ResendStatus = ResendStatus.Ready,
    /**
     * The primary action: "Check again" while waiting, "Send a new link" when expired. Sending
     * while a code or link is checked, including the check the sixth digit starts by itself.
     */
    val submit: SubmitStatus = SubmitStatus.Idle,
    /** Why the last "Send it again" failed, shown above the action; never part of [submit]. */
    val resendFailure: UiText? = null,
) {
    /**
     * False when the screen was opened by the emailed link on a phone that does not know the
     * address (a cold start, signed out). Nothing can be resent without it, so the screen
     * drops "Send it again" and "Use a different address", and an expired link sends the
     * person to sign in, where the unverified gate can send a new one.
     */
    val addressKnown: Boolean get() = email.isNotEmpty()
}

/** The two drawn layouts: waiting for the link (341:17) and the link expired (362:107). */
enum class VerifyMode { Waiting, Expired }

/** The "Send it again" affordance (states.md §1, Resent sub-state 362:95). */
enum class ResendStatus {
    Ready,
    Sending,

    /** Replaced by the muted acknowledgement for a minute, so a second tap is not a silent no-op. */
    Sent,
}

sealed interface VerifyAction {
    /** A keystroke or a paste in the code field: digits, or a whole link. */
    data class OnCodeChange(val value: String) : VerifyAction

    /** "Check again" (or the keyboard's Done) while waiting: submits the code. */
    data object OnSubmit : VerifyAction

    /** "Send it again" while waiting, or "Send a new link" when expired. */
    data object OnResendClick : VerifyAction
    data object OnChangeEmailClick : VerifyAction

    /** "Sign in" on the expired layout when the address is not known ([VerifyState.addressKnown]). */
    data object OnSignInClick : VerifyAction
    data object OnBackClick : VerifyAction
}

sealed interface VerifyEvent {
    /** 200: verified. Sign in next, with the address filled in. */
    data class NavigateToLogin(val email: String) : VerifyEvent

    /** An expired link with no address to resend to: sign in, and the gate sends a new one. */
    data object NavigateToSignIn : VerifyEvent

    /** Back to sign up, whose fields are still there. */
    data object NavigateBack : VerifyEvent
}
