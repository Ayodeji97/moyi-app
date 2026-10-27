package com.moyi.app.feature.auth.verify

import androidx.compose.runtime.Immutable
import com.moyi.app.feature.auth.common.FieldInput
import com.moyi.app.feature.auth.common.SubmitStatus

/** Verify email (Onboarding 3, Figma 341:17), plus the paste-token field (not in Figma). */
@Immutable
data class VerifyState(
    val email: String,
    val token: FieldInput = FieldInput(),
    val mode: VerifyMode = VerifyMode.Waiting,
    val resend: ResendStatus = ResendStatus.Ready,
    val submit: SubmitStatus = SubmitStatus.Idle,
)

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
    data class OnTokenChange(val value: String) : VerifyAction

    /** "Check again" while waiting: submits the pasted token. */
    data object OnSubmit : VerifyAction

    /** "Send it again" while waiting, or "Send a new link" when expired. */
    data object OnResendClick : VerifyAction
    data object OnChangeEmailClick : VerifyAction
    data object OnBackClick : VerifyAction
}

sealed interface VerifyEvent {
    /** 200: verified. Sign in next, with the address filled in. */
    data class NavigateToLogin(val email: String) : VerifyEvent

    /** Back to sign up, whose fields are still there. */
    data object NavigateBack : VerifyEvent
}
