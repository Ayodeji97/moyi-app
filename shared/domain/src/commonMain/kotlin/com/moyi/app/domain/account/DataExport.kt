package com.moyi.app.domain.account

import com.moyi.app.core.DataError
import com.moyi.app.core.Result
import kotlin.time.Instant

/** Where an export is (FR-009): built by a job, ready for 24 hours, then gone. */
enum class DataExportStatus { PREPARING, READY, EXPIRED, FAILED }

/**
 * One export of everything the person is entitled to (FR-009, FR-094, ADR-0059).
 *
 * [downloadUrl], [expiresAt] and [sizeBytes] are set only while [status] is READY.
 * **[downloadUrl] is a credential**: a signed URL that downloads the archive without a
 * bearer, so anyone holding it holds the archive. It is kept out of [toString], so a log
 * line or a crash report that prints an export never carries it.
 */
class DataExport(
    val id: String,
    val status: DataExportStatus,
    val requestedAt: Instant,
    val readyAt: Instant? = null,
    val expiresAt: Instant? = null,
    val sizeBytes: Long? = null,
    val downloadUrl: String? = null,
) {
    override fun equals(other: Any?): Boolean =
        other is DataExport && id == other.id && status == other.status && requestedAt == other.requestedAt &&
            readyAt == other.readyAt && expiresAt == other.expiresAt && sizeBytes == other.sizeBytes &&
            downloadUrl == other.downloadUrl

    override fun hashCode(): Int = listOf(id, status, requestedAt, readyAt, expiresAt, sizeBytes, downloadUrl).hashCode()

    override fun toString(): String =
        "DataExport(id=$id, status=$status, requestedAt=$requestedAt, readyAt=$readyAt, expiresAt=$expiresAt, " +
            "sizeBytes=$sizeBytes, downloadUrl=${if (downloadUrl == null) "null" else "<redacted>"})"
}

/**
 * Data export, `POST /me/export`, `GET /me/export` and `GET /me/export/{exportId}`.
 *
 * On [request], asking while one is PREPARING answers that same export; three new ones a day,
 * the fourth is `429 RATE_LIMITED`.
 */
interface DataExportRepository {
    /** The most recently requested export, or null when none was ever asked for (`404`). */
    suspend fun latest(): Result<DataExport?, DataError.Network>

    suspend fun request(): Result<DataExport, DataError.Network>

    /** One export as it is now; `404` once it is not the caller's to see. */
    suspend fun export(id: String): Result<DataExport, DataError.Network>
}
