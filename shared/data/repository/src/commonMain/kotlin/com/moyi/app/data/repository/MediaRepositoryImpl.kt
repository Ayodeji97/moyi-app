package com.moyi.app.data.repository

import com.moyi.app.api.apis.MediaControllerApi
import com.moyi.app.api.models.MediaResponse
import com.moyi.app.api.models.UploadIntentRequest
import com.moyi.app.core.DataError
import com.moyi.app.core.EmptyResult
import com.moyi.app.core.Result
import com.moyi.app.data.network.ApiConfig
import com.moyi.app.data.network.AuthedHttpClient
import com.moyi.app.data.network.StorageHttpClient
import com.moyi.app.data.network.safeApiCall
import com.moyi.app.data.network.safeApiCallEmpty
import com.moyi.app.data.network.safeStorageCall
import com.moyi.app.domain.media.MediaBytes
import com.moyi.app.domain.media.MediaKind
import com.moyi.app.domain.media.MediaRepository
import com.moyi.app.domain.media.MediaStatus
import com.moyi.app.domain.media.UploadTicket
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.content.ByteArrayContent
import kotlin.io.encoding.Base64

/**
 * The media endpoints through the generated `MediaControllerApi`, and the one request
 * that is not the API's: the PUT of the bytes to the presigned URL, through
 * [StorageHttpClient] (no bearer token, no logging).
 *
 * **The PUT sends exactly the headers the intent names** (`requiredHeaders`: the
 * signature covers `Content-Type`, `Content-Length` and `x-amz-checksum-sha256`, read at
 * backend `R2ObjectStore.presignUpload`). Ktor refuses to set `Content-Type` and
 * `Content-Length` as plain headers, so those two travel the way Ktor sets them: the type
 * on the body, the length from the body's size, which is the declared size because it is
 * the same bytes. Every other required header is sent as given.
 */
class MediaRepositoryImpl(
    authedClient: AuthedHttpClient,
    private val storage: StorageHttpClient,
    config: ApiConfig,
) : MediaRepository {
    private val api = MediaControllerApi(config.origin, authedClient.client)

    override suspend fun requestUpload(media: MediaBytes): Result<UploadTicket, DataError.Network> {
        val request = UploadIntentRequest(
            checksum = Base64.encode(sha256(media.bytes)),
            contentType = media.contentType,
            kind = media.kind.wireName,
            sizeBytes = media.bytes.size.toLong(),
        )
        return safeApiCall { api.createMediaUploadIntent(request) }.mapOrSerialization { response ->
            UploadTicket(
                mediaId = response.mediaId ?: return@mapOrSerialization null,
                uploadUrl = response.uploadUrl ?: return@mapOrSerialization null,
                requiredHeaders = response.requiredHeaders.orEmpty(),
            )
        }
    }

    override suspend fun upload(ticket: UploadTicket, media: MediaBytes): EmptyResult<DataError.Network> {
        val signedType = ticket.requiredHeaders.valueOf(HttpHeaders.ContentType) ?: media.contentType
        return safeStorageCall {
            storage.client.put(ticket.uploadUrl) {
                ticket.requiredHeaders
                    .filterKeys { !it.equals(HttpHeaders.ContentType, ignoreCase = true) && !it.equals(HttpHeaders.ContentLength, ignoreCase = true) }
                    .forEach { (name, value) -> headers.append(name, value) }
                setBody(ByteArrayContent(media.bytes, ContentType.parse(signedType)))
            }
        }
    }

    override suspend fun complete(mediaId: String, idempotencyKey: String): Result<MediaStatus, DataError.Network> =
        safeApiCall { api.completeMediaUpload(mediaId, idempotencyKey) }.mapOrSerialization { it.status?.toDomain() }

    override suspend fun status(mediaId: String): Result<MediaStatus, DataError.Network> =
        safeApiCall { api.getMedia(mediaId) }.mapOrSerialization { it.status?.toDomain() }

    override suspend fun delete(mediaId: String): EmptyResult<DataError.Network> =
        safeApiCallEmpty { api.deleteMedia(mediaId) }
}

private val MediaKind.wireName: String
    get() = when (this) {
        MediaKind.Image -> "IMAGE"
        MediaKind.Voice -> "VOICE"
    }

private fun Map<String, String>.valueOf(name: String): String? = entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

private fun MediaResponse.Status.toDomain(): MediaStatus = when (this) {
    MediaResponse.Status.PENDING_UPLOAD -> MediaStatus.PendingUpload
    MediaResponse.Status.UPLOADED -> MediaStatus.Uploaded
    MediaResponse.Status.PROCESSING -> MediaStatus.Processing
    MediaResponse.Status.READY -> MediaStatus.Ready
    MediaResponse.Status.QUARANTINED -> MediaStatus.Quarantined
    MediaResponse.Status.FAILED -> MediaStatus.Failed
    MediaResponse.Status.DELETED -> MediaStatus.Deleted
}

/** SHA-256 of [bytes]: the platform's own (`MessageDigest`, CommonCrypto). */
internal expect fun sha256(bytes: ByteArray): ByteArray
