package com.moyi.app.domain.legal

import com.moyi.app.core.DataError
import com.moyi.app.core.EmptyResult
import com.moyi.app.core.Result

/**
 * One published legal document: an opaque [version] label (compared, never parsed)
 * and the absolute [url] it is read at (`GET /meta/legal`, ADR-0056).
 */
data class PublishedDocument(val version: String, val url: String)

/** The documents in force now. The data-processing summary is shown, never accepted on its own. */
data class LegalDocuments(
    val terms: PublishedDocument,
    val privacy: PublishedDocument,
    val dataProcessingSummary: PublishedDocument,
)

/** The versions this person accepted (`GET /me`'s `legalAccepted`); null for one never accepted. */
data class AcceptedVersions(val terms: String?, val privacy: String?)

/** What the person accepted against what is current: the comparison the re-consent step rests on. */
data class LegalStatus(val documents: LegalDocuments, val accepted: AcceptedVersions) {
    /**
     * True when both the Terms and the Privacy Policy accepted are the ones in force.
     * Plain string equality, as the contract asks: a version is a label, so "local-2"
     * against "local-10" is simply different, never older or newer.
     */
    val upToDate: Boolean
        get() = accepted.terms == documents.terms.version && accepted.privacy == documents.privacy.version
}

/**
 * The Terms of Service and Privacy Policy (FR-011, FR-103): which are current, which the
 * signed-in person accepted, and accepting the current ones again.
 *
 * Accepting is never required by the server: no call is refused for an out-of-date
 * consent (the contract has no such error code), so the app asks and does not block.
 */
interface LegalRepository {
    /** `GET /meta/legal`: public, so it works before there is an account. */
    suspend fun documents(): Result<LegalDocuments, DataError.Network>

    /** The current documents and what the signed-in person accepted (`GET /meta/legal` and `GET /me`). */
    suspend fun status(): Result<LegalStatus, DataError.Network>

    /** `POST /me/legal-consent`: accepts the versions the server names as current, whichever they are. */
    suspend fun acceptCurrent(): EmptyResult<DataError.Network>
}
