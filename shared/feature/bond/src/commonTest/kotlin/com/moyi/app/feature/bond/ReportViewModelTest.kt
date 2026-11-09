package com.moyi.app.feature.bond

import app.cash.turbine.test
import com.moyi.app.core.ApiErrorCode
import com.moyi.app.core.ApiProblem
import com.moyi.app.core.DataError
import com.moyi.app.core.FieldError
import com.moyi.app.core.Result
import com.moyi.app.designsystem.presentation.UiText
import com.moyi.app.domain.report.FiledReport
import com.moyi.app.domain.report.ReportReason
import com.moyi.app.domain.report.ReportRepository
import com.moyi.app.domain.report.ReportTarget
import com.moyi.app.feature.bond.report.ReportAction
import com.moyi.app.feature.bond.report.ReportArgs
import com.moyi.app.feature.bond.report.ReportEvent
import com.moyi.app.feature.bond.report.ReportSubject
import com.moyi.app.feature.bond.report.ReportViewModel
import com.moyi.app.feature.bond.resources.Res
import com.moyi.app.feature.bond.resources.report_error_failed
import com.moyi.app.feature.bond.resources.report_error_not_found
import com.moyi.app.feature.bond.resources.report_error_own_entry
import com.moyi.app.feature.bond.resources.report_error_paused
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A report's ViewModel: what is reported, the checks before sending, one send at a time, the key, and each refusal's copy. */
class ReportViewModelTest : MainDispatcherTest() {
    private class Call(val target: ReportTarget, val reason: ReportReason, val detail: String?, val key: String)

    private class FakeReportRepository : ReportRepository {
        val calls = mutableListOf<Call>()
        var answer: Result<FiledReport, DataError.Network> = Result.Success(FiledReport("r1", ReportReason.OTHER))

        /** When set, the call waits for it: a report on its way. */
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun report(target: ReportTarget, reason: ReportReason, detail: String?, idempotencyKey: String): Result<FiledReport, DataError.Network> {
            calls += Call(target, reason, detail, idempotencyKey)
            gate?.await()
            return answer
        }
    }

    private val repo = FakeReportRepository()
    private var keys = 0

    private fun viewModel(args: ReportArgs) = ReportViewModel(args, repo, newKey = { "key-${++keys}" })

    private val fromDay = ReportArgs(bondId = "b1", entryId = "e9", partnerName = "Tunde")
    private val fromSettings = ReportArgs(bondId = "b1", partnerName = "Tunde", fromSettings = true)

    private fun api(status: Int, code: ApiErrorCode, fields: List<FieldError> = emptyList()) =
        Result.Failure(DataError.Network.Api(ApiProblem(status, code, fields)))

    // ----- what is reported -----

    @Test
    fun fromADayThatShowsTheEntryTheEntryIsOfferedAndReported() = runTest {
        val vm = viewModel(fromDay)
        assertTrue(vm.state.value.canReportEntry)
        vm.events.test {
            vm.onAction(ReportAction.OnSubjectChosen(ReportSubject.Entry))
            assertEquals(ReportEvent.OpenCompose, awaitItem())
            vm.onAction(ReportAction.OnReasonPicked(ReportReason.NON_CONSENSUAL))
            assertEquals(ReportEvent.ReasonPicked, awaitItem())
            vm.onAction(ReportAction.OnSendClick)
            assertEquals(ReportEvent.Sent, awaitItem())
        }
        assertEquals(ReportTarget.Entry("e9"), repo.calls.single().target)
        assertEquals(ReportReason.NON_CONSENSUAL, repo.calls.single().reason)
    }

    @Test
    fun fromTheSameDayTheMemberCanBeChosenInstead() = runTest {
        val vm = viewModel(fromDay)
        vm.onAction(ReportAction.OnSubjectChosen(ReportSubject.Member))
        vm.onAction(ReportAction.OnReasonPicked(ReportReason.HARASSMENT))
        vm.onAction(ReportAction.OnSendClick)
        assertEquals(ReportTarget.Member("b1"), repo.calls.single().target)
    }

    @Test
    fun fromSettingsItIsTheMemberAndNoEntryCanBeChosen() = runTest {
        val vm = viewModel(fromSettings)
        assertFalse(vm.state.value.canReportEntry)
        vm.onAction(ReportAction.OnSubjectChosen(ReportSubject.Entry))
        assertEquals(ReportSubject.Member, vm.state.value.subject)
        vm.onAction(ReportAction.OnReasonPicked(ReportReason.OTHER))
        vm.onAction(ReportAction.OnSendClick)
        assertEquals(ReportTarget.Member("b1"), repo.calls.single().target)
    }

    // ----- checks before sending -----

    @Test
    fun withNoReasonNothingIsSentAndTheFieldSaysSo() = runTest {
        val vm = viewModel(fromSettings)
        vm.onAction(ReportAction.OnSendClick)
        assertTrue(vm.state.value.reasonMissing)
        assertTrue(repo.calls.isEmpty())
        vm.onAction(ReportAction.OnReasonPicked(ReportReason.OTHER))
        assertFalse(vm.state.value.reasonMissing)
    }

    @Test
    fun aDetailOverFiveHundredIsNotSentAndIsKept() = runTest {
        val vm = viewModel(fromSettings)
        val long = "a".repeat(501)
        vm.onAction(ReportAction.OnReasonPicked(ReportReason.OTHER))
        vm.onAction(ReportAction.OnDetailChange(long))
        vm.onAction(ReportAction.OnSendClick)
        assertTrue(vm.state.value.detailTooLong)
        assertEquals(long, vm.state.value.detail)
        assertTrue(repo.calls.isEmpty())
        vm.onAction(ReportAction.OnDetailChange("a".repeat(500)))
        assertFalse(vm.state.value.detailTooLong)
    }

    @Test
    fun theDetailIsSentAsTyped() = runTest {
        val vm = viewModel(fromSettings)
        vm.onAction(ReportAction.OnReasonPicked(ReportReason.OTHER))
        vm.onAction(ReportAction.OnDetailChange("They told me to stop seeing friends."))
        vm.onAction(ReportAction.OnSendClick)
        assertEquals("They told me to stop seeing friends.", repo.calls.single().detail)
    }

    // ----- sending -----

    @Test
    fun aSecondTapWhileSendingIsIgnoredAndSendSaysWhatItIsDoing() = runTest {
        val vm = viewModel(fromSettings)
        val gate = CompletableDeferred<Unit>().also { repo.gate = it }
        vm.events.test {
            vm.onAction(ReportAction.OnReasonPicked(ReportReason.OTHER))
            assertEquals(ReportEvent.ReasonPicked, awaitItem())
            vm.onAction(ReportAction.OnSendClick)
            assertTrue(vm.state.value.sending)
            vm.onAction(ReportAction.OnSendClick)
            vm.onAction(ReportAction.OnSendClick)
            // While sending, the form does not change under the report.
            vm.onAction(ReportAction.OnReasonPicked(ReportReason.HARASSMENT))
            assertEquals(ReportReason.OTHER, vm.state.value.reason)
            gate.complete(Unit)
            assertEquals(ReportEvent.Sent, awaitItem())
        }
        assertEquals(1, repo.calls.size)
        assertFalse(vm.state.value.sending)
    }

    @Test
    fun aRetryOfTheSameReportSendsTheSameKey() = runTest {
        val vm = viewModel(fromSettings)
        repo.answer = Result.Failure(DataError.Network.NoInternet)
        vm.onAction(ReportAction.OnReasonPicked(ReportReason.OTHER))
        vm.onAction(ReportAction.OnSendClick)
        repo.answer = Result.Success(FiledReport("r1", ReportReason.OTHER))
        vm.onAction(ReportAction.OnSendClick)
        assertEquals(listOf("key-1", "key-1"), repo.calls.map { it.key })
    }

    @Test
    fun changingTheReportAfterAFailureMintsANewKey() = runTest {
        val vm = viewModel(fromSettings)
        repo.answer = Result.Failure(DataError.Network.Timeout)
        vm.onAction(ReportAction.OnReasonPicked(ReportReason.OTHER))
        vm.onAction(ReportAction.OnSendClick)
        vm.onAction(ReportAction.OnDetailChange("more"))
        vm.onAction(ReportAction.OnSendClick)
        assertEquals(listOf("key-1", "key-2"), repo.calls.map { it.key })
    }

    @Test
    fun doneLeavesTheFlow() = runTest {
        val vm = viewModel(fromDay)
        vm.events.test {
            vm.onAction(ReportAction.OnDoneClick)
            assertEquals(ReportEvent.Done, awaitItem())
        }
    }

    // ----- each refusal, as copy, the form kept -----

    private suspend fun failWith(answer: Result<FiledReport, DataError.Network>): ReportViewModel {
        val vm = viewModel(fromDay)
        repo.answer = answer
        vm.onAction(ReportAction.OnSubjectChosen(ReportSubject.Entry))
        vm.onAction(ReportAction.OnReasonPicked(ReportReason.HARASSMENT))
        vm.onAction(ReportAction.OnDetailChange("kept"))
        vm.onAction(ReportAction.OnSendClick)
        assertFalse(vm.state.value.sending)
        assertEquals("kept", vm.state.value.detail, "the form is kept (Report · failed · INPUT KEPT)")
        assertEquals(ReportReason.HARASSMENT, vm.state.value.reason)
        return vm
    }

    @Test
    fun noAnswerIsTheFailedLineWithTryAgain() = runTest {
        for (error in listOf(DataError.Network.NoInternet, DataError.Network.Timeout, DataError.Network.Server(503), DataError.Network.Unknown)) {
            val vm = failWith(Result.Failure(error))
            assertEquals(UiText.Resource(Res.string.report_error_failed), vm.state.value.error)
            assertTrue(vm.state.value.retryable)
        }
    }

    @Test
    fun theSameReportStillBeingFiledIsTheFailedLineWithTryAgain() = runTest {
        val vm = failWith(api(409, ApiErrorCode.IDEMPOTENCY_KEY_IN_FLIGHT))
        assertEquals(UiText.Resource(Res.string.report_error_failed), vm.state.value.error)
        assertTrue(vm.state.value.retryable)
    }

    @Test
    fun myOwnEntryIsItsOwnLine() = runTest {
        val vm = failWith(api(409, ApiErrorCode.REPORT_ON_OWN_ENTRY))
        assertEquals(UiText.Resource(Res.string.report_error_own_entry), vm.state.value.error)
        assertFalse(vm.state.value.retryable)
    }

    @Test
    fun notFoundSaysItCanNoLongerBeReported() = runTest {
        val vm = failWith(api(404, ApiErrorCode.NOT_FOUND))
        assertEquals(UiText.Resource(Res.string.report_error_not_found), vm.state.value.error)
        assertFalse(vm.state.value.retryable)
    }

    @Test
    fun theDailyAllowanceSaysReportsArePaused() = runTest {
        val vm = failWith(api(429, ApiErrorCode.RATE_LIMITED))
        assertEquals(UiText.Resource(Res.string.report_error_paused), vm.state.value.error)
        assertFalse(vm.state.value.retryable)
    }

    @Test
    fun aDetailTheServerRefusesIsSaidUnderTheField() = runTest {
        val vm = failWith(api(422, ApiErrorCode.VALIDATION_FAILED, listOf(FieldError("detail", "SIZE", "too long"))))
        assertTrue(vm.state.value.detailTooLong)
        assertNull(vm.state.value.error)
    }

    @Test
    fun anyOther422IsTheFailedLine() = runTest {
        val vm = failWith(api(422, ApiErrorCode.VALIDATION_FAILED, listOf(FieldError("Idempotency-Key", "NOT_BLANK", "is required"))))
        assertEquals(UiText.Resource(Res.string.report_error_failed), vm.state.value.error)
        assertFalse(vm.state.value.detailTooLong)
    }

    @Test
    fun editingAfterAFailureClearsTheLine() = runTest {
        val vm = failWith(Result.Failure(DataError.Network.NoInternet))
        vm.onAction(ReportAction.OnDetailChange("kept, and more"))
        assertNull(vm.state.value.error)
        assertFalse(vm.state.value.retryable)
    }
}
