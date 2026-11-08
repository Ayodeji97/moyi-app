package com.moyi.app.domain.media

import com.moyi.app.core.ApiErrorCode
import com.moyi.app.core.DataError
import com.moyi.app.core.Result
import kotlinx.coroutines.delay

/**
 * How far one upload got, so a retry resumes rather than starting over: once the bytes
 * are in storage they are not sent again, and once the server was told, the same
 * completion key is reused (the server replays it). A fresh upload is [UploadProgress.None].
 */
data class UploadProgress(
    /** Set once the PUT succeeded: the asset the bytes are in. */
    val mediaId: String? = null,
    /** The completion's `Idempotency-Key`, minted once per asset. */
    val completeKey: String? = null,
    /** The server has accepted the completion: only waiting for it to be processed is left. */
    val completed: Boolean = false,
) {
    companion object {
        val None = UploadProgress()
    }
}

/** Why an upload stopped. */
enum class UploadFailure {
    /** No answer, a timeout, a busy server or a limit: the same upload may succeed later. */
    Retryable,

    /** The server will not take this photo or voice note (its type or size, a failed processing, moderation): retrying cannot help. */
    Refused,
}

sealed interface UploadOutcome {
    val progress: UploadProgress

    /** Processed and attachable: send the entry with [mediaId]. */
    data class Ready(val mediaId: String, override val progress: UploadProgress) : UploadOutcome

    data class Failed(val failure: UploadFailure, override val progress: UploadProgress) : UploadOutcome
}

/**
 * The upload, in the order the backend requires (spec §4 D1, the Bruno requests):
 *
 * 1. `POST /media/upload-intent` with the kind, type, length and SHA-256;
 * 2. a `PUT` of the bytes **straight to storage**, with exactly the signed headers;
 * 3. `POST /media/{id}/complete` (an `Idempotency-Key`);
 * 4. `GET /media/{id}` until it is `READY`, because an entry can attach only a ready
 *    asset (`MediaAssetStore.claim`, backend read only): processing runs on the worker,
 *    after the completion, and takes a few seconds.
 *
 * It does not submit the entry; the composer does, with the id this returns.
 *
 * @param pollDelays the waits between status reads, in milliseconds; the upload gives up
 *   (retryably) after the last one. About a minute in all.
 */
class MediaUploader(
    private val repository: MediaRepository,
    private val newIdempotencyKey: () -> String,
    private val pollDelays: List<Long> = DEFAULT_POLL_DELAYS,
) {
    suspend fun send(media: MediaBytes, from: UploadProgress = UploadProgress.None): UploadOutcome {
        var progress = from
        if (progress.mediaId == null) {
            val ticket = when (val intent = repository.requestUpload(media)) {
                is Result.Success -> intent.data
                is Result.Failure -> return UploadOutcome.Failed(intent.error.uploadFailure(), UploadProgress.None)
            }
            // A failed PUT keeps nothing: the URL may have expired, so a retry asks for a new one.
            when (val put = repository.upload(ticket, media)) {
                is Result.Success -> Unit
                is Result.Failure -> return UploadOutcome.Failed(UploadFailure.Retryable, UploadProgress.None)
            }
            progress = UploadProgress(mediaId = ticket.mediaId)
        }
        val mediaId = checkNotNull(progress.mediaId)
        if (!progress.completed) {
            val key = progress.completeKey ?: newIdempotencyKey()
            progress = progress.copy(completeKey = key)
            when (val completion = repository.complete(mediaId, key)) {
                is Result.Success -> {
                    progress = progress.copy(completed = true)
                    finished(completion.data, progress)?.let { return it }
                }
                is Result.Failure -> {
                    // The asset is gone (reaped, or never ours), or storage does not hold the bytes
                    // (409 MEDIA_NOT_UPLOADED): the next try starts again from the bytes.
                    val restart = completion.error.isNotFound() || completion.error.hasCode(ApiErrorCode.MEDIA_NOT_UPLOADED)
                    return UploadOutcome.Failed(completion.error.uploadFailure(), if (restart) UploadProgress.None else progress)
                }
            }
        }
        for (wait in pollDelays) {
            delay(wait)
            when (val read = repository.status(mediaId)) {
                is Result.Success -> finished(read.data, progress)?.let { return it }
                is Result.Failure -> if (read.error.isNotFound()) {
                    return UploadOutcome.Failed(UploadFailure.Retryable, UploadProgress.None)
                }
            }
        }
        // Still processing: not refused, only slow. Try again resumes the wait.
        return UploadOutcome.Failed(UploadFailure.Retryable, progress)
    }

    /**
     * Deletes what [progress] uploaded, when it will not be sent after all (the photo
     * or voice note was removed or replaced, or the words discarded). Best effort: an asset never attached
     * is deleted by the server a day later anyway (`Retention.UNATTACHED`).
     */
    suspend fun discard(progress: UploadProgress) {
        progress.mediaId?.let { repository.delete(it) }
    }

    /** Null while it is still on its way; otherwise the end of the upload. */
    private fun finished(status: MediaStatus, progress: UploadProgress): UploadOutcome? = when (status) {
        MediaStatus.Ready -> UploadOutcome.Ready(checkNotNull(progress.mediaId), progress)
        MediaStatus.Quarantined, MediaStatus.Failed -> UploadOutcome.Failed(UploadFailure.Refused, UploadProgress.None)
        MediaStatus.Deleted -> UploadOutcome.Failed(UploadFailure.Retryable, UploadProgress.None)
        MediaStatus.PendingUpload, MediaStatus.Uploaded, MediaStatus.Processing -> null
    }

    companion object {
        /**
         * Quick at first (a photo is processed in a second or two, a minute's voice note transcoded
         * to Opus in about as long), then every two seconds; 64 s in all.
         */
        val DEFAULT_POLL_DELAYS: List<Long> = listOf(500L, 500L, 1_000L, 1_000L) + List(30) { 2_000L }
    }
}

/**
 * Which failures are the photo's or voice note's own. 413 (too large), 415 (a type it does not take) and
 * 422 (a declaration it refused) would be refused again for the same bytes; everything
 * else (no answer, 429, 5xx, a busy asset, an address not yet confirmed) may pass later.
 */
internal fun DataError.Network.uploadFailure(): UploadFailure = when (this) {
    is DataError.Network.Api -> when {
        problem.status == HTTP_PAYLOAD_TOO_LARGE || problem.status == HTTP_UNSUPPORTED_MEDIA_TYPE -> UploadFailure.Refused
        problem.code == ApiErrorCode.MEDIA_QUOTA_EXCEEDED || problem.code == ApiErrorCode.MEDIA_TYPE_UNSUPPORTED -> UploadFailure.Refused
        problem.status == HTTP_UNPROCESSABLE -> UploadFailure.Refused
        else -> UploadFailure.Retryable
    }
    else -> UploadFailure.Retryable
}

private fun DataError.Network.isNotFound(): Boolean = this is DataError.Network.Api && problem.status == HTTP_NOT_FOUND

private fun DataError.Network.hasCode(code: ApiErrorCode): Boolean = this is DataError.Network.Api && problem.code == code

private const val HTTP_NOT_FOUND = 404
private const val HTTP_PAYLOAD_TOO_LARGE = 413
private const val HTTP_UNSUPPORTED_MEDIA_TYPE = 415
private const val HTTP_UNPROCESSABLE = 422
