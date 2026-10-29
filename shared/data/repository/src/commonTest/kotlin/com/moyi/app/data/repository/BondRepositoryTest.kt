package com.moyi.app.data.repository

import com.moyi.app.api.apis.RefreshControllerApi
import com.moyi.app.core.ApiErrorCode
import com.moyi.app.core.ApiProblem
import com.moyi.app.core.DataError
import com.moyi.app.core.FieldError
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
import com.moyi.app.data.testing.storedSession
import com.moyi.app.data.testing.tokenPairJson
import com.moyi.app.data.testing.userJson
import com.moyi.app.domain.auth.User
import com.moyi.app.domain.auth.UserStatus
import com.moyi.app.domain.bond.Bond
import com.moyi.app.domain.bond.BondStatus
import com.moyi.app.domain.bond.BondType
import com.moyi.app.domain.bond.Invite
import com.moyi.app.domain.bond.InvitePreview
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

/**
 * [BondRepositoryImpl] and [AccountRepositoryImpl] against a MockEngine,
 * through the real authed client: paths and bodies as the contract has them,
 * problem+json refusals read by code, and a missing field in a 2xx treated as
 * a body that did not parse.
 */
class BondRepositoryTest {
    private val config = ApiConfig("http://localhost:8080/api/v1")

    private class Fixture(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) {
        val log = EventLog()
        val store = InMemoryTokenStore(log = log)
        val requests = mutableListOf<HttpRequestData>()
        val engine = MockEngine { request ->
            requests += request
            log.add("${request.method.value} ${request.url.encodedPath} ${request.headers[HttpHeaders.Authorization] ?: "no-auth"}")
            handler(request)
        }
        val publicClient = PublicHttpClient(HttpClientFactory.createPublicClient(engine))
        val sessionManager = SessionManager(store, RefreshControllerApi(ApiConfig("http://localhost:8080/api/v1").origin, publicClient.client))
        val authed = AuthedHttpClient(HttpClientFactory.createAuthedClient(engine, sessionManager))
        val bonds = BondRepositoryImpl(authed, ApiConfig("http://localhost:8080/api/v1"))
        val account = AccountRepositoryImpl(authed, ApiConfig("http://localhost:8080/api/v1"))

        fun body(path: String): String = assertIs<TextContent>(requests.single { it.url.encodedPath == path }.body).text
    }

    @Test
    fun listMapsEveryBondAndItsLiveInvite() = runTest {
        val fixture = Fixture { respondJson("""{"bonds":[${bondJson()},${bondJson(id = "b2", status = "ARCHIVED", invite = null)}]}""") }

        val result = fixture.bonds.bonds()

        assertEquals(
            Result.Success(
                listOf(
                    Bond("b1", "Amara and Tunde", BondType.COUPLE, BondStatus.PENDING_MEMBER, Invite("i1", "K7Q2PD", "https://getmoyi.app/i/K7Q2PD")),
                    Bond("b2", "Amara and Tunde", BondType.COUPLE, BondStatus.ARCHIVED, null),
                ),
            ),
            result,
        )
        assertEquals(HttpMethod.Get, fixture.requests.single().method)
        assertEquals("/api/v1/bonds", fixture.requests.single().url.encodedPath)
    }

    @Test
    fun aBondMissingAFieldTheAppRoutesOnFailsTheWholeList() = runTest {
        val fixture = Fixture { respondJson("""{"bonds":[${bondJson()},{"id":"b2","name":"x","type":"COUPLE"}]}""") }
        assertEquals(Result.Failure(DataError.Network.Serialization), fixture.bonds.bonds())
    }

    @Test
    fun aStatusThisBuildHasNotHeardOfIsASerializationErrorNotACrash() = runTest {
        val fixture = Fixture { respondJson("""{"bonds":[${bondJson(status = "PAUSED")}]}""") }
        assertEquals(Result.Failure(DataError.Network.Serialization), fixture.bonds.bonds())
    }

    @Test
    fun createSendsTheContractsBodyAndReturnsTheBondWithItsInvite() = runTest {
        val fixture = Fixture { respondJson(bondJson(), HttpStatusCode.Created) }

        val result = fixture.bonds.createBond("Amara and Tunde", BondType.FRIENDS, "Africa/Lagos")

        assertEquals("K7Q2PD", assertIs<Result.Success<Bond>>(result).data.invite?.code)
        val sent = Json.parseToJsonElement(fixture.body("/api/v1/bonds")).jsonObject
        assertEquals("Amara and Tunde", sent["name"]?.jsonPrimitive?.content)
        assertEquals("FRIENDS", sent["type"]?.jsonPrimitive?.content)
        assertEquals("Africa/Lagos", sent["anchorTimezone"]?.jsonPrimitive?.content)
        assertEquals("Africa/Lagos", sent["reminderTimezone"]?.jsonPrimitive?.content)
        assertEquals(null, sent["revealTimeLocal"], "reveal time lives in Bond settings, never onboarding")
    }

    @Test
    fun anUnverifiedCreatorIsTheCodeNotTheStatus() = runTest {
        val fixture = Fixture { respondProblem(HttpStatusCode.Forbidden, "EMAIL_NOT_VERIFIED") }
        assertEquals(
            Result.Failure(DataError.Network.Api(ApiProblem(403, ApiErrorCode.EMAIL_NOT_VERIFIED))),
            fixture.bonds.createBond("Us", BondType.COUPLE, "Africa/Lagos"),
        )
    }

    @Test
    fun aValidationRefusalCarriesItsFields() = runTest {
        val fixture = Fixture {
            respond(
                """{"status":422,"code":"VALIDATION_FAILED","errors":[{"field":"name","code":"Pattern","message":"must not be blank"}]}""",
                HttpStatusCode.UnprocessableEntity,
                headersOf(HttpHeaders.ContentType, "application/problem+json"),
            )
        }
        val error = assertIs<Result.Failure<DataError.Network>>(fixture.bonds.createBond(" ", BondType.COUPLE, "Africa/Lagos")).error
        assertEquals(listOf(FieldError("name", "Pattern", "must not be blank")), assertIs<DataError.Network.Api>(error).problem.fieldErrors)
    }

    @Test
    fun createInvitePostsToTheBondsInvites() = runTest {
        val fixture = Fixture {
            respondJson("""{"id":"i2","code":"M3N4P5","link":"https://getmoyi.app/i/M3N4P5","expiresAt":"2026-11-05T12:00:00Z"}""", HttpStatusCode.Created)
        }
        assertEquals(Result.Success(Invite("i2", "M3N4P5", "https://getmoyi.app/i/M3N4P5")), fixture.bonds.createInvite("b1"))
        assertEquals(HttpMethod.Post, fixture.requests.single().method)
        assertEquals("/api/v1/bonds/b1/invites", fixture.requests.single().url.encodedPath)
    }

    @Test
    fun revokeIsADeleteThatAnswersNoContent() = runTest {
        val fixture = Fixture { respond("", HttpStatusCode.NoContent) }
        assertEquals(Result.Success(Unit), fixture.bonds.revokeInvite("b1", "i1"))
        assertEquals(HttpMethod.Delete, fixture.requests.single().method)
        assertEquals("/api/v1/bonds/b1/invites/i1", fixture.requests.single().url.encodedPath)
    }

    @Test
    fun resolveReturnsThePreview() = runTest {
        val fixture = Fixture { respondJson("""{"bondName":"Amara and Tunde","bondType":"PARENT_CHILD","inviterDisplayName":"Amara"}""") }
        assertEquals(
            Result.Success(InvitePreview("Amara", "Amara and Tunde", BondType.PARENT_CHILD)),
            fixture.bonds.resolveInvite("K7Q2PD"),
        )
        assertEquals("/api/v1/invites/K7Q2PD", fixture.requests.single().url.encodedPath)
    }

    @Test
    fun everyWayACodeFailsIsTheOneNotUsableCode() = runTest {
        val fixture = Fixture { respondProblem(HttpStatusCode.NotFound, "INVITE_NOT_USABLE") }
        val notUsable = Result.Failure(DataError.Network.Api(ApiProblem(404, ApiErrorCode.INVITE_NOT_USABLE)))
        assertEquals(notUsable, fixture.bonds.resolveInvite("ZZZZZZ"))
        assertEquals(notUsable, fixture.bonds.acceptInvite("ZZZZZZ"))
    }

    @Test
    fun acceptPostsAndReturnsTheJoinedBond() = runTest {
        val fixture = Fixture { respondJson(bondJson(status = "ACTIVE", invite = null)) }
        val bond = assertIs<Result.Success<Bond>>(fixture.bonds.acceptInvite("K7Q2PD")).data
        assertEquals(BondStatus.ACTIVE, bond.status)
        assertEquals(HttpMethod.Post, fixture.requests.single().method)
        assertEquals("/api/v1/invites/K7Q2PD/accept", fixture.requests.single().url.encodedPath)
    }

    @Test
    fun theCallersOwnStateIsNamed() = runTest {
        for (code in listOf("ALREADY_MEMBER", "BOND_LIMIT_REACHED")) {
            val fixture = Fixture { respondProblem(HttpStatusCode.Conflict, code) }
            val error = assertIs<Result.Failure<DataError.Network>>(fixture.bonds.acceptInvite("K7Q2PD")).error
            assertEquals(code, assertIs<DataError.Network.Api>(error).problem.code.name)
        }
    }

    @Test
    fun rateLimitedAndServerErrorsAndOfflineAreTyped() = runTest {
        assertEquals(
            Result.Failure(DataError.Network.Api(ApiProblem(429, ApiErrorCode.RATE_LIMITED))),
            Fixture { respondProblem(HttpStatusCode.TooManyRequests, "RATE_LIMITED") }.bonds.resolveInvite("K7Q2PD"),
        )
        assertEquals(
            Result.Failure(DataError.Network.Server(502)),
            Fixture { respond("<html>Bad gateway</html>", HttpStatusCode.BadGateway, headersOf(HttpHeaders.ContentType, "text/html")) }
                .bonds.bonds(),
        )
        assertEquals(Result.Failure(DataError.Network.NoInternet), Fixture { throw IOException("offline") }.bonds.bonds())
    }

    @Test
    fun anExpiredAccessTokenIsRefreshedOnceAndTheCallRetried() = runTest {
        var bondsCalls = 0
        var refreshes = 0
        val fixture = Fixture { request ->
            when (request.url.encodedPath) {
                // The restore spends r1 for a1; the 401 below spends r2 for a2.
                "/api/v1/auth/refresh" -> if (refreshes++ == 0) respondJson(tokenPairJson("a1", "r2")) else respondJson(tokenPairJson("a2", "r3"))
                else -> if (bondsCalls++ == 0) {
                    respondProblem(HttpStatusCode.Unauthorized, "UNAUTHENTICATED", bearerChallenge = true)
                } else {
                    respondJson("""{"bonds":[]}""")
                }
            }
        }
        fixture.store.write(storedSession("r1"))
        fixture.sessionManager.restore()

        assertEquals(Result.Success(emptyList()), fixture.bonds.bonds())
        val calls = fixture.log.snapshot().filter { it.startsWith("GET /api/v1/bonds") }
        assertEquals(listOf("GET /api/v1/bonds Bearer a1", "GET /api/v1/bonds Bearer a2"), calls)
        assertEquals(2, refreshes, "one refresh at launch, one for the 401: never a second for the same 401")
    }

    @Test
    fun currentUserReadsMe() = runTest {
        val fixture = Fixture { respondJson(userJson()) }
        assertEquals(
            Result.Success(User("7c9e6679-7425-40de-944b-e07fc1f90ae7", "ada@example.test", "Ada", emailVerified = true, status = UserStatus.ACTIVE)),
            fixture.account.currentUser(),
        )
        assertEquals("/api/v1/me", fixture.requests.single().url.encodedPath)
    }

    @Test
    fun aMeWithoutAnEmailIsASerializationError() = runTest {
        val fixture = Fixture { respondJson("""{"id":"u1","displayName":"Ada","emailVerified":false}""") }
        assertEquals(Result.Failure(DataError.Network.Serialization), fixture.account.currentUser())
    }

    private fun bondJson(id: String = "b1", status: String = "PENDING_MEMBER", invite: String? = INVITE_JSON): String =
        """{"id":"$id","type":"COUPLE","name":"Amara and Tunde","anchorTimezone":"Africa/Lagos","revealTimeLocal":null,""" +
            """"strictMode":false,"status":"$status","maxMembers":2,"createdAt":"2026-10-29T10:00:00Z","archivedAt":null,""" +
            """"deletionScheduledFor":null,"members":[],"me":{"memberId":"m1","role":"OWNER"},"invite":$invite,""" +
            """"pendingTimezoneChange":null,"pendingDeletionRequest":null}"""

    private companion object {
        const val INVITE_JSON =
            """{"id":"i1","code":"K7Q2PD","link":"https://getmoyi.app/i/K7Q2PD","expiresAt":"2026-11-05T10:00:00Z"}"""
    }
}
