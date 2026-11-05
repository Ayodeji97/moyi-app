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
import com.moyi.app.data.testing.respondJson
import com.moyi.app.data.testing.respondProblem
import com.moyi.app.domain.archive.ArchiveDay
import com.moyi.app.domain.archive.ArchivePage
import com.moyi.app.domain.today.BondDayStatus
import com.moyi.app.domain.today.EntryStatus
import com.moyi.app.domain.today.MyEntry
import com.moyi.app.domain.today.PartnerEntry
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [ArchiveRepositoryImpl] against a MockEngine, through the real authed client: the
 * paths and query the contract declares, the cursor passed back unchanged, days drawn
 * from their entries (a tombstone on an EMPTY day), and the partner's `oneOf` decoded
 * through PartnerEntryWire exactly as on Today.
 */
class ArchiveRepositoryTest {
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
        )
        val authed = AuthedHttpClient(HttpClientFactory.createAuthedClient(engine, sessionManager))
        val archive = ArchiveRepositoryImpl(authed, ApiConfig("http://localhost:8080/api/v1"))

        val request: HttpRequestData get() = requests.single()
    }

    @Test
    fun theFirstPageAsksWithNoCursorAndReadsEveryShape() = runTest {
        val fixture = Fixture {
            respondJson(
                pageJson(
                    "HC1",
                    dayJson("2026-09-14", "REVEALED", entryJson("e1", "REVEALED", "Mine."), entryJson("e2", "REVEALED", "Theirs.")),
                    dayJson("2026-09-13", "PENDING_REVEAL", entryJson("e3", "SUBMITTED", "Waiting."), LOCKED),
                    dayJson("2026-09-12", "EMPTY", entryJson("e4", "DELETED", null), "null"),
                ),
            )
        }

        val result = fixture.archive.days("b1")

        assertEquals(
            Result.Success(
                ArchivePage(
                    days = listOf(
                        ArchiveDay("2026-09-14", BondDayStatus.REVEALED, MyEntry("e1", "Mine.", EntryStatus.REVEALED), PartnerEntry.Revealed("e2", "Theirs.")),
                        ArchiveDay("2026-09-13", BondDayStatus.PENDING_REVEAL, MyEntry("e3", "Waiting.", EntryStatus.SUBMITTED), PartnerEntry.Locked),
                        // Status EMPTY, yet there is something to draw: my tombstone.
                        ArchiveDay("2026-09-12", BondDayStatus.EMPTY, MyEntry("e4", null, EntryStatus.DELETED), null),
                    ),
                    nextCursor = "HC1",
                ),
            ),
            result,
        )
        assertEquals(HttpMethod.Get, fixture.request.method)
        assertEquals("/api/v1/bonds/b1/days", fixture.request.url.encodedPath)
        assertNull(fixture.request.url.parameters["cursor"])
        assertEquals("20", fixture.request.url.parameters["limit"])
    }

    @Test
    fun theNextPagePassesTheCursorBackUnchangedAndANullCursorIsTheEnd() = runTest {
        val fixture = Fixture { respondJson("""{"items":[],"nextCursor":null}""") }

        val result = fixture.archive.days("b1", cursor = "Hk+/=1")

        assertEquals(Result.Success(ArchivePage(emptyList(), null)), result)
        assertEquals("Hk+/=1", fixture.request.url.parameters["cursor"])
    }

    @Test
    fun oneMalformedDayFailsThePageRatherThanSkippingIt() = runTest {
        val fixture = Fixture { respondJson(pageJson("HC1", dayJson("2026-09-14", "REVEALED", "null", "null"), """{"status":"REVEALED"}""")) }
        assertEquals(Result.Failure(DataError.Network.Serialization), fixture.archive.days("b1"))
    }

    @Test
    fun anUnknownPartnerShapeIsABodyThatDidNotParse() = runTest {
        val fixture = Fixture { respondJson(pageJson(null, dayJson("2026-09-14", "REVEALED", "null", """{"status":"SEALED"}"""))) }
        assertEquals(Result.Failure(DataError.Network.Serialization), fixture.archive.days("b1"))
    }

    @Test
    fun oneDayIsReadByItsDate() = runTest {
        val fixture = Fixture { respondJson(dayJson("2026-09-14", "SOLO", "null", entryJson("e2", "SUBMITTED", "Alone."))) }

        val result = fixture.archive.day("b1", "2026-09-14")

        assertEquals(Result.Success(ArchiveDay("2026-09-14", BondDayStatus.SOLO, null, PartnerEntry.Submitted("e2", "Alone."))), result)
        assertEquals("/api/v1/bonds/b1/days/2026-09-14", fixture.request.url.encodedPath)
    }

    @Test
    fun aDayNotInTheArchiveIsTheNotFound() = runTest {
        val fixture = Fixture { respondProblem(HttpStatusCode.NotFound, "DAY_NOT_FOUND") }
        val result = fixture.archive.day("b1", "2026-09-14")
        assertTrue(result is Result.Failure && (result.error as DataError.Network.Api).problem.status == 404)
    }

    @Test
    fun aValueThatIsNotADateIsTheNotFoundWithoutACall() = runTest {
        val fixture = Fixture { error("no request expected") }
        assertEquals(
            Result.Failure(DataError.Network.Api(ApiProblem(404, ApiErrorCode.NOT_FOUND))),
            fixture.archive.day("b1", "yesterday"),
        )
        assertTrue(fixture.requests.isEmpty())
    }

    private fun pageJson(nextCursor: String?, vararg days: String): String =
        """{"items":[${days.joinToString(",")}],"nextCursor":${nextCursor?.let { "\"$it\"" } ?: "null"}}"""

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
