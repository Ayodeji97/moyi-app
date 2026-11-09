@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.moyi.app.data.repository

import com.moyi.app.api.apis.RefreshControllerApi
import com.moyi.app.api.models.DeviceRequest
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
import com.moyi.app.data.testing.tokenPairJson
import com.moyi.app.domain.auth.SessionEndReason
import com.moyi.app.domain.auth.SessionState
import com.moyi.app.domain.notification.PushTokenSource
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

/**
 * [PushTokenRegistrar] with a fake [PushTokenSource] and the real session and authed
 * client over a MockEngine: the token is `PUT` once someone is signed in and there is
 * one, again when it rotates, never without one; and a voluntary sign-out `DELETE`s it
 * with the session's bearer BEFORE the session's tokens are cleared.
 */
class PushTokenRegistrarTest {
    private val config = ApiConfig("http://localhost:8080/api/v1")

    private class FakeTokenSource(initial: String?) : PushTokenSource {
        override val token = MutableStateFlow(initial)
    }

    private inner class Fixture(
        scope: TestScope,
        token: String?,
        val pushHandler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = { respond("", HttpStatusCode.NoContent) },
    ) {
        val log = EventLog()
        val store = InMemoryTokenStore(log = log)
        val requests = mutableListOf<HttpRequestData>()
        // The engine on the test's own thread, so every call has answered before an assertion
        // runs, and the sign-out's time limit is measured on the same (virtual) clock.
        val engine = MockEngine(
            MockEngineConfig().apply {
                dispatcher = UnconfinedTestDispatcher(scope.testScheduler)
                addHandler { request ->
                    requests += request
                    log.add("${request.method.value} ${request.url.encodedPath} ${request.headers[HttpHeaders.Authorization] ?: "no-auth"}")
                    when (request.url.encodedPath) {
                        "/api/v1/auth/login" -> respondJson(tokenPairJson("a1", "r1"))
                        "/api/v1/auth/logout" -> respond("", HttpStatusCode.NoContent)
                        PUSH_TOKEN -> pushHandler(request)
                        else -> respondProblem(HttpStatusCode.Unauthorized, "REFRESH_TOKEN_INVALID")
                    }
                }
            },
        )
        val source = FakeTokenSource(token)
        val publicClient = PublicHttpClient(HttpClientFactory.createPublicClient(engine))
        val sessionManager = SessionManager(store, RefreshControllerApi(config.origin, publicClient.client), NoPersonalData)
        val registrar = PushTokenRegistrar(
            source = source,
            authedClient = AuthedHttpClient(HttpClientFactory.createAuthedClient(engine, sessionManager)),
            config = config,
            session = sessionManager.sessionState,
            scope = scope.backgroundScope + UnconfinedTestDispatcher(scope.testScheduler),
        )
        val repository = AuthRepositoryImpl(publicClient, config, sessionManager, pushTokens = registrar) {
            DeviceInfo(platform = DeviceRequest.Platform.ANDROID, appVersion = "1.0", osVersion = "16")
        }

        fun pushCalls(): List<HttpRequestData> = requests.filter { it.url.encodedPath == PUSH_TOKEN }
    }

    @Test
    fun aSignedInSessionWithATokenRegistersItWithTheBearer() = runTest {
        val fixture = Fixture(this, token = "fcm-1")
        fixture.registrar.start()

        fixture.repository.login("ada@example.test", "pw")

        val put = fixture.pushCalls().single()
        assertEquals(HttpMethod.Put, put.method)
        assertEquals("Bearer a1", put.headers[HttpHeaders.Authorization])
        assertEquals("""{"token":"fcm-1"}""", assertIs<TextContent>(put.body).text)
    }

    @Test
    fun withoutATokenNothingIsSent() = runTest {
        val fixture = Fixture(this, token = null)
        fixture.registrar.start()

        fixture.repository.login("ada@example.test", "pw")
        fixture.registrar.retry()

        assertTrue(fixture.pushCalls().isEmpty())
    }

    @Test
    fun nothingIsSentWhileNobodyIsSignedIn() = runTest {
        val fixture = Fixture(this, token = "fcm-1")
        fixture.registrar.start()

        fixture.source.token.value = "fcm-2"

        assertTrue(fixture.pushCalls().isEmpty())
    }

    @Test
    fun aRotatedTokenIsRegisteredAgainAndTheSameOneIsNotSentTwice() = runTest {
        val fixture = Fixture(this, token = "fcm-1")
        fixture.registrar.start()
        fixture.repository.login("ada@example.test", "pw")

        fixture.registrar.retry() // the same token, already registered: nothing
        fixture.source.token.value = "fcm-2"

        val bodies = fixture.pushCalls().map { assertIs<TextContent>(it.body).text }
        assertEquals(listOf("""{"token":"fcm-1"}""", """{"token":"fcm-2"}"""), bodies)
    }

    @Test
    fun aTokenThatArrivesAfterSignInIsRegisteredThen() = runTest {
        val fixture = Fixture(this, token = null)
        fixture.registrar.start()
        fixture.repository.login("ada@example.test", "pw")

        fixture.source.token.value = "fcm-1"

        assertEquals(1, fixture.pushCalls().size)
    }

    @Test
    fun aFailedRegistrationIsTriedAgainOnTheNextForeground() = runTest {
        var answers = 0
        val fixture = Fixture(this, token = "fcm-1") {
            if (answers++ == 0) respondProblem(HttpStatusCode.ServiceUnavailable, "INTERNAL_ERROR") else respond("", HttpStatusCode.NoContent)
        }
        fixture.registrar.start()
        fixture.repository.login("ada@example.test", "pw")

        fixture.registrar.retry()
        fixture.registrar.retry() // registered now: nothing more

        assertEquals(2, fixture.pushCalls().size)
    }

    @Test
    fun aVoluntarySignOutDeletesTheTokenWithTheBearerBeforeTheSessionIsCleared() = runTest {
        val fixture = Fixture(this, token = "fcm-1")
        fixture.registrar.start()
        fixture.repository.login("ada@example.test", "pw")

        fixture.repository.logout()

        val log = fixture.log.snapshot()
        val delete = log.indexOf("DELETE $PUSH_TOKEN Bearer a1")
        assertTrue(delete >= 0, log.joinToString("\n"))
        assertTrue(delete < log.indexOf("store.clear"), log.joinToString("\n"))
        assertTrue(delete < log.indexOfFirst { it.startsWith("POST /api/v1/auth/logout") }, log.joinToString("\n"))
        assertEquals(SessionState.SignedOut, fixture.repository.session.value)
        assertNull(fixture.store.current)
    }

    @Test
    fun aSignOutWithNoTokenSendsNoDelete() = runTest {
        val fixture = Fixture(this, token = null)
        fixture.registrar.start()
        fixture.repository.login("ada@example.test", "pw")

        fixture.repository.logout()

        assertTrue(fixture.pushCalls().isEmpty())
        assertEquals(SessionState.SignedOut, fixture.repository.session.value)
    }

    @Test
    fun aDeleteThatFailsStillSignsOut() = runTest {
        val fixture = Fixture(this, token = "fcm-1") { request ->
            if (request.method == HttpMethod.Delete) throw kotlinx.io.IOException("offline") else respond("", HttpStatusCode.NoContent)
        }
        fixture.registrar.start()
        fixture.repository.login("ada@example.test", "pw")

        fixture.repository.logout()

        assertEquals(SessionState.SignedOut, fixture.repository.session.value)
        assertNull(fixture.store.current)
    }

    @Test
    fun aDeleteThatNeverAnswersIsGivenUpAndTheSignOutGoesOn() = runTest {
        val never = CompletableDeferred<Unit>()
        val fixture = Fixture(this, token = "fcm-1") { request ->
            if (request.method == HttpMethod.Delete) never.await()
            respond("", HttpStatusCode.NoContent)
        }
        fixture.registrar.start()
        fixture.repository.login("ada@example.test", "pw")

        fixture.repository.logout()

        assertEquals(SessionState.SignedOut, fixture.repository.session.value)
        assertNull(fixture.store.current)
    }

    @Test
    fun afterAnInvoluntaryEndTheNextSignInRegistersAgain() = runTest {
        val fixture = Fixture(this, token = "fcm-1")
        fixture.registrar.start()
        fixture.repository.login("ada@example.test", "pw")

        // The server ended the session (and forgot the token with it, ADR-0044): no API call is possible.
        fixture.sessionManager.endSession(SessionEndReason.Invalid)
        fixture.repository.login("ada@example.test", "pw")

        assertEquals(listOf(HttpMethod.Put, HttpMethod.Put), fixture.pushCalls().map { it.method })
    }

    private companion object {
        const val PUSH_TOKEN = "/api/v1/me/devices/current/push-token"
    }
}
