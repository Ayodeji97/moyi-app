package com.moyi.app.feature.support

import androidx.compose.runtime.Immutable
import com.moyi.app.designsystem.presentation.UiText

/** Where Get help was opened from: it decides only the words of the way back. */
enum class SupportOrigin {
    /** Bond settings, Just you. "Back to settings" (support.back). */
    Settings,

    /** A Generic error. "Go back" (PROPOSED): the screen behind is not settings. */
    Error,

    /**
     * Account's Get help, often with no bond and so no settings behind it (relationship type,
     * the unverified gate, the archived bond). "Go back", as [Error]: it returns to Account.
     */
    Account,
}

/** The screen's three faces: the form, and the two answers that replace it. */
enum class SupportStep {
    Writing,

    /** `201`: the ticket is filed. Support · sent. */
    Sent,

    /** `429 RATE_LIMITED`: five today already. Support · paused. */
    Paused,
}

/**
 * Contact support. [subject] and [body] are what the person typed, kept exactly (the
 * server stores them as sent) and never logged. [sending] is the one request in flight:
 * the action says Sending and a second tap does nothing. [sendError] is a send that did
 * not go; the words stay and the action becomes Try again.
 */
@Immutable
data class SupportState(
    val subject: String = "",
    val body: String = "",
    val subjectError: UiText? = null,
    val bodyError: UiText? = null,
    val sending: Boolean = false,
    val sendError: UiText? = null,
    val step: SupportStep = SupportStep.Writing,
)

sealed interface SupportAction {
    data class OnSubjectChange(val subject: String) : SupportAction
    data class OnBodyChange(val body: String) : SupportAction
    data object OnSendClick : SupportAction

    /** The one action on Sent and Paused: back where the person came from. */
    data object OnDoneClick : SupportAction
}

sealed interface SupportEvent {
    data object Close : SupportEvent
}
