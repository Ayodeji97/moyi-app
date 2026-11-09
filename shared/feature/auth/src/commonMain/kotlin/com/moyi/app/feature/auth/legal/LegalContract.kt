package com.moyi.app.feature.auth.legal

import androidx.compose.runtime.Immutable
import com.moyi.app.domain.legal.LegalDocuments
import com.moyi.app.feature.auth.common.LegalDocument
import com.moyi.app.feature.auth.common.SubmitStatus

/** Whether the documents are on screen yet. Loading draws the bare `surface`: no spinner. */
enum class LegalLoad { Loading, Failed, Ready }

/**
 * Terms and Privacy (PROPOSED, docs/missing-components.md §54): the documents in force,
 * and Accept while the person's consent is older than them.
 *
 * [prompt] is the re-consent step the shell opens over Today when the versions changed;
 * otherwise the screen was opened from Bond settings, to read the documents.
 */
@Immutable
data class LegalState(
    val prompt: Boolean,
    val load: LegalLoad = LegalLoad.Loading,
    val documents: LegalDocuments? = null,
    /** The person accepted the Terms and the Privacy Policy in force; Accept is not offered. */
    val upToDate: Boolean = true,
    val accept: SubmitStatus = SubmitStatus.Idle,
)

sealed interface LegalAction {
    data object OnRetryClick : LegalAction
    data class OnDocumentClick(val document: LegalDocument) : LegalAction
    data object OnAcceptClick : LegalAction

    /** Back, or Not now on the prompt: nothing is recorded, and the app asks again next launch. */
    data object OnCloseClick : LegalAction
}

sealed interface LegalEvent {
    /** Open a document in the in-app browser (D8). */
    data class OpenPage(val url: String) : LegalEvent

    /** Leave the screen: closed, or accepted from the prompt. */
    data object Close : LegalEvent
}
