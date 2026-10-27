@file:OptIn(ExperimentalAtomicApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.moyi.app.data.repository

import com.moyi.app.api.apis.LoginControllerApi
import com.moyi.app.api.apis.MeControllerApi
import com.moyi.app.api.apis.RefreshControllerApi
import com.moyi.app.api.models.LoginRequest
import com.moyi.app.core.ApiErrorCode
import com.moyi.app.core.ApiProblem
import com.moyi.app.core.DataError
import com.moyi.app.core.Result
import com.moyi.app.data.network.ApiConfig
import com.moyi.app.data.network.HttpClientFactory
import com.moyi.app.data.network.PublicHttpClient
import com.moyi.app.data.network.safeApiCall
import com.moyi.app.data.session.SessionManager
import com.moyi.app.data.testing.EventLog
import com.moyi.app.data.testing.InMemoryTokenStore
import com.moyi.app.data.testing.USER_ID
import com.moyi.app.data.testing.respondJson
import com.moyi.app.data.testing.respondProblem
import com.moyi.app.data.testing.tokenPairJson
import com.moyi.app.data.testing.userJson
import com.moyi.app.domain.auth.SessionEndReason
import com.moyi.app.domain.auth.SessionEvent
import com.moyi.app.domain.auth.SessionState
import com.moyi.app.domain.auth.User
import com.moyi.app.domain.auth.UserStatus
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthRepositoryTest {
    private val config = ApiConfig("http://localhost:8080/api/v1")
    private val ada = User(USER_ID, "ada@example.test", "Ada", emailVerified = true, status = UserStatus.ACTIVE)
    private val device = DeviceInfo(platform = "IOS", appVersion = "1.0", osVersion = "26.0")

    private inner class Fixture(val handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) {
        val log = EventLog()
        val store = InMemoryTokenStore(log = log)
        val refreshCalls = AtomicInt(0)
        val requests = mutableListOf<HttpRequestData>()
        val engine = MockEngine { request ->
            requests += request
            log.add("${request.method.value} ${request.url.encodedPath} ${request.headers[HttpHeaders.Authorization] ?: "no-auth"}")
            if (request.url.encodedPath == "/api/v1/auth/refresh") refreshCalls.incrementAndFetch()
            handler(request)
        }
        val publicClient = PublicHttpClient(HttpClientFactory.createPublicClient(engine))
        val sessionManager = SessionManager(store, RefreshControllerApi(config.origin, publicClient.client))
        val authedClient = HttpClientFactory.createAuthedClient(engine, sessionManager)
        val repository = AuthRepositoryImpl(publicClient, config, sessionManager) { device }
    }

    private fun TestScope.events(fixture: Fixture): List<SessionEvent> {
        val events = mutableListOf<SessionEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { fixture.repository.sessionEvents.toList(events) }
        return events
    }

    @Test
    fun aWrongPasswordIsInvalidCredentialsAndNeverStartsARefresh() = runTest {
        val fixture = Fixture { request ->
            when (request.url.encodedPath) {
                "/api/v1/auth/login" -> respondProblem(HttpStatusCode.Unauthorized, "INVALID_CREDENTIALS")
                else -> respondJson(tokenPairJson("a9", "r9"))
            }
        }
        // A stored session makes a refresh possible, so its absence below is the client's choice.
        fixture.store.write(com.moyi.app.data.testing.storedSession("r1"))

        val result = fixture.repository.login("ada@example.test", "wrong horse")

        assertEquals(Result.Failure(DataError.Network.Api(ApiProblem(401, ApiErrorCode.INVALID_CREDENTIALS))), result)
        assertEquals(0, fixture.refreshCalls.load())
        assertEquals(1, fixture.requests.size)
    }

    @Test
    fun theSameLogin401OverTheAuthedClientWouldStartARefresh() = runTest {
        // Why login must use the public client: the bearer plugin treats ANY
        // 401 as an expired token (with one provider installed, even without
        // WWW-Authenticate), and would spend the stored refresh token.
        val fixture = Fixture { request ->
            when (request.url.encodedPath) {
                "/api/v1/auth/login" -> respondProblem(HttpStatusCode.Unauthorized, "INVALID_CREDENTIALS")
                else -> respondJson(tokenPairJson("a9", "r9"))
            }
        }
        fixture.store.write(com.moyi.app.data.testing.storedSession("r1"))

        safeApiCall { LoginControllerApi(config.origin, fixture.authedClient).login(LoginRequest("ada@example.test", "wrong horse")) }

        assertEquals(1, fixture.refreshCalls.load())
    }

    @Test
    fun loginStoresTheSessionSendsTheDeviceAndSignsIn() = runTest {
        val fixture = Fixture { respondJson(tokenPairJson("a1", "r1")) }

        val result = fixture.repository.login("ada@example.test", "correct horse")

        assertEquals(Result.Success(ada), result)
        assertEquals("r1", fixture.store.current?.refreshToken)
        assertEquals("a1", fixture.sessionManager.currentAccessToken())
        assertEquals(SessionState.SignedIn(ada), fixture.repository.session.value)
        val body = assertIs<TextContent>(fixture.requests.single().body).text
        assertTrue(""""device":{"appVersion":"1.0","osVersion":"26.0","platform":"IOS"}""" in body, body)
    }

    @Test
    fun aLoginResponseWithoutATokenIsASerializationErrorAndNoSession() = runTest {
        val fixture = Fixture { respondJson("""{"expiresIn":900,"user":${userJson()}}""") }

        assertEquals(Result.Failure(DataError.Network.Serialization), fixture.repository.login("ada@example.test", "pw"))
        assertNull(fixture.store.current)
        assertEquals(SessionState.Unknown, fixture.repository.session.value)
    }

    @Test
    fun aLoginWhoseSessionCannotBeStoredFailsAndLeavesNoAccessToken() = runTest {
        val fixture = Fixture { respondJson(tokenPairJson("a1", "r1")) }
        fixture.store.failWrites = true

        assertEquals(Result.Failure(DataError.Local.UNKNOWN), fixture.repository.login("ada@example.test", "pw"))
        assertNull(fixture.sessionManager.currentAccessToken())
    }

    @Test
    fun registerChecksTheStatusOnlyAndNeverDecodesTheBody() = runTest {
        val fixture = Fixture { respond("not json at all", HttpStatusCode.Created) }

        val result = fixture.repository.register("Ada", "ada@example.test", "pw", "en-GB", "2026-09-01", over18 = true)

        assertEquals(Result.Success(Unit), result)
        val body = assertIs<TextContent>(fixture.requests.single().body).text
        assertTrue(""""over18":true""" in body && """"displayName":"Ada"""" in body, body)
    }

    @Test
    fun anExpiredVerificationLinkIsItsCode() = runTest {
        val fixture = Fixture { respondProblem(HttpStatusCode.Gone, "VERIFICATION_TOKEN_EXPIRED") }

        val result = fixture.repository.verifyEmail("tok")

        assertEquals(Result.Failure(DataError.Network.Api(ApiProblem(410, ApiErrorCode.VERIFICATION_TOKEN_EXPIRED))), result)
    }

    @Test
    fun resendIsAccepted() = runTest {
        val fixture = Fixture { respond("", HttpStatusCode.Accepted) }
        assertEquals(Result.Success(Unit), fixture.repository.resendVerification("ada@example.test"))
    }

    @Test
    fun logoutClearsEverythingEvenWhenTheServerCannotBeReachedAndNoStaleTokenIsSentAfter() = runTest {
        val fixture = Fixture { request ->
            when (request.url.encodedPath) {
                "/api/v1/auth/login" -> respondJson(tokenPairJson("a1", "r1"))
                "/api/v1/auth/logout" -> throw IOException("offline")
                "/api/v1/me" -> respondJson(userJson())
                else -> respondProblem(HttpStatusCode.Unauthorized, "REFRESH_TOKEN_INVALID")
            }
        }
        val events = events(fixture)
        val me = MeControllerApi(config.origin, fixture.authedClient)
        fixture.repository.login("ada@example.test", "pw")
        safeApiCall { me.me() } // the authed client has now sent a1

        fixture.repository.logout()

        assertNull(fixture.store.current)
        assertNull(fixture.sessionManager.currentAccessToken())
        assertEquals(SessionState.SignedOut, fixture.repository.session.value)
        assertEquals(listOf<SessionEvent>(SessionEvent.Ended(SessionEndReason.SignedOut)), events)
        val logoutBody = assertIs<TextContent>(fixture.requests.single { it.url.encodedPath == "/api/v1/auth/logout" }.body).text
        assertEquals("""{"refreshToken":"r1"}""", logoutBody)

        safeApiCall { me.me() }

        val log = fixture.log.snapshot()
        assertTrue(log.indexOf("GET /api/v1/me Bearer a1") in 0..<log.indexOf("store.clear"), log.joinToString("\n"))
        // After sign-out the authed client sends no token at all: nothing cached a1.
        assertEquals("GET /api/v1/me no-auth", log.last())
    }

    @Test
    fun restoreSessionDelegatesToTheSession() = runTest {
        val fixture = Fixture { error("no network expected") }
        assertEquals(SessionState.SignedOut, fixture.repository.restoreSession())
    }
}
