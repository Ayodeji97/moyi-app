package com.moyi.app.feature.bond.account

import androidx.compose.runtime.Immutable
import com.moyi.app.designsystem.presentation.UiText
import com.moyi.app.domain.account.DataExportStatus
import kotlin.time.Instant

/** Whether the latest export is on screen yet. Loading draws the bare `surface`: no spinner. */
enum class ExportLoad { Loading, Failed, Ready }

/**
 * Export your archive (FR-009, FR-094; states.md §9 `Account 8 · export`).
 *
 * [status] is null when no export was ever asked for. [expiresAt] and [sizeBytes] are set only
 * while it is READY. **The download URL is not in this state**: it is a credential, held by
 * the ViewModel and handed to the platform browser at the tap, so it is never drawn, kept in
 * a saved state or printed with the state.
 */
@Immutable
data class ExportState(
    val load: ExportLoad = ExportLoad.Loading,
    val status: DataExportStatus? = null,
    val expiresAt: Instant? = null,
    val sizeBytes: Long? = null,
    /** The `POST` is in flight: a second tap sends nothing. */
    val requesting: Boolean = false,
    val error: UiText? = null,
)

sealed interface ExportAction {
    /** The screen is started (on screen, app in front): polling may run. */
    data object OnScreenShown : ExportAction

    /** The screen is stopped (covered, popped, app in the background): polling stops. */
    data object OnScreenHidden : ExportAction
    data object OnRetryClick : ExportAction
    data object OnRequestClick : ExportAction
    data object OnDownloadClick : ExportAction
}

sealed interface ExportEvent {
    /**
     * Open [url] in the platform's browser, which downloads the archive. Not a data class, and
     * [toString] leaves the URL out: it is the archive itself to anyone who holds it.
     */
    class OpenDownload(val url: String) : ExportEvent {
        override fun toString(): String = "OpenDownload(url=<redacted>)"
    }
}
