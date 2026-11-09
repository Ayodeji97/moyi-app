package com.moyi.app.feature.bond.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moyi.app.core.ApiErrorCode
import com.moyi.app.core.DataError
import com.moyi.app.core.Result
import com.moyi.app.designsystem.presentation.UiText
import com.moyi.app.domain.report.ReportReason
import com.moyi.app.domain.report.ReportRepository
import com.moyi.app.domain.report.ReportTarget
import com.moyi.app.domain.report.isReportDetailTooLong
import com.moyi.app.feature.bond.resources.Res
import com.moyi.app.feature.bond.resources.report_error_failed
import com.moyi.app.feature.bond.resources.report_error_not_found
import com.moyi.app.feature.bond.resources.report_error_own_entry
import com.moyi.app.feature.bond.resources.report_error_paused
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * One report (FR-100, ADR-0053), from the Archive day view or Bond settings ([args]).
 *
 * - **What:** the day's entry or the member, chosen first when the day shows the
 *   partner's entry ([ReportArgs.entryId]); otherwise the member. Never my own entry:
 *   nothing here can name it (`409 REPORT_ON_OWN_ENTRY` is a guard, mapped all the same).
 * - **Why:** one of the contract's five reasons, required; an optional detail of at most
 *   500 code points, checked here before it is sent and again by the server.
 * - **Sending** is one call at a time: a tap while one is on its way is ignored. The
 *   `Idempotency-Key` is minted at the first Send of a report and kept for every retry of
 *   the same report, so a retry after a lost answer files nothing twice. Changing what,
 *   why or the detail is a different report and gets a new key, since the server binds a
 *   key to its body.
 * - **A failure keeps the form** (`Report · failed · INPUT KEPT`), with the line that says
 *   why: no answer or a server failure (Try again), the daily allowance (`429`), a target
 *   that cannot be reported any more (`404`), my own entry (`409`), a detail too long (`422`).
 * - **Sent** says a person will read it and that the partner is not told, which is true of
 *   both routes (ADR-0053 decisions 5 and 14). Nothing else changes for the partner.
 */
class ReportViewModel(
    private val args: ReportArgs,
    private val reports: ReportRepository,
    private val newKey: () -> String = ::randomReportKey,
) : ViewModel() {
    private val _state = MutableStateFlow(
        ReportState(
            partnerName = args.partnerName,
            canReportEntry = args.entryId != null,
            fromSettings = args.fromSettings,
            subject = if (args.entryId != null) ReportSubject.Entry else ReportSubject.Member,
        ),
    )
    val state: StateFlow<ReportState> = _state.asStateFlow()

    private val _events = Channel<ReportEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /** The key of the report last sent and not filed; null once anything about it changes. */
    private var key: String? = null

    fun onAction(action: ReportAction) {
        // While a report is on its way, nothing about it changes and nothing opens.
        if (_state.value.sending && action != ReportAction.OnDoneClick) return
        when (action) {
            is ReportAction.OnSubjectChosen -> {
                if (action.subject == ReportSubject.Entry && args.entryId == null) return
                changed { it.copy(subject = action.subject) }
                send(ReportEvent.OpenCompose)
            }
            ReportAction.OnReasonFieldClick -> send(ReportEvent.OpenReasonPicker)
            is ReportAction.OnReasonPicked -> {
                changed { it.copy(reason = action.reason, reasonMissing = false) }
                send(ReportEvent.ReasonPicked)
            }
            is ReportAction.OnDetailChange -> changed {
                // The error under the field clears as soon as the text fits again.
                it.copy(detail = action.detail, detailTooLong = it.detailTooLong && isReportDetailTooLong(action.detail))
            }
            ReportAction.OnSendClick -> submit()
            ReportAction.OnDoneClick -> send(ReportEvent.Done)
        }
    }

    /** Anything that makes it a different report: a new key next time, and the old failure line goes. */
    private fun changed(update: (ReportState) -> ReportState) {
        key = null
        _state.update { update(it).copy(error = null, retryable = false) }
    }

    private fun submit() {
        val current = _state.value
        if (current.sending) return
        val reason = current.reason
        val tooLong = isReportDetailTooLong(current.detail)
        if (reason == null || tooLong) {
            _state.update { it.copy(reasonMissing = reason == null, detailTooLong = tooLong, error = null, retryable = false) }
            return
        }
        val target = when (current.subject) {
            ReportSubject.Entry -> ReportTarget.Entry(args.entryId ?: return)
            ReportSubject.Member -> ReportTarget.Member(args.bondId)
        }
        val idempotencyKey = key ?: newKey().also { key = it }
        _state.update { it.copy(sending = true, error = null, retryable = false) }
        viewModelScope.launch {
            val result = reports.report(target, reason, current.detail, idempotencyKey)
            when (result) {
                is Result.Success -> {
                    key = null
                    _state.update { it.copy(sending = false) }
                    _events.send(ReportEvent.Sent)
                }
                is Result.Failure -> {
                    val failure = result.error.toReportFailure()
                    _state.update {
                        it.copy(
                            sending = false,
                            error = failure.text,
                            retryable = failure.retryable,
                            detailTooLong = failure.detailTooLong,
                        )
                    }
                }
            }
        }
    }

    private fun send(event: ReportEvent) {
        viewModelScope.launch { _events.send(event) }
    }
}

/** How a refusal is shown: the line over the action (or none, when the field says it), and whether Try again fits. */
internal data class ReportFailure(val text: UiText?, val retryable: Boolean, val detailTooLong: Boolean = false)

/**
 * Each refusal the report routes give, as copy. By code where the code decides, else by
 * status, so a code this build does not know still lands on an honest line.
 */
internal fun DataError.Network.toReportFailure(): ReportFailure {
    val failed = ReportFailure(UiText.Resource(Res.string.report_error_failed), retryable = true)
    if (this !is DataError.Network.Api) return failed
    return when {
        problem.code == ApiErrorCode.REPORT_ON_OWN_ENTRY ->
            ReportFailure(UiText.Resource(Res.string.report_error_own_entry), retryable = false)
        problem.status == 404 ->
            ReportFailure(UiText.Resource(Res.string.report_error_not_found), retryable = false)
        problem.code == ApiErrorCode.RATE_LIMITED || problem.status == 429 ->
            ReportFailure(UiText.Resource(Res.string.report_error_paused), retryable = false)
        // The detail the server refused: said under the field, as the client's own check does.
        problem.status == 422 && problem.fieldErrors.any { it.field == "detail" } ->
            ReportFailure(text = null, retryable = false, detailTooLong = true)
        // IDEMPOTENCY_KEY_IN_FLIGHT (the same report still being filed), and anything else.
        else -> failed
    }
}

@OptIn(ExperimentalUuidApi::class)
internal fun randomReportKey(): String = Uuid.random().toString()
