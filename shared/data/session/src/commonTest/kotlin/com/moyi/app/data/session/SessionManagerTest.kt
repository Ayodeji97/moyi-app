@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.moyi.app.data.session

import com.moyi.app.data.testing.respondJson
import com.moyi.app.data.testing.respondProblem
import com.moyi.app.data.testing.storedSession
import com.moyi.app.data.testing.tokenPairJson
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.fail

/** SessionManager on its own: the single-flight guarantee, ordering, and cold start. */
class SessionManagerTest {
    @Test
    fun tenConcurrentRefreshesOfOneStaleTokenMakeExactlyOneNetworkCall() = runTest {
        val release = CompletableDeferred<Unit>()
        val server = TestServer(initial = null, dispatcher = StandardTestDispatcher(testScheduler)) { request ->
            if (!request.isRefresh) fail("unexpected ${request.url}")
            release.await()
            respondJson(tokenPairJson("a2", "r2"))
        }
        server.sessionManager.startSession(grant("a1", "r1"))

        val callers = List(10) { async { server.sessionManager.refresh(staleAccess = "a1") } }
        // Let all ten run as far as they can while the first refresh is held
        // at the server: without the Mutex every one of them would be on the
        // wire by now.
        runCurrent()
        release.complete(Unit)

        assertEquals(List(10) { "a2" }, callers.awaitAll())
        assertEquals(1, server.refreshCalls.load())
        assertEquals("r2", server.store.current?.refreshToken)
    }

    @Test
    fun aLateCallerWithAnAlreadyReplacedTokenGetsTheCurrentOneWithoutARefresh() = runTest {
        val server = TestServer(initial = null) { respondJson(tokenPairJson("a2", "r2")) }
        server.sessionManager.startSession(grant("a1", "r1"))

        assertEquals("a2", server.sessionManager.refresh("a1"))
        assertEquals("a2", server.sessionManager.refresh("a1"))

        assertEquals(1, server.refreshCalls.load())
    }

    @Test
    fun aRefusedCurrentTokenIsRefreshedAgain() = runTest {
        var next = 1
        val server = TestServer(initial = null) { next++; respondJson(tokenPairJson("a${next}", "r$next")) }
        server.sessionManager.startSession(grant("a1", "r1"))

        assertEquals("a2", server.sessionManager.refresh("a1"))
        assertEquals("a3", server.sessionManager.refresh("a2"))
        assertEquals(2, server.refreshCalls.load())
        assertEquals("r3", server.store.current?.refreshToken)
    }

    @Test
    fun theNewRefreshTokenIsStoredBeforeTheNewAccessTokenIsPublished() = runTest {
        val server = TestServer(initial = null) { respondJson(tokenPairJson("a2", "r2")) }
        server.sessionManager.startSession(grant("a1", "r1"))
        val publishedAtWrite = mutableListOf<String?>()
        server.onStoreWrite = { publishedAtWrite += server.sessionManager.currentAccessToken() }

        server.sessionManager.refresh("a1")

        // While r2 was being written, the access token in use was still a1.
        assertEquals(listOf<String?>("a1"), publishedAtWrite)
        assertEquals("a2", server.sessionManager.currentAccessToken())
    }

    @Test
    fun startSessionStoresTheRefreshTokenBeforePublishingTheAccessToken() = runTest {
        val server = TestServer(initial = null) { fail("no network expected") }
        val publishedAtWrite = mutableListOf<String?>()
        server.onStoreWrite = { publishedAtWrite += server.sessionManager.currentAccessToken() }

        server.sessionManager.startSession(grant("a1", "r1"))

        assertEquals(listOf<String?>(null), publishedAtWrite)
        assertEquals("a1", server.sessionManager.currentAccessToken())
        assertEquals(SessionState.SignedIn(ada), server.sessionManager.sessionState.value)
    }

    @Test
    fun aNetworkErrorDuringRefreshKeepsTheSession() = runTest {
        val server = TestServer(initial = null) { throw IOException("offline") }
        server.sessionManager.startSession(grant("a1", "r1"))
        val events = collectEvents(server.sessionManager)

        assertNull(server.sessionManager.refresh("a1"))

        assertEquals("r1", server.store.current?.refreshToken)
        assertEquals("a1", server.sessionManager.currentAccessToken())
        assertEquals(SessionState.SignedIn(ada), server.sessionManager.sessionState.value)
        assertEquals(emptyList(), events)
    }

    @Test
    fun aRotatedTokenThatCannotBeStoredEndsTheSessionInsteadOfRiskingReuse() = runTest {
        val server = TestServer(initial = null) { respondJson(tokenPairJson("a2", "r2")) }
        server.sessionManager.startSession(grant("a1", "r1"))
        val events = collectEvents(server.sessionManager)
        server.store.failWrites = true

        assertNull(server.sessionManager.refresh("a1"))

        assertNull(server.sessionManager.currentAccessToken())
        assertEquals(SessionState.SignedOut, server.sessionManager.sessionState.value)
        assertEquals(listOf<SessionEvent>(SessionEvent.Ended(SessionEndReason.Invalid)), events)
    }

    @Test
    fun restoreWithAStoredTokenRefreshesOnceAndSignsIn() = runTest {
        val server = TestServer(initial = storedSession("r1")) { request ->
            if (!request.isRefresh) fail("unexpected ${request.url}")
            respondJson(tokenPairJson("a2", "r2"))
        }
        assertEquals(SessionState.Unknown, server.sessionManager.sessionState.value)

        assertEquals(SessionState.SignedIn(ada), server.sessionManager.restore())

        assertEquals(1, server.refreshCalls.load())
        assertEquals("a2", server.sessionManager.currentAccessToken())
        assertEquals("r2", server.store.current?.refreshToken)
    }

    @Test
    fun restoreWithNoStoredTokenIsSignedOutAndSendsNothing() = runTest {
        val server = TestServer(initial = null) { request -> fail("no network expected, got ${request.url}") }

        assertEquals(SessionState.SignedOut, server.sessionManager.restore())
        assertEquals(emptyList(), server.log.snapshot())
    }

    @Test
    fun restoreOfflineKeepsThePersonSignedInWithTheStoredUser() = runTest {
        val server = TestServer(initial = storedSession("r1")) { throw IOException("offline") }

        assertEquals(SessionState.SignedIn(ada), server.sessionManager.restore())
        assertEquals("r1", server.store.current?.refreshToken)
        assertNull(server.sessionManager.currentAccessToken())
    }

    @Test
    fun restoreWithALockedStoreStaysUnknownAndCanBeCalledAgain() = runTest {
        val server = TestServer(initial = storedSession("r1")) { request ->
            if (!request.isRefresh) fail("unexpected ${request.url}")
            respondJson(tokenPairJson("a2", "r2"))
        }
        server.store.readFailure = TokenStoreUnavailableException("Keychain locked")
        val events = collectEvents(server.sessionManager)

        // Locked: not signed out, nothing sent, nothing wiped.
        assertEquals(SessionState.Unknown, server.sessionManager.restore())
        assertEquals(SessionState.Unknown, server.sessionManager.sessionState.value)
        assertEquals(0, server.refreshCalls.load())
        assertEquals("r1", server.store.current?.refreshToken)
        assertEquals(emptyList(), events)

        // Unlocked (the app came back to the foreground): the same call resumes.
        server.store.readFailure = null
        assertEquals(SessionState.SignedIn(ada), server.sessionManager.restore())
        assertEquals(1, server.refreshCalls.load())
    }

    @Test
    fun restoreOnceDecidedDoesNothingMore() = runTest {
        val server = TestServer(initial = storedSession("r1")) { respondJson(tokenPairJson("a2", "r2")) }

        assertEquals(SessionState.SignedIn(ada), server.sessionManager.restore())
        assertEquals(SessionState.SignedIn(ada), server.sessionManager.restore())

        assertEquals(1, server.refreshCalls.load())
    }

    @Test
    fun aRefreshWithALockedStoreKeepsTheSession() = runTest {
        val server = TestServer(initial = null) { fail("no network expected") }
        server.sessionManager.startSession(grant("a1", "r1"))
        val events = collectEvents(server.sessionManager)
        server.store.readFailure = TokenStoreUnavailableException("Keychain locked")

        assertNull(server.sessionManager.refresh("a1"))

        assertEquals(SessionState.SignedIn(ada), server.sessionManager.sessionState.value)
        assertEquals("a1", server.sessionManager.currentAccessToken())
        assertEquals(emptyList(), events)
    }

    @Test
    fun theStoredRefreshTokenOfALockedStoreIsNullSoSignOutStillWipesLocally() = runTest {
        val server = TestServer(initial = null) { fail("no network expected") }
        server.sessionManager.startSession(grant("a1", "r1"))
        server.store.readFailure = TokenStoreUnavailableException("Keychain locked")

        assertNull(server.sessionManager.storedRefreshToken())
    }

    @Test
    fun restoreOfARevokedSessionIsSignedOut() = runTest {
        val server = TestServer(initial = storedSession("r1")) {
            respondProblem(HttpStatusCode.Unauthorized, "REFRESH_TOKEN_INVALID")
        }
        val events = collectEvents(server.sessionManager)

        assertEquals(SessionState.SignedOut, server.sessionManager.restore())
        assertNull(server.store.current)
        assertEquals(listOf<SessionEvent>(SessionEvent.Ended(SessionEndReason.Invalid)), events)
    }
}
