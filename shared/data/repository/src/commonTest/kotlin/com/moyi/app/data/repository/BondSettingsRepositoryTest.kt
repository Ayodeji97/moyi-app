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
import com.moyi.app.data.testing.InMemoryTokenStore
import com.moyi.app.data.testing.respondJson
import com.moyi.app.data.testing.respondProblem
import com.moyi.app.domain.bond.BondSettings
import com.moyi.app.domain.bond.BondSettingsChange
import com.moyi.app.domain.bond.BondStatus
import com.moyi.app.domain.bond.BondType
import com.moyi.app.domain.bond.MemberSettings
import com.moyi.app.domain.bond.TimeOfDay
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * [BondSettingsRepositoryImpl] against a MockEngine through the real authed client:
 * the `ETag` read from every Bond response and sent back as `If-Match`, the PATCH
 * body naming only what changed, the `412` reaching the caller by its code, leave's
 * explicit body, and the member's own settings.
 */
class BondSettingsRepositoryTest {
    private class Fixture(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) {
        val requests = mutableListOf<HttpRequestData>()
        val engine = MockEngine { request ->
            requests += request
            handler(request)
        }
        private val config = ApiConfig("http://localhost:8080/api/v1")
        private val publicClient = PublicHttpClient(HttpClientFactory.createPublicClient(engine))
        private val sessionManager = SessionManager(InMemoryTokenStore(), RefreshControllerApi(config.origin, publicClient.client))
        val repo = BondSettingsRepositoryImpl(AuthedHttpClient(HttpClientFactory.createAuthedClient(engine, sessionManager)), config)

        fun json(): JsonObject = Json.parseToJsonElement(assertIs<TextContent>(requests.single().body).text).jsonObject
    }

    @Test
    fun readingKeepsTheETagAsTheVersion() = runTest {
        val fixture = Fixture { respondBond(bondJson(revealTime = "\"21:00\""), etag = "\"7\"") }

        val result = fixture.repo.settings("b1")

        assertEquals(
            Result.Success(
                BondSettings(
                    bondId = "b1",
                    name = "Amara and Tunde",
                    type = BondType.COUPLE,
                    status = BondStatus.ACTIVE,
                    anchorTimezone = "Africa/Lagos",
                    revealTime = TimeOfDay(21, 0),
                    strictMode = false,
                    version = "\"7\"",
                ),
            ),
            result,
        )
        assertEquals(HttpMethod.Get, fixture.requests.single().method)
        assertEquals("/api/v1/bonds/b1", fixture.requests.single().url.encodedPath)
    }

    @Test
    fun aNullRevealTimeIsNoRevealTime() = runTest {
        val fixture = Fixture { respondBond(bondJson(revealTime = "null"), etag = "\"7\"") }
        assertNull(assertIs<Result.Success<BondSettings>>(fixture.repo.settings("b1")).data.revealTime)
    }

    @Test
    fun aBondWithoutAnETagCannotBeEditedSoItIsASerializationError() = runTest {
        val fixture = Fixture { respondJson(bondJson()) }
        assertEquals(Result.Failure(DataError.Network.Serialization), fixture.repo.settings("b1"))
    }

    @Test
    fun aRevealTimeThatIsNotHoursAndMinutesIsASerializationError() = runTest {
        val fixture = Fixture { respondBond(bondJson(revealTime = "\"21:00:00\""), etag = "\"7\"") }
        assertEquals(Result.Failure(DataError.Network.Serialization), fixture.repo.settings("b1"))
    }

    @Test
    fun aChangeSendsTheVersionAsIfMatchAndOnlyTheFieldsItNames() = runTest {
        val fixture = Fixture { respondBond(bondJson(revealTime = "\"20:30\""), etag = "\"8\"") }

        val result = fixture.repo.update("b1", version = "\"7\"", change = BondSettingsChange(revealTime = TimeOfDay(20, 30)))

        val request = fixture.requests.single()
        assertEquals(HttpMethod.Patch, request.method)
        assertEquals("/api/v1/bonds/b1", request.url.encodedPath)
        assertEquals("\"7\"", request.headers[HttpHeaders.IfMatch])
        assertEquals(setOf("revealTimeLocal"), fixture.json().keys, "a field not named is not sent, so it keeps its value")
        assertEquals("\"20:30\"", fixture.json()["revealTimeLocal"].toString())
        val saved = assertIs<Result.Success<BondSettings>>(result).data
        assertEquals("\"8\"", saved.version, "the 200 carries the new version, which the next change must send")
        assertEquals(TimeOfDay(20, 30), saved.revealTime)
    }

    @Test
    fun theTypeAndRestDaysGoAsTheContractSpellsThem() = runTest {
        val fixture = Fixture { respondBond(bondJson(), etag = "\"8\"") }

        fixture.repo.update("b1", "\"7\"", BondSettingsChange(type = BondType.FRIENDS, strictMode = true, name = "Us"))

        val body = fixture.json()
        assertEquals("\"FRIENDS\"", body["type"].toString())
        assertEquals("true", body["strictMode"].toString())
        assertEquals("\"Us\"", body["name"].toString())
    }

    @Test
    fun aStaleVersionReachesTheCallerAsPreconditionFailed() = runTest {
        val fixture = Fixture { respondProblem(HttpStatusCode.PreconditionFailed, "PRECONDITION_FAILED") }

        val result = fixture.repo.update("b1", "\"6\"", BondSettingsChange(name = "Us"))

        assertEquals(Result.Failure(DataError.Network.Api(ApiProblem(412, ApiErrorCode.PRECONDITION_FAILED))), result)
    }

    @Test
    fun leaveKeepsTheCallersEntriesAndSaysSoExplicitly() = runTest {
        val fixture = Fixture { respond("", HttpStatusCode.NoContent) }

        val result = fixture.repo.leave("b1")

        assertEquals(Result.Success(Unit), result)
        val request = fixture.requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/api/v1/bonds/b1/leave", request.url.encodedPath)
        assertEquals("""{"withdrawEntries":false}""", assertIs<TextContent>(request.body).text)
    }

    @Test
    fun leavingABondThatAlreadyEndedIsBondArchived() = runTest {
        val fixture = Fixture { respondProblem(HttpStatusCode.Conflict, "BOND_ARCHIVED") }
        assertEquals(Result.Failure(DataError.Network.Api(ApiProblem(409, ApiErrorCode.BOND_ARCHIVED))), fixture.repo.leave("b1"))
    }

    @Test
    fun memberSettingsAreTheCallersOwn() = runTest {
        val fixture = Fixture {
            respondJson("""{"reminderTimeLocal":"20:00","reminderTimezone":"Europe/London","quietHoursStart":null,"quietHoursEnd":null,"nicknameForOther":"T"}""")
        }

        val result = fixture.repo.memberSettings("b1")

        assertEquals(Result.Success(MemberSettings(TimeOfDay(20, 0), "Europe/London", nicknameForOther = "T")), result)
        assertEquals("/api/v1/bonds/b1/members/me/settings", fixture.requests.single().url.encodedPath)
    }

    @Test
    fun replacingMemberSettingsIsAPutWithNoCondition() = runTest {
        val fixture = Fixture { respondJson("""{"reminderTimeLocal":"07:30","reminderTimezone":"Europe/London"}""") }

        val result = fixture.repo.replaceMemberSettings("b1", MemberSettings(TimeOfDay(7, 30), "Europe/London"))

        val request = fixture.requests.single()
        assertEquals(HttpMethod.Put, request.method)
        assertNull(request.headers[HttpHeaders.IfMatch])
        assertEquals("\"07:30\"", fixture.json()["reminderTimeLocal"].toString())
        assertEquals(Result.Success(MemberSettings(TimeOfDay(7, 30), "Europe/London")), result)
    }

    private fun MockRequestHandleScope.respondBond(body: String, etag: String): HttpResponseData =
        respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType to listOf("application/json"), HttpHeaders.ETag to listOf(etag)))

    private fun bondJson(revealTime: String = "null"): String =
        """{"id":"b1","type":"COUPLE","name":"Amara and Tunde","anchorTimezone":"Africa/Lagos","revealTimeLocal":$revealTime,""" +
            """"strictMode":false,"status":"ACTIVE","maxMembers":2,"createdAt":"2026-10-29T10:00:00Z","archivedAt":null,""" +
            """"deletionScheduledFor":null,"members":[],"me":{"memberId":"m1","role":"OWNER"},"invite":null,""" +
            """"pendingTimezoneChange":null,"pendingDeletionRequest":null}"""
}
