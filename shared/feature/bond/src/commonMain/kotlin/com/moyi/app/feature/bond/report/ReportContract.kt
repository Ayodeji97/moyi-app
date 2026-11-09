package com.moyi.app.feature.bond.report

import androidx.compose.runtime.Immutable
import com.moyi.app.designsystem.presentation.UiText
import com.moyi.app.domain.report.ReportReason

/**
 * Where a report starts, and what it may be about.
 *
 * [entryId] is the partner's entry on the day the report was opened from, when that day
 * shows it (read in full): then the flow starts by asking which, the entry or the member
 * (`Report · choose target`). Null from Bond settings, or from a day without the partner's
 * words: then it is the member, and the flow starts at the form.
 */
data class ReportArgs(
    val bondId: String,
    val entryId: String? = null,
    val partnerName: String? = null,
    val fromSettings: Boolean = false,
)

/** What is being reported, once chosen. */
enum class ReportSubject { Entry, Member }

/**
 * The report flow (`26` §4; Figma Safety 599:1781): choose target, compose, the reason
 * picker, sent. One state for all of them, held by the flow's graph.
 */
@Immutable
data class ReportState(
    val partnerName: String? = null,
    /** True when the flow can report the day's entry: the choose-target screen offers it. */
    val canReportEntry: Boolean = false,
    val fromSettings: Boolean = false,
    val subject: ReportSubject = ReportSubject.Member,
    val reason: ReportReason? = null,
    val detail: String = "",
    /** The report is on its way: Send says so and cannot be tapped again. */
    val sending: Boolean = false,
    /** Send was tapped with no reason chosen: said under the reason field. */
    val reasonMissing: Boolean = false,
    /** The detail is over 500 code points: said under the detail field, input kept. */
    val detailTooLong: Boolean = false,
    /** Why the last send did not go through, over the action; the form is kept (`Report · failed · INPUT KEPT`). */
    val error: UiText? = null,
    /** The last send failed in a way a retry may fix: Send reads Try again. */
    val retryable: Boolean = false,
) {
    /** The reasons in the order the picker lists them: T-09's first, Something else last. */
    val reasons: List<ReportReason> get() = REASON_ORDER

    companion object {
        val REASON_ORDER = listOf(
            ReportReason.HARASSMENT,
            ReportReason.NON_CONSENSUAL,
            ReportReason.VIOLENCE_OR_SELF_HARM,
            ReportReason.MINOR_SEXUAL_CONTENT,
            ReportReason.OTHER,
        )
    }
}

sealed interface ReportAction {
    /** A row of `Report · choose target`. */
    data class OnSubjectChosen(val subject: ReportSubject) : ReportAction
    data object OnReasonFieldClick : ReportAction
    data class OnReasonPicked(val reason: ReportReason) : ReportAction
    data class OnDetailChange(val detail: String) : ReportAction
    data object OnSendClick : ReportAction
    /** The sent screen's one action: back where the report started. */
    data object OnDoneClick : ReportAction
}

sealed interface ReportEvent {
    data object OpenCompose : ReportEvent
    data object OpenReasonPicker : ReportEvent
    data object ReasonPicked : ReportEvent
    data object Sent : ReportEvent
    data object Done : ReportEvent
}
