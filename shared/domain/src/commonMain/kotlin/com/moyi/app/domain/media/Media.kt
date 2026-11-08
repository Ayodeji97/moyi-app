package com.moyi.app.domain.media

import com.moyi.app.core.DataError
import com.moyi.app.core.EmptyResult
import com.moyi.app.core.Result

/**
 * What an upload is (`UploadIntentRequest.kind`). A voice note is the same pipeline with
 * another kind and content type, so nothing here is photo-only.
 */
enum class MediaKind { Image, Voice }

/** Where an uploaded asset stands on the server (`MediaResponse.status`, spec §4 D1 and D2). */
enum class MediaStatus {
    PendingUpload,
    Uploaded,
    Processing,

    /** Processed: an entry may attach it now, and not before (`media_assets.status = 'READY'`). */
    Ready,

    /** Kept back by moderation. It cannot be attached. */
    Quarantined,

    /** The bytes could not be processed. It cannot be attached. */
    Failed,
    Deleted,
}

/**
 * The bytes to upload, already prepared on the device (for a photo: downscaled and
 * re-encoded, so no camera metadata leaves the phone). Never printed: [toString] names
 * only the type and the size.
 */
class MediaBytes(
    val kind: MediaKind,
    val contentType: String,
    val bytes: ByteArray,
) {
    override fun toString(): String = "MediaBytes(kind=$kind, contentType=$contentType, size=${bytes.size})"
}

/**
 * `POST /media/upload-intent`'s answer: where to PUT the bytes, and the headers the
 * signature covers, which the PUT must send exactly. [uploadUrl] is a bearer credential
 * for one upload, so [toString] leaves it out.
 */
class UploadTicket(
    val mediaId: String,
    val uploadUrl: String,
    val requiredHeaders: Map<String, String>,
) {
    override fun toString(): String = "UploadTicket(mediaId=$mediaId, uploadUrl=(redacted), headers=${requiredHeaders.keys})"
}

/**
 * The media endpoints (doc 06 §3.5) and the direct upload to storage. Every call but
 * [upload] goes to the API; [upload] goes straight to the storage, so the photo's bytes
 * never pass through the API server (FR-044, NFR-009).
 */
interface MediaRepository {
    /** Declares the upload: its kind, type, exact length and SHA-256, which the storage then holds it to. */
    suspend fun requestUpload(media: MediaBytes): Result<UploadTicket, DataError.Network>

    /** PUTs [media]'s bytes to [ticket]'s URL with exactly its required headers. */
    suspend fun upload(ticket: UploadTicket, media: MediaBytes): EmptyResult<DataError.Network>

    /** Tells the server the PUT is done. [idempotencyKey] is reused on every retry of the same completion. */
    suspend fun complete(mediaId: String, idempotencyKey: String): Result<MediaStatus, DataError.Network>

    suspend fun status(mediaId: String): Result<MediaStatus, DataError.Network>

    /** Deletes an upload that will not be sent after all (removed or replaced before Send). */
    suspend fun delete(mediaId: String): EmptyResult<DataError.Network>
}
