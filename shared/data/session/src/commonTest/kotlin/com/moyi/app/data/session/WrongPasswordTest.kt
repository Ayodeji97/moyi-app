@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package com.moyi.app.data.session

import com.moyi.app.api.models.DeletionRequestBody
import com.moyi.app.api.models.DeletionRequestResponse
import com.moyi.app.core.ApiErrorCode
import com.moyi.app.core.ApiProblem
import com.moyi.app.core.DataError
import com.moyi.app.core.Result
import com.moyi.app.data.network.safeApiCall
import com.moyi.app.data.testing.respondJson
import com.moyi.app.data.testing.respondProblem
import com.moyi.app.data.testing.tokenPairJson
import com.moyi.app.domain.auth.SessionState
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest

/**
 * docs/backend-contract-gaps.md, row 33: a wrong password on `POST /me/deletion-request` is a
 * `403 INVALID_CREDENTIALS` (backend #179), and `401` is only ever the token. The authed client
 * hands the 403 back as it is: no refresh, no retry (the password is sent once), and the
 * session untouched. A `401` still refreshes, on this route as on every other.
 */
class WrongPasswordTest {
    private val body = DeletionRequestBody(password = "not-the-password")
    private val deletionJson =
        """{"requestedAt":"2026-11-09T10:00:00Z","cancellableUntil":"2026-11-23T10:00:00Z","deleteBy":"2026-12-09T10:00:00Z"}"""

    private val HttpRequestData.isDeletionPost: Boolean get() = isDeletionRequest && method == HttpMethod.Post

    @Test
    fun aWrongPasswordIsA403ThatNeitherRefreshesNorSignsOutAndIsSentOnce() = runTest {
        val server = TestServer(initial = null) { request ->
            when {
                request.isRefresh -> error("a wrong password must not refresh")
                request.isDeletionPost -> respondProblem(HttpStatusCode.Forbidden, "INVALID_CREDENTIALS")
                else -> error("unexpected ${request.url}")
            }
        }
        server.sessionManager.startSession(grant("a1", "r1"))
        val events = collectEvents(server.sessionManager)
        val writesBefore = server.store.writes

        val result = safeApiCall { server.deletion.requestAccountDeletion(body) }

        assertEquals(Result.Failure(DataError.Network.Api(ApiProblem(403, ApiErrorCode.INVALID_CREDENTIALS))), result)
        assertEquals(0, server.refreshCalls.load())
        assertEquals(listOf("POST /api/v1/me/deletion-request Bearer a1"), server.log.snapshot().filter { it.startsWith("POST") })
        assertEquals("r1", server.store.current?.refreshToken)
        assertEquals(writesBefore, server.store.writes)
        assertEquals("a1", server.sessionManager.currentAccessToken())
        assertEquals(SessionState.SignedIn(ada), server.sessionManager.sessionState.value)
        assertEquals(emptyList(), events)
    }

    @Test
    fun anExpiredTokenOnTheDeletionRouteStillRefreshesAndRetries() = runTest {
        // The same route, refused for its token: a 401, so it is a session matter.
        val server = TestServer(initial = null) { request ->
            when {
                request.isRefresh -> respondJson(tokenPairJson("a2", "r2"))
                request.isDeletionPost && request.bearer == "a2" -> respondJson(deletionJson, HttpStatusCode.Accepted)
                request.isDeletionPost -> respondProblem(HttpStatusCode.Unauthorized, "UNAUTHENTICATED", bearerChallenge = true)
                else -> error("unexpected ${request.url}")
            }
        }
        server.sessionManager.startSession(grant("a1", "r1"))

        val result = safeApiCall { server.deletion.requestAccountDeletion(body) }

        assertIs<Result.Success<DeletionRequestResponse>>(result)
        assertEquals(1, server.refreshCalls.load())
        assertEquals("r2", server.store.current?.refreshToken)
    }
}
