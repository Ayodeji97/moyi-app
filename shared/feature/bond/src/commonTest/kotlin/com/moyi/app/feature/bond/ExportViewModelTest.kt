package com.moyi.app.feature.bond

import app.cash.turbine.test
import com.moyi.app.core.ApiErrorCode
import com.moyi.app.core.ApiProblem
import com.moyi.app.core.DataError
import com.moyi.app.core.Result
import com.moyi.app.designsystem.presentation.UiText
import com.moyi.app.domain.account.DataExport
import com.moyi.app.domain.account.DataExportRepository
import com.moyi.app.domain.account.DataExportStatus
import com.moyi.app.feature.bond.account.ExportAction
import com.moyi.app.feature.bond.account.ExportEvent
import com.moyi.app.feature.bond.account.ExportLoad
import com.moyi.app.feature.bond.account.ExportViewModel
import com.moyi.app.feature.bond.resources.Res
import com.moyi.app.feature.bond.resources.export_error_rate_limited
import com.moyi.app.feature.bond.resources.settings_error_generic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Export your archive: load, ask, the double-tap guard, polling (backed off, only while shown,
 * stopping when ready or when the screen leaves), the download and its expiry, and refusals.
 *
 * Polling waits on [tick], not on time: each `tick()` lets exactly one wait end.
 */
class ExportViewModelTest : MainDispatcherTest() {
    private class FakeExports : DataExportRepository {
        var latest: DataExport? = null
        val latestReads = ArrayDeque<Result<DataExport?, DataError.Network>>()
        val requestAnswers = ArrayDeque<Result<DataExport, DataError.Network>>()
        /** What each poll answers, in order; then the last export as it was. */
        val polls = ArrayDeque<Result<DataExport, DataError.Network>>()
        var requests = 0
        var pollCount = 0
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun latest() = latestReads.removeFirstOrNull() ?: Result.Success(latest)

        override suspend fun request(): Result<DataExport, DataError.Network> {
            requests++
            gate?.await()
            return requestAnswers.removeFirstOrNull() ?: Result.Success(preparing).also { latest = preparing }
        }

        override suspend fun export(id: String): Result<DataExport, DataError.Network> {
            pollCount++
            return polls.removeFirstOrNull() ?: Result.Success(latest ?: preparing)
        }
    }

    private val exports = FakeExports()
    private val ticks = Channel<Unit>(Channel.UNLIMITED)
    private val waits = mutableListOf<Duration>()
    private var now = Instant.parse("2026-11-09T11:00:00Z")
    private val clock = object : Clock {
        override fun now(): Instant = now
    }
    private val viewModel by lazy {
        ExportViewModel(exports, clock) { duration ->
            waits += duration
            ticks.receive()
        }
    }

    private fun tick() {
        ticks.trySend(Unit)
    }

    // ----- load -----

    @Test
    fun neverAskedIsTheStartingStateNotAnError() {
        val state = viewModel.state.value
        assertEquals(ExportLoad.Ready, state.load)
        assertNull(state.status)
    }

    @Test
    fun aReadyExportIsShownWithoutItsUrl() {
        exports.latest = ready
        val state = viewModel.state.value

        assertEquals(DataExportStatus.READY, state.status)
        assertEquals(ready.expiresAt, state.expiresAt)
        assertEquals(2_048_000L, state.sizeBytes)
        assertFalse("secret" in state.toString())
    }

    @Test
    fun aFailedLoadIsRetried() {
        exports.latestReads += Result.Failure(DataError.Network.Timeout)
        assertEquals(ExportLoad.Failed, viewModel.state.value.load)

        viewModel.onAction(ExportAction.OnRetryClick)

        assertEquals(ExportLoad.Ready, viewModel.state.value.load)
    }

    // ----- asking -----

    @Test
    fun askingShowsPreparing() {
        viewModel.onAction(ExportAction.OnRequestClick)

        assertEquals(DataExportStatus.PREPARING, viewModel.state.value.status)
        assertFalse(viewModel.state.value.requesting)
        assertEquals(1, exports.requests)
    }

    @Test
    fun aDoubleTapAsksOnce() {
        exports.gate = CompletableDeferred()
        viewModel.onAction(ExportAction.OnRequestClick)
        viewModel.onAction(ExportAction.OnRequestClick)
        assertTrue(viewModel.state.value.requesting)
        exports.gate?.complete(Unit)

        viewModel.onAction(ExportAction.OnRequestClick) // now PREPARING: nothing new to ask for
        assertEquals(1, exports.requests)
    }

    @Test
    fun theDailyLimitIsSaidPlainly() {
        exports.requestAnswers += Result.Failure(DataError.Network.Api(ApiProblem(429, ApiErrorCode.RATE_LIMITED)))
        viewModel.onAction(ExportAction.OnRequestClick)

        assertEquals(UiText.Resource(Res.string.export_error_rate_limited), viewModel.state.value.error)
        assertNull(viewModel.state.value.status)
        assertFalse(viewModel.state.value.requesting)
    }

    @Test
    fun anyOtherFailureIsTheGenericError() {
        exports.requestAnswers += Result.Failure(DataError.Network.NoInternet)
        viewModel.onAction(ExportAction.OnRequestClick)

        assertEquals(UiText.Resource(Res.string.settings_error_generic), viewModel.state.value.error)
    }

    @Test
    fun afterAFailedOrExpiredExportANewOneCanBeAskedFor() {
        exports.latest = preparing.copyAs(DataExportStatus.FAILED)
        viewModel.state.value

        viewModel.onAction(ExportAction.OnRequestClick)

        assertEquals(1, exports.requests)
        assertEquals(DataExportStatus.PREPARING, viewModel.state.value.status)
    }

    // ----- polling -----

    @Test
    fun pollingRunsWhileShownBacksOffAndStopsWhenReady() {
        exports.latest = preparing
        exports.polls += Result.Success(preparing)
        exports.polls += Result.Failure(DataError.Network.NoInternet) // silent: tried again later
        exports.polls += Result.Success(ready)
        viewModel.onAction(ExportAction.OnScreenShown)

        repeat(3) { tick() }

        assertEquals(3, exports.pollCount)
        assertEquals(DataExportStatus.READY, viewModel.state.value.status)
        assertEquals(ExportViewModel.POLL_STEPS.take(3), waits)

        tick()
        assertEquals(3, exports.pollCount, "no poll once it is ready")
    }

    @Test
    fun pollingStopsWhenTheScreenLeavesAndResumesWhenItReturns() {
        exports.latest = preparing
        viewModel.onAction(ExportAction.OnScreenShown)
        tick()
        assertEquals(1, exports.pollCount)

        viewModel.onAction(ExportAction.OnScreenHidden)
        repeat(5) { tick() }
        assertEquals(1, exports.pollCount, "no poll while the screen is gone")

        // Back on screen: polling starts again, from the shortest wait.
        while (ticks.tryReceive().isSuccess) Unit
        waits.clear()
        viewModel.onAction(ExportAction.OnScreenShown)
        tick()
        assertEquals(2, exports.pollCount)
        assertEquals(ExportViewModel.POLL_STEPS.first(), waits.first())
    }

    @Test
    fun nothingPollsBeforeTheScreenIsShown() {
        exports.latest = preparing
        viewModel.state.value
        repeat(3) { tick() }

        assertEquals(0, exports.pollCount)
    }

    @Test
    fun askingWhileShownStartsPolling() {
        viewModel.onAction(ExportAction.OnScreenShown)
        viewModel.onAction(ExportAction.OnRequestClick)
        exports.polls += Result.Success(ready)

        tick()

        assertEquals(1, exports.pollCount)
        assertEquals(DataExportStatus.READY, viewModel.state.value.status)
    }

    @Test
    fun theWaitsGrowAndThenHoldAtTheLongest() {
        exports.latest = preparing
        viewModel.onAction(ExportAction.OnScreenShown)
        repeat(ExportViewModel.POLL_STEPS.size + 2) { tick() }

        assertEquals(ExportViewModel.POLL_STEPS + List(2) { ExportViewModel.POLL_STEPS.last() }, waits.take(ExportViewModel.POLL_STEPS.size + 2))
        assertTrue(waits.zipWithNext().all { (a, b) -> b >= a })
    }

    // ----- download -----

    @Test
    fun downloadOpensTheSignedUrl() = runTest {
        exports.latest = ready
        viewModel.events.test {
            viewModel.onAction(ExportAction.OnDownloadClick)

            val event = assertIs<ExportEvent.OpenDownload>(awaitItem())
            assertEquals(ready.downloadUrl, event.url)
            assertFalse("secret" in event.toString())
        }
    }

    @Test
    fun aDownloadPastItsExpiryShowsExpiredInsteadOfOpening() = runTest {
        exports.latest = ready
        viewModel.state.value
        now = ready.expiresAt!!

        viewModel.events.test {
            viewModel.onAction(ExportAction.OnDownloadClick)
            expectNoEvents()
        }
        assertEquals(DataExportStatus.EXPIRED, viewModel.state.value.status)
    }

    private companion object {
        val preparing = DataExport(id = "e1", status = DataExportStatus.PREPARING, requestedAt = Instant.parse("2026-11-09T10:00:00Z"))
        val ready = DataExport(
            id = "e1",
            status = DataExportStatus.READY,
            requestedAt = Instant.parse("2026-11-09T10:00:00Z"),
            readyAt = Instant.parse("2026-11-09T10:03:00Z"),
            expiresAt = Instant.parse("2026-11-10T10:03:00Z"),
            sizeBytes = 2_048_000,
            downloadUrl = "https://storage.test/e1.zip?X-Sig=secret",
        )

        fun DataExport.copyAs(status: DataExportStatus) = DataExport(id, status, requestedAt)
    }
}
