package com.moyi.app.feature.bond.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moyi.app.core.ApiErrorCode
import com.moyi.app.core.DataError
import com.moyi.app.core.Result
import com.moyi.app.designsystem.presentation.UiText
import com.moyi.app.domain.account.DataExport
import com.moyi.app.domain.account.DataExportRepository
import com.moyi.app.domain.account.DataExportStatus
import com.moyi.app.feature.bond.resources.Res
import com.moyi.app.feature.bond.resources.export_error_rate_limited
import com.moyi.app.feature.bond.resources.settings_error_generic
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Export your archive (FR-009, FR-094, ADR-0059), scoped to its own screen.
 *
 * - **Load**: `GET /me/export`, the latest export, so a restart shows where it is. Never
 *   asked is the starting state, not an error.
 * - **Ask**: `POST /me/export`. Asking while one is PREPARING answers that same export. The
 *   fourth new one in a day is `429`, said plainly.
 * - **Polling**, `GET /me/export/{id}`, only while the export is PREPARING **and the screen
 *   is shown** ([ExportAction.OnScreenShown] / [ExportAction.OnScreenHidden], from the
 *   lifecycle): never in the background, never after the screen has gone. It backs off
 *   ([POLL_STEPS]) and stops once the export is no longer PREPARING. A failed poll is
 *   silent and tried again at the next step; the ready email covers a person who leaves.
 * - **Download** hands the signed URL to the platform browser. It lasts 24 hours; past
 *   [DataExport.expiresAt] the screen says it has expired instead of opening a dead link.
 *
 * [pause] is `delay` in the app; a test gives one it controls, so polling runs step by step.
 */
class ExportViewModel(
    private val exports: DataExportRepository,
    private val clock: Clock = Clock.System,
    private val pause: suspend (Duration) -> Unit = { delay(it) },
) : ViewModel() {
    private val _state = MutableStateFlow(ExportState())
    val state: StateFlow<ExportState> = _state.asStateFlow()

    private val _events = Channel<ExportEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /** The export as last read, with its URL; only [state]'s safe parts are published. */
    private var current: DataExport? = null
    private var shown = false
    private var polling: Job? = null

    init {
        load()
    }

    fun onAction(action: ExportAction) {
        when (action) {
            ExportAction.OnScreenShown -> {
                shown = true
                startPolling()
            }
            ExportAction.OnScreenHidden -> {
                shown = false
                stopPolling()
            }
            ExportAction.OnRetryClick -> load()
            ExportAction.OnRequestClick -> request()
            ExportAction.OnDownloadClick -> download()
        }
    }

    private fun load() {
        _state.update { it.copy(load = ExportLoad.Loading, error = null) }
        viewModelScope.launch {
            when (val result = exports.latest()) {
                is Result.Success -> {
                    show(result.data)
                    _state.update { it.copy(load = ExportLoad.Ready) }
                    startPolling()
                }
                is Result.Failure -> _state.update { it.copy(load = ExportLoad.Failed) }
            }
        }
    }

    private fun request() {
        if (_state.value.requesting || current?.status == DataExportStatus.PREPARING) return
        _state.update { it.copy(requesting = true, error = null) }
        viewModelScope.launch {
            when (val result = exports.request()) {
                is Result.Success -> {
                    show(result.data)
                    _state.update { it.copy(requesting = false) }
                    startPolling()
                }
                is Result.Failure -> _state.update {
                    it.copy(requesting = false, error = if (result.error.code() == ApiErrorCode.RATE_LIMITED) rateLimited else generic)
                }
            }
        }
    }

    private fun download() {
        val export = current ?: return
        val url = export.downloadUrl
        val expiresAt = export.expiresAt
        if (export.status != DataExportStatus.READY || url == null) return
        if (expiresAt != null && clock.now() >= expiresAt) {
            // The 24 hours ran out while the screen was open: say so, and offer a new one.
            show(DataExport(export.id, DataExportStatus.EXPIRED, export.requestedAt, export.readyAt))
            return
        }
        viewModelScope.launch { _events.send(ExportEvent.OpenDownload(url)) }
    }

    private fun show(export: DataExport?) {
        current = export
        _state.update { it.copy(status = export?.status, expiresAt = export?.expiresAt, sizeBytes = export?.sizeBytes) }
    }

    private fun startPolling() {
        if (!shown || polling?.isActive == true) return
        val id = current?.takeIf { it.status == DataExportStatus.PREPARING }?.id ?: return
        polling = viewModelScope.launch {
            var step = 0
            while (true) {
                pause(POLL_STEPS[step.coerceAtMost(POLL_STEPS.lastIndex)])
                step++
                val result = exports.export(id)
                if (result is Result.Success) {
                    show(result.data)
                    if (result.data.status != DataExportStatus.PREPARING) break
                } else if ((result as Result.Failure).error.status() == NOT_FOUND) {
                    // Not ours to see any more: start again from the latest.
                    show(null)
                    break
                }
            }
        }
    }

    private fun stopPolling() {
        polling?.cancel()
        polling = null
    }

    private fun DataError.Network.code(): ApiErrorCode? = (this as? DataError.Network.Api)?.problem?.code

    private fun DataError.Network.status(): Int? = (this as? DataError.Network.Api)?.problem?.status

    private val rateLimited = UiText.Resource(Res.string.export_error_rate_limited)
    private val generic = UiText.Resource(Res.string.settings_error_generic)

    companion object {
        /** The waits between polls: quick at first (it is usually minutes), then once a minute. */
        val POLL_STEPS: List<Duration> = listOf(3.seconds, 5.seconds, 10.seconds, 20.seconds, 30.seconds, 60.seconds)
    }
}

private const val NOT_FOUND = 404
