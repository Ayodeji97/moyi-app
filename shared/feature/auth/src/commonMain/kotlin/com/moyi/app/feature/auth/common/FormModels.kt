package com.moyi.app.feature.auth.common

import androidx.compose.runtime.Immutable
import com.moyi.app.designsystem.presentation.UiText

/**
 * One text field's value and the error shown under it, if any.
 *
 * [showErrors] records that the person has left the field (blur) or tried to
 * submit: until then, a half-typed address is not an error (states.md §1c
 * places Field's Error variant on submit, not on every keystroke).
 */
@Immutable
data class FieldInput(
    val value: String = "",
    val error: UiText? = null,
    val showErrors: Boolean = false,
)

/**
 * Where a submission stands. One explicit status rather than separate
 * `isLoading` / `error` flags, so "sending with an error showing" cannot be
 * represented.
 */
@Immutable
sealed interface SubmitStatus {
    /** Nothing in flight; the action reads its normal label. */
    data object Idle : SubmitStatus

    /** In flight. The action shows its Sending label and ignores taps. */
    data object Sending : SubmitStatus

    /**
     * The last attempt failed for a reason that belongs to the form, not a
     * field. [message] sits above the action (InlineError). When [offerRetry]
     * is true the action reads "Try again" (states.md §1c, no connection).
     * Everything the person typed is kept.
     */
    data class Failed(val message: UiText, val offerRetry: Boolean) : SubmitStatus
}
