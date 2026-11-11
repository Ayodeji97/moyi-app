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
import com.moyi.app.data.testing.NoPersonalData
import com.moyi.app.data.testing.respondJson
import com.moyi.app.data.testing.respondProblem
import com.moyi.app.domain.bond.BondSettings
import com.moyi.app.domain.bond.BondSettingsChange
import com.moyi.app.domain.bond.BondStatus
import com.moyi.app.domain.bond.BondType
import com.moyi.app.domain.bond.Invite
import com.moyi.app.domain.bond.PendingDeletionRequest
import com.moyi.app.domain.bond.PendingTimezoneChange
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
import kotlin.test.assertTrue
import kotlin.time.Instant

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
        private val sessionManager = SessionManager(InMemoryTokenStore(), RefreshControllerApi(config.origin, publicClient.client), NoPersonalData)
        val repo = BondSettingsRepositoryImpl(AuthedHttpClient(HttpClientFactory.createAuthedClient(engine, sessionManager)), config)

        fun json(): JsonObject = Json.parseToJsonElement(assertIs<TextContent>(requests.single().body).text).jsonObject
    }

    @Test
    fun theDateTheZoneCanChangeAgainIsMappedAsItCame() = runTest {
        val locked = Fixture { respondBond(bondJson(allowedFrom = "\"2026-12-10\""), etag = "\"1\"") }
        assertEquals("2026-12-10", assertIs<Result.Success<BondSettings>>(locked.repo.settings("b1")).data.timezoneChangeAllowedFrom)

        val free = Fixture { respondBond(bondJson(), etag = "\"1\"") }
        assertNull(assertIs<Result.Success<BondSettings>>(free.repo.settings("b1")).data.timezoneChangeAllowedFrom)
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
                    createdAt = Instant.parse("2026-10-29T10:00:00Z"),
                ),
            ),
            result,
        )
        assertEquals(HttpMethod.Get, fixture.requests.single().method)
        assertEquals("/api/v1/bonds/b1", fixture.requests.single().url.encodedPath)
    }

    @Test
    fun theHeaderNamesBothMembersAndWhenTheBondBegan() = runTest {
        val members =
            """[{"id":"m1","displayName":"Amara","role":"OWNER","joinedAt":"2026-10-29T10:00:00Z","leftAt":null},""" +
                """{"id":"m2","displayName":"Tunde","role":"MEMBER","joinedAt":"2026-10-30T10:00:00Z","leftAt":null}]"""
        val fixture = Fixture { respondBond(bondJson(members = members), etag = "\"7\"") }

        val settings = assertIs<Result.Success<BondSettings>>(fixture.repo.settings("b1")).data

        assertEquals("Amara", settings.myName)
        assertEquals("Tunde", settings.partnerName)
        assertEquals(Instant.parse("2026-10-29T10:00:00Z"), settings.createdAt)
    }

    @Test
    fun aMembersListWithoutTheCallerLeavesMyNameEmpty() = runTest {
        val fixture = Fixture { respondBond(bondJson(), etag = "\"7\"") }
        assertNull(assertIs<Result.Success<BondSettings>>(fixture.repo.settings("b1")).data.myName)
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

        val result = fixture.repo.leave("b1", withdrawEntries = false)

        assertEquals(Result.Success(Unit), result)
        val request = fixture.requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/api/v1/bonds/b1/leave", request.url.encodedPath)
        assertEquals("""{"withdrawEntries":false}""", assertIs<TextContent>(request.body).text)
    }

    @Test
    fun leavingABondThatAlreadyEndedIsBondArchived() = runTest {
        val fixture = Fixture { respondProblem(HttpStatusCode.Conflict, "BOND_ARCHIVED") }
        assertEquals(Result.Failure(DataError.Network.Api(ApiProblem(409, ApiErrorCode.BOND_ARCHIVED))), fixture.repo.leave("b1", withdrawEntries = false))
    }

    @Test
    fun leaveSendsAWithdrawalWhenAskedFor() = runTest {
        val fixture = Fixture { respond("", HttpStatusCode.NoContent) }

        assertEquals(Result.Success(Unit), fixture.repo.leave("b1", withdrawEntries = true))
        assertEquals("""{"withdrawEntries":true}""", assertIs<TextContent>(fixture.requests.single().body).text)
    }

    @Test
    fun blockPostsToItsOwnRouteWithTheChoiceAlwaysExplicit() = runTest {
        val fixture = Fixture { respond("", HttpStatusCode.NoContent) }

        assertEquals(Result.Success(Unit), fixture.repo.block("b1", withdrawEntries = true))
        assertEquals(Result.Success(Unit), fixture.repo.block("b1", withdrawEntries = false))

        val (withdraw, keep) = fixture.requests
        assertEquals(HttpMethod.Post, withdraw.method)
        assertEquals("/api/v1/bonds/b1/block", withdraw.url.encodedPath)
        assertEquals("""{"withdrawEntries":true}""", assertIs<TextContent>(withdraw.body).text)
        // An absent value withdraws on this route (doc 06), so keeping must be said out loud.
        assertEquals("""{"withdrawEntries":false}""", assertIs<TextContent>(keep.body).text)
    }

    @Test
    fun aFailedBlockReachesTheCallerByItsStatus() = runTest {
        val fixture = Fixture { respondProblem(HttpStatusCode.NotFound, "NOT_FOUND") }
        assertEquals(Result.Failure(DataError.Network.Api(ApiProblem(404, ApiErrorCode.NOT_FOUND))), fixture.repo.block("b1", withdrawEntries = true))
    }

    @Test
    fun theFirstAskToCloseTheBoxIsAPendingRequestByTheCaller() = runTest {
        val pending = """{"requestedByMemberId":"m1","requestedAt":"2026-11-06T10:00:00Z","expiresAt":"2026-11-13T10:00:00Z"}"""
        val fixture = Fixture { respondBond(bondJson(pendingDeletion = pending), etag = "\"9\"", status = HttpStatusCode.Accepted) }

        val result = assertIs<Result.Success<BondSettings>>(fixture.repo.requestDeletion("b1")).data

        val request = fixture.requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/api/v1/bonds/b1/deletion-request", request.url.encodedPath)
        assertEquals(PendingDeletionRequest(requestedByMe = true, expiresAt = Instant.parse("2026-11-13T10:00:00Z")), result.pendingDeletion)
        assertNull(result.deletionScheduledFor)
        assertEquals("\"9\"", result.version)
    }

    @Test
    fun theOtherMembersAskIsTheirsAndBothAskingCarriesTheDate() = runTest {
        val theirs = """{"requestedByMemberId":"m2","requestedAt":"2026-11-06T10:00:00Z","expiresAt":"2026-11-13T10:00:00Z"}"""
        val asked = Fixture { respondBond(bondJson(pendingDeletion = theirs), etag = "\"9\"") }
        assertEquals(false, assertIs<Result.Success<BondSettings>>(asked.repo.settings("b1")).data.pendingDeletion?.requestedByMe)

        val cooling = Fixture {
            respondBond(bondJson(status = "PENDING_DELETION", deletionScheduledFor = "\"2026-12-06T10:00:00Z\""), etag = "\"10\"", status = HttpStatusCode.Accepted)
        }
        val settings = assertIs<Result.Success<BondSettings>>(cooling.repo.requestDeletion("b1")).data
        assertEquals(BondStatus.PENDING_DELETION, settings.status)
        assertEquals(Instant.parse("2026-12-06T10:00:00Z"), settings.deletionScheduledFor)
        assertTrue(settings.coolingOff)
    }

    @Test
    fun askingOnAnEndedBondIsBondArchived() = runTest {
        val fixture = Fixture { respondProblem(HttpStatusCode.Conflict, "BOND_ARCHIVED") }
        assertEquals(Result.Failure(DataError.Network.Api(ApiProblem(409, ApiErrorCode.BOND_ARCHIVED))), fixture.repo.requestDeletion("b1"))
    }

    @Test
    fun cancellingTheBoxClosingIsADelete() = runTest {
        val fixture = Fixture { respond("", HttpStatusCode.NoContent) }

        assertEquals(Result.Success(Unit), fixture.repo.cancelDeletion("b1"))
        assertEquals(HttpMethod.Delete, fixture.requests.single().method)
        assertEquals("/api/v1/bonds/b1/deletion-request", fixture.requests.single().url.encodedPath)
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

    private fun MockRequestHandleScope.respondBond(body: String, etag: String, status: HttpStatusCode = HttpStatusCode.OK): HttpResponseData =
        respond(body, status, headersOf(HttpHeaders.ContentType to listOf("application/json"), HttpHeaders.ETag to listOf(etag)))

    private fun bondJson(
        revealTime: String = "null",
        pending: String = "null",
        invite: String = "null",
        members: String = "[]",
        status: String = "ACTIVE",
        deletionScheduledFor: String = "null",
        pendingDeletion: String = "null",
        allowedFrom: String = "null",
    ): String =
        """{"id":"b1","type":"COUPLE","name":"Amara and Tunde","anchorTimezone":"Africa/Lagos","revealTimeLocal":$revealTime,""" +
            """"strictMode":false,"status":"$status","maxMembers":2,"createdAt":"2026-10-29T10:00:00Z","archivedAt":null,""" +
            """"deletionScheduledFor":$deletionScheduledFor,"members":$members,"me":{"memberId":"m1","role":"OWNER"},"invite":$invite,""" +
            """"pendingTimezoneChange":$pending,"pendingDeletionRequest":$pendingDeletion,"pendingSemanticSearchRequest":null,"semanticSearchEnabled":false,""" +
            """"timezoneChangeAllowedFrom":$allowedFrom}"""

    private fun pendingJson(proposedBy: String): String =
        """{"id":"p1","proposedTimezone":"Europe/London","proposedByMemberId":"$proposedBy",""" +
            """"proposedAt":"2026-11-01T10:00:00Z","expiresAt":"2026-11-08T10:00:00Z"}"""

    // ----- the shared time zone (FR-027) and the invite, read with the settings -----

    @Test
    fun aProposalByTheCallerIsTheirsToCancelAndNotToAgreeTo() = runTest {
        val fixture = Fixture { respondBond(bondJson(pending = pendingJson(proposedBy = "m1")), etag = "\"7\"") }

        val pending = assertIs<Result.Success<BondSettings>>(fixture.repo.settings("b1")).data.pendingTimezone

        assertEquals(PendingTimezoneChange("p1", "Europe/London", proposedByMe = true, Instant.parse("2026-11-08T10:00:00Z")), pending)
    }

    @Test
    fun aProposalByTheOtherMemberIsTheCallersToAnswer() = runTest {
        val members =
            """[{"id":"m1","displayName":"Amara","role":"OWNER","joinedAt":"2026-10-29T10:00:00Z","leftAt":null},""" +
                """{"id":"m2","displayName":"Tunde","role":"MEMBER","joinedAt":"2026-10-30T10:00:00Z","leftAt":null}]"""
        val fixture = Fixture { respondBond(bondJson(pending = pendingJson(proposedBy = "m2"), members = members), etag = "\"7\"") }

        val settings = assertIs<Result.Success<BondSettings>>(fixture.repo.settings("b1")).data

        assertEquals(false, settings.pendingTimezone?.proposedByMe)
        assertEquals("Tunde", settings.partnerName)
    }

    @Test
    fun theLiveInviteComesWithTheSettings() = runTest {
        val invite = """{"id":"i1","code":"K7Q2PD","link":"https://getmoyi.app/i/K7Q2PD","expiresAt":"2026-11-08T10:00:00Z"}"""
        val fixture = Fixture { respondBond(bondJson(invite = invite, status = "PENDING_MEMBER"), etag = "\"7\"") }

        val settings = assertIs<Result.Success<BondSettings>>(fixture.repo.settings("b1")).data

        assertEquals(Invite("i1", "K7Q2PD", "https://getmoyi.app/i/K7Q2PD", Instant.parse("2026-11-08T10:00:00Z")), settings.invite)
        assertEquals(true, settings.waitingForPartner)
    }

    @Test
    fun proposingIsAPatchOfTheZoneAloneWithNoConditionAndKeepsTheNewVersion() = runTest {
        val fixture = Fixture { respondBond(bondJson(pending = pendingJson(proposedBy = "m1")), etag = "\"9\"") }

        val result = fixture.repo.proposeTimezone("b1", "Europe/London")

        val request = fixture.requests.single()
        assertEquals(HttpMethod.Patch, request.method)
        assertEquals("/api/v1/bonds/b1/timezone", request.url.encodedPath)
        assertNull(request.headers[HttpHeaders.IfMatch], "ADR-0030: the two-party routes take no If-Match")
        assertEquals("""{"anchorTimezone":"Europe/London"}""", assertIs<TextContent>(request.body).text)
        val saved = assertIs<Result.Success<BondSettings>>(result).data
        assertEquals("\"9\"", saved.version)
        assertEquals("Europe/London", saved.pendingTimezone?.timezone)
    }

    @Test
    fun aSecondChangeInsideThirtyDaysReachesTheCallerByItsCode() = runTest {
        val fixture = Fixture { respondProblem(HttpStatusCode.Conflict, "TIMEZONE_CHANGE_TOO_SOON") }

        val result = fixture.repo.proposeTimezone("b1", "Europe/London")

        assertEquals(Result.Failure(DataError.Network.Api(ApiProblem(409, ApiErrorCode.TIMEZONE_CHANGE_TOO_SOON))), result)
    }

    @Test
    fun confirmingNamesTheProposal() = runTest {
        val fixture = Fixture { respondBond(bondJson(), etag = "\"10\"") }

        val result = fixture.repo.confirmTimezone("b1", "p1")

        val request = fixture.requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/api/v1/bonds/b1/timezone/confirm", request.url.encodedPath)
        assertEquals("""{"proposalId":"p1"}""", assertIs<TextContent>(request.body).text)
        assertEquals("\"10\"", assertIs<Result.Success<BondSettings>>(result).data.version)
    }

    @Test
    fun cancellingIsADeleteAndNothingWaitingIsANotFound() = runTest {
        var answer = 0
        val fixture = Fixture {
            if (answer++ == 0) respond("", HttpStatusCode.NoContent) else respondProblem(HttpStatusCode.NotFound, "NOT_FOUND")
        }

        assertEquals(Result.Success(Unit), fixture.repo.cancelTimezoneChange("b1"))
        assertEquals(Result.Failure(DataError.Network.Api(ApiProblem(404, ApiErrorCode.NOT_FOUND))), fixture.repo.cancelTimezoneChange("b1"))
        assertEquals(HttpMethod.Delete, fixture.requests.first().method)
        assertEquals("/api/v1/bonds/b1/timezone", fixture.requests.first().url.encodedPath)
    }
}
