package com.moyi.app.data.repository

import com.moyi.app.api.apis.RefreshControllerApi
import com.moyi.app.core.ApiErrorCode
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
import com.moyi.app.domain.archive.ArchiveDay
import com.moyi.app.domain.archive.RememberError
import com.moyi.app.domain.today.BondDayStatus
import com.moyi.app.domain.today.EntryStatus
import com.moyi.app.domain.today.MyEntry
import com.moyi.app.domain.today.PartnerEntry
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [RememberRepositoryImpl] against a MockEngine, through the real authed client: the path and
 * query the contract declares (`q`, `limit=10`), the days in the order the server ranked them,
 * a `429` read with its `Retry-After`, and every other failure as the one generic error.
 */
class RememberRepositoryTest {
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
        val remember = RememberRepositoryImpl(authed, ApiConfig("http://localhost:8080/api/v1"))

        val request: HttpRequestData get() = requests.single()
    }

    @Test
    fun aQuestionSendsQAndTenAndKeepsTheServersOrder() = runTest {
        // Most relevant first, which is not newest first: the order must survive the mapping.
        val fixture = Fixture {
            respondJson(
                answerJson(
                    dayJson("2026-10-12", "REVEALED", entryJson("e1", "REVEALED", "Mine."), entryJson("e2", "REVEALED", "Theirs.")),
                    dayJson("2026-11-04", "REVEALED", entryJson("e3", "REVEALED", "Soup."), entryJson("e4", "REVEALED", "Fever.")),
                ),
            )
        }

        val result = fixture.remember.remember("b1", "when I was unwell")

        assertEquals(
            Result.Success(
                listOf(
                    ArchiveDay("2026-10-12", BondDayStatus.REVEALED, MyEntry("e1", "Mine.", EntryStatus.REVEALED), PartnerEntry.Revealed("e2", "Theirs.")),
                    ArchiveDay("2026-11-04", BondDayStatus.REVEALED, MyEntry("e3", "Soup.", EntryStatus.REVEALED), PartnerEntry.Revealed("e4", "Fever.")),
                ),
            ),
            result,
        )
        assertEquals(HttpMethod.Get, fixture.request.method)
        assertEquals("/api/v1/bonds/b1/remember", fixture.request.url.encodedPath)
        assertEquals("when I was unwell", fixture.request.url.parameters["q"])
        assertEquals("10", fixture.request.url.parameters["limit"])
        assertEquals(null, fixture.request.url.parameters["cursor"], "one answer, not a feed")
    }

    @Test
    fun todayBeforeItOpensCarriesMyWordsAndNoneOfTheirs() = runTest {
        val fixture = Fixture {
            respondJson(answerJson(dayJson("2026-11-18", "PENDING_REVEAL", entryJson("e5", "SUBMITTED", "Slept well."), LOCKED)))
        }

        val day = (fixture.remember.remember("b1", "slept") as Result.Success).data.single()

        assertEquals(MyEntry("e5", "Slept well.", EntryStatus.SUBMITTED), day.myEntry)
        assertEquals(PartnerEntry.Locked, day.partnerEntry)
    }

    @Test
    fun anEmptyAnswerIsNoDaysNotAFailure() = runTest {
        val fixture = Fixture { respondJson(answerJson()) }
        assertEquals(Result.Success(emptyList()), fixture.remember.remember("b1", "Accra"))
    }

    @Test
    fun aRateLimitIsHeldForTheRetryAfterTheServerSent() = runTest {
        val fixture = Fixture { rateLimited(retryAfter = "37") }
        assertEquals(Result.Failure(RememberError.RateLimited(37)), fixture.remember.remember("b1", "picnic"))
    }

    @Test
    fun aRateLimitWithoutAReadableRetryAfterIsHeldForAMinute() = runTest {
        for (header in listOf(null, "Wed, 18 Nov 2026 10:00:00 GMT", "0", "soon")) {
            val fixture = Fixture { rateLimited(retryAfter = header) }
            assertEquals(Result.Failure(RememberError.RateLimited(60)), fixture.remember.remember("b1", "picnic"), "Retry-After: $header")
        }
    }

    @Test
    fun everyOtherRefusalIsTheGenericError() = runTest {
        // 403 (the contract's blanket one), 404 (not a member), 422 (the field should prevent it), 500.
        val refusals = listOf(
            HttpStatusCode.Forbidden to "FORBIDDEN",
            HttpStatusCode.NotFound to "NOT_FOUND",
            HttpStatusCode.UnprocessableEntity to "VALIDATION_FAILED",
            HttpStatusCode.InternalServerError to "INTERNAL_ERROR",
        )
        for ((status, code) in refusals) {
            val fixture = Fixture { respondProblem(status, code) }
            val result = fixture.remember.remember("b1", "picnic")
            val error = (result as Result.Failure).error
            assertEquals(true, error is RememberError.Failed, "$status is generic, was $error")
        }
    }

    @Test
    fun noConnectionIsTheGenericError() = runTest {
        val fixture = Fixture { throw IOException("offline") }
        assertEquals(Result.Failure(RememberError.Failed(DataError.Network.NoInternet)), fixture.remember.remember("b1", "picnic"))
    }

    @Test
    fun oneMalformedDayFailsTheWholeAnswer() = runTest {
        val fixture = Fixture { respondJson(answerJson(dayJson("2026-09-14", "REVEALED", "null", "null"), """{"status":"REVEALED"}""")) }
        assertEquals(Result.Failure(RememberError.Failed(DataError.Network.Serialization)), fixture.remember.remember("b1", "picnic"))
    }

    private fun MockRequestHandleScope.rateLimited(retryAfter: String?): HttpResponseData {
        val headers = buildList {
            add(HttpHeaders.ContentType to listOf("application/problem+json"))
            if (retryAfter != null) add(HttpHeaders.RetryAfter to listOf(retryAfter))
        }
        return respond(
            """{"type":"about:blank","title":"Too Many Requests","status":429,"code":"${ApiErrorCode.RATE_LIMITED.name}"}""",
            HttpStatusCode.TooManyRequests,
            headersOf(*headers.toTypedArray()),
        )
    }

    private fun answerJson(vararg days: String): String = """{"items":[${days.joinToString(",")}]}"""

    private fun dayJson(date: String, status: String, myEntry: String, partnerEntry: String): String =
        """{"date":"$date","status":"$status","myEntry":$myEntry,"partnerEntry":$partnerEntry}"""

    private fun entryJson(id: String, status: String, text: String?): String =
        """{"id":"$id","bondId":"b1","date":"2026-09-14","authorMemberId":"m1","text":${text?.let { "\"$it\"" } ?: "null"},""" +
            """"status":"$status","createdAt":"2026-09-14T19:12:44Z","intendedAt":"2026-09-14T19:12:44Z",""" +
            """"favourited":false,"reaction":null}"""

    private companion object {
        const val LOCKED = """{"authorMemberId":"m2","status":"LOCKED"}"""
    }
}
