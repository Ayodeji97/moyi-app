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
import com.moyi.app.data.testing.respondJson
import com.moyi.app.data.testing.respondProblem
import com.moyi.app.data.testing.userJson
import com.moyi.app.domain.legal.AcceptedVersions
import com.moyi.app.domain.legal.LegalDocuments
import com.moyi.app.domain.legal.LegalStatus
import com.moyi.app.domain.legal.PublishedDocument
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [LegalRepositoryImpl] against a MockEngine, through the real clients: the paths and
 * methods the contract declares, `GET /meta/legal` sent with no token, the comparison
 * with `/me`'s `legalAccepted`, and failures kept as their typed errors.
 */
class LegalRepositoryTest {
    private class Fixture(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) {
        val requests = mutableListOf<HttpRequestData>()
        val engine = MockEngine { request ->
            requests += request
            handler(request)
        }
        val publicClient = PublicHttpClient(HttpClientFactory.createPublicClient(engine))
        val sessionManager = SessionManager(
            InMemoryTokenStore(log = EventLog()),
            RefreshControllerApi(ApiConfig("http://localhost:8080/api/v1").origin, publicClient.client),
            NoPersonalData,
        )
        val authed = AuthedHttpClient(HttpClientFactory.createAuthedClient(engine, sessionManager))
        val legal = LegalRepositoryImpl(publicClient, authed, ApiConfig("http://localhost:8080/api/v1"))
    }

    private val legalJson = """
        {"terms":{"version":"local-2","url":"http://localhost:3000/terms"},
         "privacy":{"version":"local-1","url":"http://localhost:3000/privacy"},
         "dataProcessingSummary":{"version":"local-1","url":"http://localhost:3000/data-processing"}}
    """.trimIndent()

    private val documents = LegalDocuments(
        terms = PublishedDocument("local-2", "http://localhost:3000/terms"),
        privacy = PublishedDocument("local-1", "http://localhost:3000/privacy"),
        dataProcessingSummary = PublishedDocument("local-1", "http://localhost:3000/data-processing"),
    )

    private fun profile(terms: String?, privacy: String?): String {
        fun accepted(version: String?) = version?.let { """{"version":"$it","acceptedAt":"2026-11-09T10:00:00Z"}""" } ?: "null"
        return userJson().dropLast(1) + ""","legalAccepted":{"terms":${accepted(terms)},"privacy":${accepted(privacy)}}}"""
    }

    @Test
    fun theDocumentsAreReadFromMetaLegalWithNoToken() = runTest {
        val fixture = Fixture { respondJson(legalJson) }

        assertEquals(Result.Success(documents), fixture.legal.documents())

        val request = fixture.requests.single()
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("/api/v1/meta/legal", request.url.encodedPath)
        assertNull(request.headers[HttpHeaders.Authorization], "a sent token is checked and may be refused: send none")
        assertNull(request.headers[HttpHeaders.IfNoneMatch])
    }

    @Test
    fun anOlderAcceptedTermsVersionIsNotUpToDate() = runTest {
        val fixture = Fixture { request ->
            if (request.url.encodedPath.endsWith("/meta/legal")) respondJson(legalJson) else respondJson(profile("local-1", "local-1"))
        }

        val result = fixture.legal.status()

        assertEquals(Result.Success(LegalStatus(documents, AcceptedVersions("local-1", "local-1"))), result)
        assertEquals(false, (result as Result.Success).data.upToDate)
        assertEquals(listOf("/api/v1/meta/legal", "/api/v1/me"), fixture.requests.map { it.url.encodedPath })
    }

    @Test
    fun theCurrentVersionsAcceptedAreUpToDate() = runTest {
        val fixture = Fixture { request ->
            if (request.url.encodedPath.endsWith("/meta/legal")) respondJson(legalJson) else respondJson(profile("local-2", "local-1"))
        }

        val result = fixture.legal.status()

        assertEquals(true, (result as Result.Success).data.upToDate)
    }

    @Test
    fun nothingEverAcceptedReadsAsNull() = runTest {
        val fixture = Fixture { request ->
            if (request.url.encodedPath.endsWith("/meta/legal")) respondJson(legalJson) else respondJson(profile(null, null))
        }

        assertEquals(Result.Success(LegalStatus(documents, AcceptedVersions(null, null))), fixture.legal.status())
    }

    @Test
    fun aFailedMetaLegalFailsTheStatusAndNeverAsksForTheProfile() = runTest {
        val fixture = Fixture { respondProblem(HttpStatusCode.TooManyRequests, "RATE_LIMITED") }

        val result = fixture.legal.status()

        assertEquals(Result.Failure(DataError.Network.Api(ApiProblem(429, ApiErrorCode.RATE_LIMITED))), result)
        assertEquals(1, fixture.requests.size)
    }

    @Test
    fun aBodyMissingADocumentIsSerialization() = runTest {
        val fixture = Fixture { respondJson("""{"terms":{"version":"local-2","url":"http://localhost:3000/terms"}}""") }

        assertEquals(Result.Failure(DataError.Network.Serialization), fixture.legal.documents())
    }

    @Test
    fun acceptingPostsToLegalConsentWithNoBody() = runTest {
        val fixture = Fixture { respond("", HttpStatusCode.NoContent) }

        assertEquals(Result.Success(Unit), fixture.legal.acceptCurrent())

        val request = fixture.requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/api/v1/me/legal-consent", request.url.encodedPath)
        assertEquals(0L, request.body.contentLength ?: 0L)
    }

    @Test
    fun aRefusedAcceptKeepsItsCode() = runTest {
        val fixture = Fixture { respondProblem(HttpStatusCode.Forbidden, "FORBIDDEN") }

        assertEquals(
            Result.Failure(DataError.Network.Api(ApiProblem(403, ApiErrorCode.FORBIDDEN))),
            fixture.legal.acceptCurrent(),
        )
    }
}
