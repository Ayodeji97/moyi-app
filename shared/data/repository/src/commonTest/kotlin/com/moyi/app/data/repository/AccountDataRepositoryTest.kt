package com.moyi.app.data.repository

import com.moyi.app.api.apis.RefreshControllerApi
import com.moyi.app.core.ApiErrorCode
import com.moyi.app.core.ApiProblem
import com.moyi.app.core.DataError
import com.moyi.app.core.Result
import com.moyi.app.data.network.ApiConfig
import com.moyi.app.data.network.AuthedHttpClient
import com.moyi.app.data.network.HttpClientFactory
import com.moyi.app.data.network.PublicHttpClient
import com.moyi.app.data.session.SessionManager
import com.moyi.app.data.testing.EventLog
import com.moyi.app.data.testing.InMemoryTokenStore
import com.moyi.app.data.testing.NoPersonalData
import com.moyi.app.data.testing.profileJson
import com.moyi.app.data.testing.respondJson
import com.moyi.app.data.testing.respondProblem
import com.moyi.app.domain.account.DataExport
import com.moyi.app.domain.account.DataExportStatus
import com.moyi.app.domain.account.DeletionRequest
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.time.Instant

/**
 * [DataExportRepositoryImpl] and [AccountDeletionRepositoryImpl] against a MockEngine, through
 * the real authed client: the paths and methods the contract declares, each export status, a
 * never-asked export as null, the password in the body, and the pending deletion read from `/me`.
 */
class AccountDataRepositoryTest {
    private class Fixture(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) {
        val requests = mutableListOf<HttpRequestData>()
        val engine = MockEngine { request ->
            requests += request
            handler(request)
        }
        private val config = ApiConfig("http://localhost:8080/api/v1")
        val publicClient = PublicHttpClient(HttpClientFactory.createPublicClient(engine))
        val sessionManager = SessionManager(
            InMemoryTokenStore(log = EventLog()),
            RefreshControllerApi(config.origin, publicClient.client),
            NoPersonalData,
        )
        val authed = AuthedHttpClient(HttpClientFactory.createAuthedClient(engine, sessionManager))
        val exports = DataExportRepositoryImpl(authed, config)
        val deletion = AccountDeletionRepositoryImpl(authed, config)

        val request: HttpRequestData get() = requests.single()
    }

    private val readyJson =
        """{"id":"e1","requestedAt":"2026-11-09T10:00:00Z","status":"READY","readyAt":"2026-11-09T10:03:00Z",
           "expiresAt":"2026-11-10T10:03:00Z","sizeBytes":2048000,"downloadUrl":"https://storage.test/e1.zip?X-Sig=secret"}"""

    @Test
    fun aReadyExportIsReadWithItsUrlAndTheUrlStaysOutOfToString() = runTest {
        val fixture = Fixture { respondJson(readyJson) }

        val export = assertIs<Result.Success<DataExport?>>(fixture.exports.latest()).data

        assertEquals(HttpMethod.Get, fixture.request.method)
        assertEquals("/api/v1/me/export", fixture.request.url.encodedPath)
        assertEquals(
            DataExport(
                id = "e1",
                status = DataExportStatus.READY,
                requestedAt = Instant.parse("2026-11-09T10:00:00Z"),
                readyAt = Instant.parse("2026-11-09T10:03:00Z"),
                expiresAt = Instant.parse("2026-11-10T10:03:00Z"),
                sizeBytes = 2_048_000,
                downloadUrl = "https://storage.test/e1.zip?X-Sig=secret",
            ),
            export,
        )
        assertFalse("secret" in export.toString(), export.toString())
    }

    @Test
    fun neverHavingAskedIsNullNotAFailure() = runTest {
        val fixture = Fixture { respondProblem(HttpStatusCode.NotFound, "NOT_FOUND") }

        assertEquals(Result.Success(null), fixture.exports.latest())
    }

    @Test
    fun askingPostsAndReadsPreparing() = runTest {
        val fixture = Fixture {
            respondJson(
                """{"id":"e2","requestedAt":"2026-11-09T10:00:00Z","status":"PREPARING","readyAt":null,"expiresAt":null,"sizeBytes":null,"downloadUrl":null}""",
                HttpStatusCode.Accepted,
            )
        }

        val export = assertIs<Result.Success<DataExport>>(fixture.exports.request()).data

        assertEquals(HttpMethod.Post, fixture.request.method)
        assertEquals("/api/v1/me/export", fixture.request.url.encodedPath)
        assertEquals(DataExportStatus.PREPARING, export.status)
        assertEquals(null, export.downloadUrl)
    }

    @Test
    fun oneExportIsReadByIdAndARateLimitIsAFailure() = runTest {
        val fixture = Fixture { request ->
            if (request.method == HttpMethod.Get) {
                respondJson("""{"id":"e3","requestedAt":"2026-11-09T10:00:00Z","status":"FAILED","readyAt":null,"expiresAt":null,"sizeBytes":null,"downloadUrl":null}""")
            } else {
                respondProblem(HttpStatusCode.TooManyRequests, "RATE_LIMITED")
            }
        }

        assertEquals(DataExportStatus.FAILED, assertIs<Result.Success<DataExport>>(fixture.exports.export("e3")).data.status)
        assertEquals("/api/v1/me/export/e3", fixture.requests.last().url.encodedPath)
        assertEquals(
            Result.Failure(DataError.Network.Api(ApiProblem(429, ApiErrorCode.RATE_LIMITED))),
            fixture.exports.request(),
        )
    }

    @Test
    fun theDeletionSendsThePasswordAndReadsTheDates() = runTest {
        val fixture = Fixture {
            respondJson(
                """{"requestedAt":"2026-11-09T10:00:00Z","cancellableUntil":"2026-11-23T10:00:00Z","deleteBy":"2026-12-09T10:00:00Z"}""",
                HttpStatusCode.Accepted,
            )
        }

        val result = fixture.deletion.request("correct horse")

        assertEquals(HttpMethod.Post, fixture.request.method)
        assertEquals("/api/v1/me/deletion-request", fixture.request.url.encodedPath)
        assertEquals("""{"password":"correct horse"}""", assertIs<TextContent>(fixture.request.body).text)
        assertEquals(
            Result.Success(
                DeletionRequest(
                    requestedAt = Instant.parse("2026-11-09T10:00:00Z"),
                    cancellableUntil = Instant.parse("2026-11-23T10:00:00Z"),
                    deleteBy = Instant.parse("2026-12-09T10:00:00Z"),
                ),
            ),
            result,
        )
    }

    @Test
    fun thePendingDeletionIsReadFromMe() = runTest {
        val pending = profileJson().replace(
            "\"deletionRequest\":null",
            """"deletionRequest":{"requestedAt":"2026-11-09T10:00:00Z","cancellableUntil":"2026-11-23T10:00:00Z","deleteBy":"2026-12-09T10:00:00Z"}""",
        )
        val none = Fixture { respondJson(profileJson()) }
        val some = Fixture { respondJson(pending) }

        assertEquals(Result.Success(null), none.deletion.pending())
        assertEquals(
            Instant.parse("2026-11-23T10:00:00Z"),
            assertIs<Result.Success<DeletionRequest?>>(some.deletion.pending()).data?.cancellableUntil,
        )
        assertEquals("/api/v1/me", some.request.url.encodedPath)
    }

    @Test
    fun cancellingIsADelete() = runTest {
        val fixture = Fixture { respond("", HttpStatusCode.NoContent) }

        assertEquals(Result.Success(Unit), fixture.deletion.cancel())
        assertEquals(HttpMethod.Delete, fixture.request.method)
        assertEquals("/api/v1/me/deletion-request", fixture.request.url.encodedPath)
    }
}
