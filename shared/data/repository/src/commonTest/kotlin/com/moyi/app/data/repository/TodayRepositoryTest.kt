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
import com.moyi.app.domain.today.BondDayStatus
import com.moyi.app.domain.today.EntryStatus
import com.moyi.app.domain.today.MyEntry
import com.moyi.app.domain.today.PartnerEntry
import com.moyi.app.domain.today.Prompt
import com.moyi.app.domain.today.Today
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * [TodayRepositoryImpl] against a MockEngine, through the real authed client:
 * the paths, headers and bodies the contract and the Bruno requests (08 to 11)
 * use, the partner entry's `oneOf` decoded through the generated models, and
 * refusals read by code.
 */
class TodayRepositoryTest {
    private class Fixture(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) {
        val log = EventLog()
        val store = InMemoryTokenStore(log = log)
        val requests = mutableListOf<HttpRequestData>()
        val engine = MockEngine { request ->
            requests += request
            handler(request)
        }
        val publicClient = PublicHttpClient(HttpClientFactory.createPublicClient(engine))
        val sessionManager = SessionManager(store, RefreshControllerApi(ApiConfig("http://localhost:8080/api/v1").origin, publicClient.client), NoPersonalData)
        val authed = AuthedHttpClient(HttpClientFactory.createAuthedClient(engine, sessionManager))
        val today = TodayRepositoryImpl(authed, ApiConfig("http://localhost:8080/api/v1"))

        val request: HttpRequestData get() = requests.single()

        fun sentJson() = Json.parseToJsonElement(assertIs<TextContent>(request.body).text).jsonObject
    }

    @Test
    fun todayReadsTheDayAndALockedPartnerEntryCarriesNothing() = runTest {
        val fixture = Fixture {
            respondJson(todayJson(status = "PARTIAL", myEntry = entryJson("e1", "SUBMITTED", "Thank you."), partnerEntry = LOCKED))
        }

        val result = fixture.today.today("b1")

        assertEquals(
            Result.Success(
                Today(
                    date = "2026-09-14",
                    status = BondDayStatus.PARTIAL,
                    myEntry = MyEntry("e1", "Thank you.", EntryStatus.SUBMITTED),
                    partnerEntry = PartnerEntry.Locked,
                    prompt = Prompt("p1", "What did Tunde do today that you noticed?"),
                ),
            ),
            result,
        )
        assertEquals(HttpMethod.Get, fixture.request.method)
        assertEquals("/api/v1/bonds/b1/today", fixture.request.url.encodedPath)
    }

    @Test
    fun aRevealedPartnerEntryCarriesItsWords() = runTest {
        val fixture = Fixture {
            respondJson(
                todayJson(
                    status = "REVEALED",
                    myEntry = entryJson("e1", "REVEALED", "Mine."),
                    partnerEntry = entryJson("e2", "REVEALED", "Theirs."),
                ),
            )
        }
        val today = assertIs<Result.Success<Today>>(fixture.today.today("b1")).data
        assertEquals(PartnerEntry.Revealed("e2", "Theirs."), today.partnerEntry)
    }

    @Test
    fun everyPartnerShapeTheContractNamesDecodes() = runTest {
        val shapes = mapOf(
            entryJson("e2", "SUBMITTED", "Solo words.") to PartnerEntry.Submitted("e2", "Solo words."),
            entryJson("e2", "DELETED", null) to PartnerEntry.Deleted("e2"),
            LOCKED to PartnerEntry.Locked,
            "null" to null,
        )
        for ((json, expected) in shapes) {
            val fixture = Fixture { respondJson(todayJson(status = "SOLO", partnerEntry = json)) }
            val today = assertIs<Result.Success<Today>>(fixture.today.today("b1"), json).data
            assertEquals(expected, today.partnerEntry, json)
        }
    }

    @Test
    fun removedIsNoLongerAShapeTheContractNames() = runTest {
        // Backend #101: an entry erased before it was seen arrives as null, never REMOVED.
        val fixture = Fixture { respondJson(todayJson(partnerEntry = """{"authorMemberId":"m2","status":"REMOVED"}""")) }
        assertEquals(Result.Failure(DataError.Network.Serialization), fixture.today.today("b1"))
    }

    @Test
    fun aPartnerStatusThisBuildDoesNotKnowIsASerializationErrorNeverAGuess() = runTest {
        val fixture = Fixture { respondJson(todayJson(partnerEntry = """{"authorMemberId":"m2","status":"HIDDEN"}""")) }
        assertEquals(Result.Failure(DataError.Network.Serialization), fixture.today.today("b1"))
    }

    @Test
    fun aDayWithoutItsStatusIsASerializationError() = runTest {
        val fixture = Fixture { respondJson("""{"bondDay":{"date":"2026-09-14"},"myEntry":null,"partnerEntry":null,"prompt":null}""") }
        assertEquals(Result.Failure(DataError.Network.Serialization), fixture.today.today("b1"))
    }

    @Test
    fun aRevealedEntryWithoutItsWordsIsASerializationError() = runTest {
        val fixture = Fixture { respondJson(todayJson(status = "REVEALED", partnerEntry = entryJson("e2", "REVEALED", null))) }
        assertEquals(Result.Failure(DataError.Network.Serialization), fixture.today.today("b1"))
    }

    @Test
    fun noPromptIsNoPrompt() = runTest {
        val fixture = Fixture { respondJson(todayJson(prompt = "null")) }
        assertNull(assertIs<Result.Success<Today>>(fixture.today.today("b1")).data.prompt)
    }

    @Test
    fun submitPostsTheWordsThePromptAndTheCallersIdempotencyKey() = runTest {
        val fixture = Fixture { respondJson(entryJson("e1", "SUBMITTED", "You made the tea."), HttpStatusCode.Created) }

        val result = fixture.today.submitEntry("b1", "You made the tea.", promptId = "p1", idempotencyKey = "k-1")

        assertEquals(Result.Success(MyEntry("e1", "You made the tea.", EntryStatus.SUBMITTED)), result)
        assertEquals(HttpMethod.Post, fixture.request.method)
        assertEquals("/api/v1/bonds/b1/entries", fixture.request.url.encodedPath)
        assertEquals("k-1", fixture.request.headers["Idempotency-Key"])
        val sent = fixture.sentJson()
        assertEquals("You made the tea.", sent["text"]?.jsonPrimitive?.content)
        assertEquals("p1", sent["promptId"]?.jsonPrimitive?.content)
    }

    @Test
    fun intendedAtIsSentAsTheInstantOfTheTapToTheMillisecond() = runTest {
        val fixture = Fixture { respondJson(entryJson("e1", "SUBMITTED", "Words."), HttpStatusCode.Created) }
        fixture.today.submitEntry("b1", "Words.", null, "k-1", intendedAt = kotlin.time.Instant.parse("2026-09-14T10:59:30.123Z"))
        assertEquals("2026-09-14T10:59:30.123Z", fixture.sentJson()["intendedAt"]?.jsonPrimitive?.content)
    }

    @Test
    fun aDismissedPromptSendsNoPromptIdAtAll() = runTest {
        val fixture = Fixture { respondJson(entryJson("e1", "SUBMITTED", "Words."), HttpStatusCode.Created) }
        fixture.today.submitEntry("b1", "Words.", promptId = null, idempotencyKey = "k-1")
        assertEquals(setOf("text"), fixture.sentJson().keys, "no promptId, no intendedAt, no media ids")
    }

    @Test
    fun oneEntryADayIsTheCode() = runTest {
        val fixture = Fixture { respondProblem(HttpStatusCode.Conflict, "ENTRY_ALREADY_EXISTS") }
        assertEquals(
            Result.Failure(DataError.Network.Api(ApiProblem(409, ApiErrorCode.ENTRY_ALREADY_EXISTS))),
            fixture.today.submitEntry("b1", "Words.", null, "k-1"),
        )
    }

    @Test
    fun editPatchesTheEntryWithAKey() = runTest {
        val fixture = Fixture { respondJson(entryJson("e1", "SUBMITTED", "Better words.")) }

        val result = fixture.today.editEntry("e1", "Better words.", idempotencyKey = "k-2")

        assertEquals(Result.Success(MyEntry("e1", "Better words.", EntryStatus.SUBMITTED)), result)
        assertEquals(HttpMethod.Patch, fixture.request.method)
        assertEquals("/api/v1/entries/e1", fixture.request.url.encodedPath)
        assertEquals("k-2", fixture.request.headers["Idempotency-Key"])
        assertEquals(setOf("text"), fixture.sentJson().keys)
    }

    @Test
    fun anEditAfterTheRevealIsImmutable() = runTest {
        val fixture = Fixture { respondProblem(HttpStatusCode.Conflict, "ENTRY_IMMUTABLE") }
        assertEquals(
            Result.Failure(DataError.Network.Api(ApiProblem(409, ApiErrorCode.ENTRY_IMMUTABLE))),
            fixture.today.editEntry("e1", "Words.", "k-2"),
        )
    }

    @Test
    fun deleteIsABodylessDeleteWithNoKey() = runTest {
        val fixture = Fixture { respond("", HttpStatusCode.NoContent) }
        assertEquals(Result.Success(Unit), fixture.today.deleteEntry("e1"))
        assertEquals(HttpMethod.Delete, fixture.request.method)
        assertEquals("/api/v1/entries/e1", fixture.request.url.encodedPath)
        assertNull(fixture.request.headers["Idempotency-Key"])
    }

    @Test
    fun anEntryAlreadyGoneIsTheNotFound() = runTest {
        val fixture = Fixture { respondProblem(HttpStatusCode.NotFound, "NOT_FOUND") }
        assertEquals(
            Result.Failure(DataError.Network.Api(ApiProblem(404, ApiErrorCode.NOT_FOUND))),
            fixture.today.deleteEntry("e1"),
        )
    }

    @Test
    fun offlineAndServerErrorsAreTyped() = runTest {
        assertEquals(Result.Failure(DataError.Network.NoInternet), Fixture { throw IOException("offline") }.today.today("b1"))
        assertEquals(
            Result.Failure(DataError.Network.Server(502)),
            Fixture { respond("<html>Bad gateway</html>", HttpStatusCode.BadGateway, headersOf(HttpHeaders.ContentType, "text/html")) }
                .today.submitEntry("b1", "Words.", null, "k-1"),
        )
    }

    private fun todayJson(
        status: String = "OPEN",
        myEntry: String = "null",
        partnerEntry: String = "null",
        prompt: String = """{"id":"p1","text":"What did Tunde do today that you noticed?"}""",
    ): String =
        """{"bondDay":{"date":"2026-09-14","status":"$status","settlesAt":"2026-09-15T08:00:00Z"},"myEntry":$myEntry,"partnerEntry":$partnerEntry,""" +
            """"streak":{"current":3,"longest":5,"freezesAvailable":1,"strictMode":false},"prompt":$prompt}"""

    private fun entryJson(id: String, status: String, text: String?): String =
        """{"id":"$id","bondId":"b1","date":"2026-09-14","authorMemberId":"m1","text":${text?.let { "\"$it\"" } ?: "null"},""" +
            """"status":"$status","createdAt":"2026-09-14T19:12:44Z","intendedAt":"2026-09-14T19:12:44Z",""" +
            """"favourited":false,"reaction":null}"""

    private companion object {
        const val LOCKED = """{"authorMemberId":"m2","status":"LOCKED"}"""
    }
}
