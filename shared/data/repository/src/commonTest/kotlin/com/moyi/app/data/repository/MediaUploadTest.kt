package com.moyi.app.data.repository

import com.moyi.app.api.apis.RefreshControllerApi
import com.moyi.app.core.Result
import com.moyi.app.data.network.ApiConfig
import com.moyi.app.data.network.AuthedHttpClient
import com.moyi.app.data.network.HttpClientFactory
import com.moyi.app.data.network.PublicHttpClient
import com.moyi.app.data.network.StorageHttpClient
import com.moyi.app.data.session.SessionManager
import com.moyi.app.data.testing.InMemoryTokenStore
import com.moyi.app.data.testing.NoPersonalData
import com.moyi.app.data.testing.respondJson
import com.moyi.app.data.testing.respondProblem
import com.moyi.app.domain.media.MediaBytes
import com.moyi.app.domain.media.MediaKind
import com.moyi.app.domain.media.MediaUploader
import com.moyi.app.domain.media.UploadFailure
import com.moyi.app.domain.media.UploadOutcome
import com.moyi.app.domain.media.UploadProgress
import com.moyi.app.domain.today.EntryImage
import com.moyi.app.domain.today.EntryVoice
import com.moyi.app.domain.today.EntryMediaStatus
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The whole photo send against one MockEngine, through the real clients: the intent, the
 * PUT straight to storage with exactly the signed headers and no bearer token, the
 * completion, the wait for READY, and the entry sent with the photo's id. Then a failure
 * at each step, and what a retry does with what is kept.
 */
class MediaUploadTest {
    private val bytes = ByteArray(1_234) { (it % 251).toByte() }
    private val photo = MediaBytes(MediaKind.Image, "image/jpeg", bytes)

    /** The SHA-256 of [bytes], computed independently of the code under test (Python's hashlib). */
    private val expectedChecksum = "67Nd5bvu6/rZWThd+888ngAaPm2Sxx6OkmgaJYRrfSc="

    private class Fixture(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) {
        val requests = mutableListOf<HttpRequestData>()
        val engine = MockEngine { request ->
            requests += request
            handler(request)
        }
        private val config = ApiConfig("http://localhost:8080/api/v1")
        private val publicClient = PublicHttpClient(HttpClientFactory.createPublicClient(engine))
        private val sessionManager = SessionManager(
            InMemoryTokenStore(),
            RefreshControllerApi(config.origin, publicClient.client),
            NoPersonalData,
        )
        private val authed = AuthedHttpClient(HttpClientFactory.createAuthedClient(engine, sessionManager))
        val media = MediaRepositoryImpl(authed, StorageHttpClient(HttpClientFactory.createStorageClient(engine)), config)
        val today = TodayRepositoryImpl(authed, config)
        var keys = 0
        val uploader = MediaUploader(media, newIdempotencyKey = { "complete-${++keys}" }, pollDelays = listOf(1, 1, 1))

        fun paths() = requests.map { "${it.method.value} ${it.url.encodedPath}" }
    }

    /** What `GET /media/m1` answers, in turn; READY once they run out. One list per test. */
    private val processing = mutableListOf("PROCESSING", "READY")

    private fun MockRequestHandleScope.happy(request: HttpRequestData, statuses: MutableList<String> = processing): HttpResponseData {
        val path = request.url.encodedPath
        return when {
            path == "/api/v1/media/upload-intent" -> respondJson(INTENT, HttpStatusCode.Created)
            request.url.host == "storage.test" -> respond("", HttpStatusCode.OK)
            path == "/api/v1/media/m1/complete" -> respondJson(media("UPLOADED"))
            path == "/api/v1/media/m1" -> respondJson(media(statuses.removeFirstOrNull() ?: "READY"))
            path == "/api/v1/bonds/b1/entries" -> respondJson(ENTRY_WITH_PHOTO, HttpStatusCode.Created)
            else -> error("unexpected $path")
        }
    }

    @Test
    fun aPhotoGoesIntentThenStorageThenCompleteThenWaitsForReadyThenTheEntryCarriesItsId() = runTest {
        val fixture = Fixture { happy(it) }

        val outcome = fixture.uploader.send(photo)
        val ready = assertIs<UploadOutcome.Ready>(outcome)
        assertEquals("m1", ready.mediaId)
        val entry = fixture.today.submitEntry("b1", "You made the tea.", null, "entry-key", imageMediaId = ready.mediaId)

        assertEquals(
            listOf(
                "POST /api/v1/media/upload-intent",
                "PUT /moyi-media/uploads/k1",
                "POST /api/v1/media/m1/complete",
                "GET /api/v1/media/m1",
                "GET /api/v1/media/m1",
                "POST /api/v1/bonds/b1/entries",
            ),
            fixture.paths(),
        )
        // The intent declares exactly the bytes about to be sent.
        val intent = Json.parseToJsonElement(assertIs<TextContent>(fixture.requests[0].body).text).jsonObject
        assertEquals("IMAGE", intent["kind"]?.jsonPrimitive?.content)
        assertEquals("image/jpeg", intent["contentType"]?.jsonPrimitive?.content)
        assertEquals("1234", intent["sizeBytes"]?.jsonPrimitive?.content)
        assertEquals(expectedChecksum, intent["checksum"]?.jsonPrimitive?.content)
        // The completion carries an Idempotency-Key.
        assertEquals("complete-1", fixture.requests[2].headers["Idempotency-Key"])
        // The entry names the photo.
        val submitted = Json.parseToJsonElement(assertIs<TextContent>(fixture.requests[5].body).text).jsonObject
        assertEquals("m1", submitted["imageMediaId"]?.jsonPrimitive?.content)
        val mine = assertIs<Result.Success<com.moyi.app.domain.today.MyEntry>>(entry).data
        assertEquals(EntryImage("m1", EntryMediaStatus.Ready, "http://storage.test/display", 1280, 960, sensitive = false), mine.image)
    }

    @Test
    fun aVoiceNoteGoesTheSameWayAsAudioAndTheEntryCarriesItsVoiceMediaId() = runTest {
        val voice = MediaBytes(MediaKind.Voice, "audio/mp4", bytes)
        val fixture = Fixture { request ->
            when (request.url.encodedPath) {
                "/api/v1/media/upload-intent" -> respondJson(INTENT.replace("image/jpeg", "audio/mp4"), HttpStatusCode.Created)
                "/api/v1/bonds/b1/entries" -> respondJson(ENTRY_WITH_VOICE, HttpStatusCode.Created)
                else -> happy(request)
            }
        }

        val ready = assertIs<UploadOutcome.Ready>(fixture.uploader.send(voice))
        val entry = fixture.today.submitEntry("b1", "You sang.", null, "entry-key", voiceMediaId = ready.mediaId)

        val intent = Json.parseToJsonElement(assertIs<TextContent>(fixture.requests[0].body).text).jsonObject
        assertEquals("VOICE", intent["kind"]?.jsonPrimitive?.content)
        assertEquals("audio/mp4", intent["contentType"]?.jsonPrimitive?.content)
        assertEquals(expectedChecksum, intent["checksum"]?.jsonPrimitive?.content)
        // Straight to storage, typed as signed, with no bearer token.
        val put = fixture.requests.single { it.method == HttpMethod.Put }
        assertEquals("audio/mp4", assertIs<OutgoingContent.ByteArrayContent>(put.body).contentType.toString())
        assertNull(put.headers[HttpHeaders.Authorization])
        // The entry names the voice note, and no photo.
        val submitted = Json.parseToJsonElement(assertIs<TextContent>(fixture.requests.last().body).text).jsonObject
        assertEquals("m1", submitted["voiceMediaId"]?.jsonPrimitive?.content)
        assertNull(submitted["imageMediaId"]?.jsonPrimitive?.contentOrNull)
        val mine = assertIs<Result.Success<com.moyi.app.domain.today.MyEntry>>(entry).data
        assertEquals(EntryVoice("m1", EntryMediaStatus.Ready, "http://storage.test/voice.ogg", 42_000, sensitive = false), mine.voice)
    }

    @Test
    fun theStoragePutSendsExactlyTheSignedHeadersTheBytesAndNoBearerToken() = runTest {
        val fixture = Fixture { happy(it) }

        fixture.uploader.send(photo)

        val put = fixture.requests.single { it.method == HttpMethod.Put }
        // The URL exactly as issued: its query is the signature.
        assertEquals(UPLOAD_URL, put.url.toString())
        assertEquals(expectedChecksum, put.headers["x-amz-checksum-sha256"])
        assertNull(put.headers[HttpHeaders.Authorization], "a bearer token must never reach the storage")
        val body = assertIs<OutgoingContent.ByteArrayContent>(put.body)
        assertEquals("image/jpeg", body.contentType.toString())
        assertEquals(bytes.size.toLong(), body.contentLength)
        assertContentEquals(bytes, body.bytes())
    }

    @Test
    fun aRefusedIntentStopsBeforeStorageAndKeepsNothing() = runTest {
        val fixture = Fixture { request ->
            if (request.url.encodedPath == "/api/v1/media/upload-intent") respondProblem(HttpStatusCode.ServiceUnavailable, "MEDIA_STORAGE_UNAVAILABLE") else happy(request)
        }

        val outcome = assertIs<UploadOutcome.Failed>(fixture.uploader.send(photo))

        assertEquals(UploadFailure.Retryable, outcome.failure)
        assertEquals(UploadProgress.None, outcome.progress)
        assertEquals(listOf("POST /api/v1/media/upload-intent"), fixture.paths())
    }

    @Test
    fun aPhotoTooLargeOrOfAnUnknownTypeIsRefusedNotRetried() = runTest {
        val fixture = Fixture { respondProblem(HttpStatusCode.PayloadTooLarge, "MEDIA_QUOTA_EXCEEDED") }

        assertEquals(UploadFailure.Refused, assertIs<UploadOutcome.Failed>(fixture.uploader.send(photo)).failure)
    }

    @Test
    fun aFailedPutKeepsNothingSoTheRetryAsksForAFreshUrl() = runTest {
        var storageDown = true
        val fixture = Fixture { request ->
            if (request.url.host == "storage.test" && storageDown) respondError(HttpStatusCode.Forbidden) else happy(request)
        }

        val first = assertIs<UploadOutcome.Failed>(fixture.uploader.send(photo))
        assertEquals(UploadFailure.Retryable, first.failure)
        assertEquals(UploadProgress.None, first.progress)

        storageDown = false
        assertIs<UploadOutcome.Ready>(fixture.uploader.send(photo, first.progress))
        assertEquals(2, fixture.requests.count { it.url.encodedPath == "/api/v1/media/upload-intent" })
    }

    @Test
    fun aLostCompletionIsRetriedWithTheSameKeyAndTheBytesAreNotSentAgain() = runTest {
        var offline = true
        val fixture = Fixture { request ->
            if (request.url.encodedPath.endsWith("/complete") && offline) throw IOException("offline") else happy(request)
        }

        val first = assertIs<UploadOutcome.Failed>(fixture.uploader.send(photo))
        assertEquals(UploadFailure.Retryable, first.failure)
        assertEquals(UploadProgress(mediaId = "m1", completeKey = "complete-1"), first.progress)

        offline = false
        assertIs<UploadOutcome.Ready>(fixture.uploader.send(photo, first.progress))
        assertEquals(1, fixture.requests.count { it.method == HttpMethod.Put }, "the bytes went to storage once")
        val completions = fixture.requests.filter { it.url.encodedPath.endsWith("/complete") }
        assertEquals(listOf("complete-1", "complete-1"), completions.map { it.headers["Idempotency-Key"] })
    }

    @Test
    fun aCompletionTheStorageDoesNotBackStartsAgainFromTheBytes() = runTest {
        val fixture = Fixture { request ->
            if (request.url.encodedPath.endsWith("/complete")) respondProblem(HttpStatusCode.Conflict, "MEDIA_NOT_UPLOADED") else happy(request)
        }

        assertEquals(UploadProgress.None, assertIs<UploadOutcome.Failed>(fixture.uploader.send(photo)).progress)
    }

    @Test
    fun aPhotoStillProcessingAfterTheWaitIsRetryableAndTheRetryOnlyWaits() = runTest {
        val statuses = mutableListOf("PROCESSING", "PROCESSING", "PROCESSING", "READY")
        val fixture = Fixture { happy(it, statuses) }

        val first = assertIs<UploadOutcome.Failed>(fixture.uploader.send(photo))
        assertEquals(UploadFailure.Retryable, first.failure)
        assertEquals(UploadProgress(mediaId = "m1", completeKey = "complete-1", completed = true), first.progress)

        assertIs<UploadOutcome.Ready>(fixture.uploader.send(photo, first.progress))
        assertEquals(1, fixture.requests.count { it.url.encodedPath.endsWith("/complete") }, "completed once")
        assertEquals(1, fixture.requests.count { it.method == HttpMethod.Put })
    }

    @Test
    fun aPhotoTheServerCouldNotProcessOrHeldBackIsRefused() = runTest {
        for (end in listOf("FAILED", "QUARANTINED")) {
            val fixture = Fixture { happy(it, mutableListOf(end)) }
            val outcome = assertIs<UploadOutcome.Failed>(fixture.uploader.send(photo))
            assertEquals(UploadFailure.Refused, outcome.failure, end)
            assertEquals(UploadProgress.None, outcome.progress)
        }
    }

    @Test
    fun discardingAnUploadDeletesIt() = runTest {
        val fixture = Fixture { request ->
            if (request.method == HttpMethod.Delete) respond("", HttpStatusCode.NoContent) else happy(request)
        }
        val ready = assertIs<UploadOutcome.Ready>(fixture.uploader.send(photo))

        fixture.uploader.discard(ready.progress)

        assertTrue("DELETE /api/v1/media/m1" in fixture.paths())
    }

    private fun media(status: String) =
        """{"id":"m1","kind":"IMAGE","status":"$status","contentType":"image/jpeg","sizeBytes":1234,"sensitive":false}"""

    private companion object {
        const val UPLOAD_URL =
            "http://storage.test/moyi-media/uploads/k1?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential=moyi%2F20261108%2Fauto%2Fs3%2Faws4_request" +
                "&X-Amz-SignedHeaders=content-length%3Bcontent-type%3Bhost%3Bx-amz-checksum-sha256&X-Amz-Signature=abc123"

        val INTENT =
            """{"mediaId":"m1","uploadUrl":"$UPLOAD_URL","expiresAt":"2026-11-08T20:00:00Z",""" +
                """"requiredHeaders":{"Content-Type":"image/jpeg","Content-Length":"1234","x-amz-checksum-sha256":"67Nd5bvu6/rZWThd+888ngAaPm2Sxx6OkmgaJYRrfSc="}}"""

        const val ENTRY_WITH_VOICE =
            """{"id":"e1","bondId":"b1","date":"2026-09-14","authorMemberId":"m1","text":"You sang.",""" +
                """"status":"SUBMITTED","createdAt":"2026-09-14T19:12:44Z","intendedAt":"2026-09-14T19:12:44Z",""" +
                """"favourited":false,"reaction":null,"image":null,""" +
                """"voice":{"id":"m1","status":"READY","url":"http://storage.test/voice.ogg","durationMs":42000,"sensitive":false}}"""

        const val ENTRY_WITH_PHOTO =
            """{"id":"e1","bondId":"b1","date":"2026-09-14","authorMemberId":"m1","text":"You made the tea.",""" +
                """"status":"SUBMITTED","createdAt":"2026-09-14T19:12:44Z","intendedAt":"2026-09-14T19:12:44Z",""" +
                """"favourited":false,"reaction":null,"voice":null,""" +
                """"image":{"id":"m1","status":"READY","url":"http://storage.test/display","width":1280,"height":960,"sensitive":false}}"""
    }
}
