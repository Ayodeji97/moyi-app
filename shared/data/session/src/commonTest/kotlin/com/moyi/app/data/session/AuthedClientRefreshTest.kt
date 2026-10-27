@file:OptIn(ExperimentalAtomicApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.moyi.app.data.session

import com.moyi.app.api.models.UserResponse
import com.moyi.app.core.ApiErrorCode
import com.moyi.app.core.ApiProblem
import com.moyi.app.core.DataError
import com.moyi.app.core.Result
import com.moyi.app.data.network.safeApiCall
import com.moyi.app.data.testing.respondJson
import com.moyi.app.data.testing.respondProblem
import com.moyi.app.data.testing.storedSession
import com.moyi.app.data.testing.tokenPairJson
import com.moyi.app.data.testing.userJson
import com.moyi.app.domain.auth.SessionEndReason
import com.moyi.app.domain.auth.SessionEvent
import com.moyi.app.domain.auth.SessionState
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
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
import kotlin.test.fail

/**
 * The authed HttpClient and SessionManager together, over MockEngine. The
 * fake server refuses any access token but the current one with
 * `401 UNAUTHENTICATED` and `WWW-Authenticate: Bearer`, as the backend does.
 */
class AuthedClientRefreshTest {
    private val unauthenticated = DataError.Network.Api(ApiProblem(401, ApiErrorCode.UNAUTHENTICATED))

    @Test
    fun tenConcurrentRequestsWithAnExpiredTokenShareOneRefreshAndAllRetryWithTheNewToken() = runTest {
        val refused = AtomicInt(0)
        val allTenRefused = CompletableDeferred<Unit>()
        val server = TestServer(initial = null) { request ->
            when {
                // Held until all ten have been refused, so they are all
                // waiting on a refresh at the same time.
                request.isRefresh -> {
                    allTenRefused.await()
                    respondJson(tokenPairJson("a2", "r2"))
                }
                request.isMe && request.bearer == "a2" -> respondJson(userJson())
                request.isMe -> {
                    if (refused.incrementAndFetch() == 10) allTenRefused.complete(Unit)
                    respondProblem(HttpStatusCode.Unauthorized, "UNAUTHENTICATED", bearerChallenge = true)
                }
                else -> fail("unexpected ${request.url}")
            }
        }
        server.sessionManager.startSession(grant("a1", "r1"))

        val results = List(10) { async { safeApiCall { server.me.me() } } }.awaitAll()

        results.forEach { assertEquals("Ada", assertIs<Result.Success<UserResponse>>(it).data.displayName) }
        assertEquals(1, server.refreshCalls.load())
        val log = server.log.snapshot()
        assertEquals(10, log.count { it == "GET /api/v1/me Bearer a1" })
        assertEquals(10, log.count { it == "GET /api/v1/me Bearer a2" })

        // (b) The rotated refresh token was stored before ANY request used the new access token.
        val stored = log.indexOf("store.write(r2)")
        val firstRetry = log.indexOf("GET /api/v1/me Bearer a2")
        assertTrue(stored in 0..<firstRetry, "store.write(r2) must come before the first retry:\n${log.joinToString("\n")}")
    }

    @Test
    fun aLate401ForAnAlreadyReplacedTokenCausesNoSecondRefresh() = runTest {
        // Request B goes out with a1 and is held at the server. Meanwhile the
        // session refreshes to a2 by another path (cold start, another
        // client), which Ktor's own bookkeeping never sees. Then B's 401
        // arrives. Its stale token must be read from B's own header (a1), so
        // the session sees a1 is already replaced and hands back a2. Reading
        // Ktor's `oldTokens` instead would say a2, and rotate again.
        val bRefusedLate = CompletableDeferred<Unit>()
        val bArrived = CompletableDeferred<Unit>()
        val server = TestServer(initial = null) { request ->
            when {
                request.isRefresh -> respondJson(tokenPairJson("a2", "r2"))
                request.isMe && request.bearer == "a2" -> respondJson(userJson())
                request.isMe -> {
                    bArrived.complete(Unit)
                    bRefusedLate.await()
                    respondProblem(HttpStatusCode.Unauthorized, "UNAUTHENTICATED", bearerChallenge = true)
                }
                else -> fail("unexpected ${request.url}")
            }
        }
        server.sessionManager.startSession(grant("a1", "r1"))

        val b = async { safeApiCall { server.me.me() } }
        bArrived.await()
        assertEquals("a2", server.sessionManager.refresh(staleAccess = "a1"))
        bRefusedLate.complete(Unit)

        assertIs<Result.Success<UserResponse>>(b.await())
        assertEquals(1, server.refreshCalls.load())
        assertTrue("GET /api/v1/me Bearer a2" in server.log.snapshot())
    }

    @Test
    fun aLate401FromThePreviousAccountIsNotRetriedWithTheNewAccountsToken() = runTest {
        // A request goes out as account A (a1) and is held at the server. A
        // signs out and B signs in (b1). Then A's 401 arrives. a1 was never
        // issued in B's session, so it must not be retried as B, and no
        // refresh may be spent on it.
        val aRefusedLate = CompletableDeferred<Unit>()
        val aArrived = CompletableDeferred<Unit>()
        val server = TestServer(initial = null) { request ->
            when {
                request.isRefresh -> fail("no refresh expected")
                request.isMe && request.bearer == "a1" -> {
                    aArrived.complete(Unit)
                    aRefusedLate.await()
                    respondProblem(HttpStatusCode.Unauthorized, "UNAUTHENTICATED", bearerChallenge = true)
                }
                request.isMe -> respondJson(userJson())
                else -> fail("unexpected ${request.url}")
            }
        }
        server.sessionManager.startSession(grant("a1", "ra"))

        val aRequest = async { safeApiCall { server.me.me() } }
        aArrived.await()
        server.sessionManager.endSession(SessionEndReason.SignedOut)
        server.sessionManager.startSession(grant("b1", "rb"))
        aRefusedLate.complete(Unit)

        assertEquals(Result.Failure(unauthenticated), aRequest.await())
        assertEquals(0, server.refreshCalls.load())
        assertTrue("GET /api/v1/me Bearer b1" !in server.log.snapshot(), server.log.snapshot().joinToString("\n"))
        assertEquals("b1", server.sessionManager.currentAccessToken())
    }

    @Test
    fun aRequestRefusedWhileColdStartIsRefreshingWaitsForThatRefresh() = runTest {
        val refreshStarted = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val server = TestServer(initial = storedSession("r1"), dispatcher = StandardTestDispatcher(testScheduler)) { request ->
            when {
                request.isRefresh -> {
                    refreshStarted.complete(Unit)
                    release.await()
                    respondJson(tokenPairJson("a2", "r2"))
                }
                request.isMe && request.bearer == "a2" -> respondJson(userJson())
                request.isMe -> respondProblem(HttpStatusCode.Unauthorized, "UNAUTHENTICATED", bearerChallenge = true)
                else -> fail("unexpected ${request.url}")
            }
        }

        val restore = async { server.sessionManager.restore() }
        refreshStarted.await()
        // No access token yet: the request goes without one, is refused, and
        // asks the session for a refresh while restore's is still on the wire.
        val request = async { safeApiCall { server.me.me() } }
        runCurrent()
        release.complete(Unit)

        assertEquals(SessionState.SignedIn(ada), restore.await())
        assertIs<Result.Success<UserResponse>>(request.await())
        assertEquals(1, server.refreshCalls.load())
    }

    @Test
    fun reuseDetectedEndsTheSessionAndTheRequestFailsAsUnauthenticated() = runTest {
        val server = TestServer(initial = null) { request ->
            when {
                request.isRefresh -> respondProblem(HttpStatusCode.Unauthorized, "TOKEN_REUSE_DETECTED")
                request.isMe -> respondProblem(HttpStatusCode.Unauthorized, "UNAUTHENTICATED", bearerChallenge = true)
                else -> fail("unexpected ${request.url}")
            }
        }
        server.sessionManager.startSession(grant("a1", "r1"))
        val events = collectEvents(server.sessionManager)

        val result = safeApiCall { server.me.me() }

        assertEquals(Result.Failure(unauthenticated), result)
        assertNull(server.store.current)
        assertNull(server.sessionManager.currentAccessToken())
        assertEquals(SessionState.SignedOut, server.sessionManager.sessionState.value)
        assertEquals(listOf<SessionEvent>(SessionEvent.Ended(SessionEndReason.ReuseDetected)), events)
        assertEquals(1, server.refreshCalls.load())
    }

    @Test
    fun anInvalidRefreshTokenEndsTheSessionAsInvalid() = runTest {
        val server = TestServer(initial = null) { request ->
            if (request.isRefresh) {
                respondProblem(HttpStatusCode.Unauthorized, "REFRESH_TOKEN_INVALID")
            } else {
                respondProblem(HttpStatusCode.Unauthorized, "UNAUTHENTICATED", bearerChallenge = true)
            }
        }
        server.sessionManager.startSession(grant("a1", "r1"))
        val events = collectEvents(server.sessionManager)

        assertEquals(Result.Failure(unauthenticated), safeApiCall { server.me.me() })
        assertNull(server.store.current)
        assertEquals(listOf<SessionEvent>(SessionEvent.Ended(SessionEndReason.Invalid)), events)
    }

    @Test
    fun aRefreshThatCannotReachTheServerKeepsTheSessionAndTheRequestFailsAsNoInternet() = runTest {
        val server = TestServer(initial = null) { request ->
            when {
                request.isRefresh -> throw IOException("Network is unreachable")
                else -> respondProblem(HttpStatusCode.Unauthorized, "UNAUTHENTICATED", bearerChallenge = true)
            }
        }
        server.sessionManager.startSession(grant("a1", "r1"))
        val events = collectEvents(server.sessionManager)
        val writesBefore = server.store.writes

        assertEquals(Result.Failure(DataError.Network.NoInternet), safeApiCall { server.me.me() })

        assertEquals("r1", server.store.current?.refreshToken)
        assertEquals(writesBefore, server.store.writes)
        assertEquals("a1", server.sessionManager.currentAccessToken())
        assertEquals(SessionState.SignedIn(ada), server.sessionManager.sessionState.value)
        assertEquals(emptyList(), events)
    }

    @Test
    fun aRefreshThatHitsA503KeepsTheSessionAndReportsTheServerError() = runTest {
        val server = TestServer(initial = null) { request ->
            when {
                request.isRefresh -> respondJson("<html>down</html>", HttpStatusCode.ServiceUnavailable)
                else -> respondProblem(HttpStatusCode.Unauthorized, "UNAUTHENTICATED", bearerChallenge = true)
            }
        }
        server.sessionManager.startSession(grant("a1", "r1"))
        val events = collectEvents(server.sessionManager)

        val result = safeApiCall { server.me.me() }

        assertIs<Result.Failure<DataError.Network>>(result)
        assertEquals("r1", server.store.current?.refreshToken)
        assertEquals(emptyList(), events)
    }

    @Test
    fun withNoSessionARequestGoesWithoutATokenAndNothingIsRefreshed() = runTest {
        val server = TestServer(initial = null) { request ->
            if (request.isRefresh) fail("no refresh without a stored token")
            respondProblem(HttpStatusCode.Unauthorized, "UNAUTHENTICATED", bearerChallenge = true)
        }

        assertEquals(Result.Failure(unauthenticated), safeApiCall { server.me.me() })
        assertEquals(listOf("GET /api/v1/me no-auth"), server.log.snapshot())
    }
}
