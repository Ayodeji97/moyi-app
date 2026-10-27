package com.moyi.app.data.testing

import com.moyi.app.data.session.StoredSession
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf

const val USER_ID = "7c9e6679-7425-40de-944b-e07fc1f90ae7"

fun userJson(displayName: String = "Ada"): String =
    """{"id":"$USER_ID","email":"ada@example.test","displayName":"$displayName","emailVerified":true,"status":"ACTIVE"}"""

/** The body of a 200 from login or refresh. */
fun tokenPairJson(access: String, refresh: String, displayName: String = "Ada"): String =
    """{"accessToken":"$access","expiresIn":900,"refreshToken":"$refresh","user":${userJson(displayName)}}"""

fun storedSession(refresh: String = "r1", displayName: String = "Ada"): StoredSession = StoredSession(
    refreshToken = refresh,
    userId = USER_ID,
    email = "ada@example.test",
    displayName = displayName,
    emailVerified = true,
    status = "ACTIVE",
)

fun MockRequestHandleScope.respondJson(body: String, status: HttpStatusCode = HttpStatusCode.OK): HttpResponseData =
    respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

/**
 * A problem response (doc 06 §2). [bearerChallenge] adds `WWW-Authenticate:
 * Bearer`, as the resource server does on an expired access token
 * (ADR-0019); login's 401 carries none.
 */
fun MockRequestHandleScope.respondProblem(
    status: HttpStatusCode,
    code: String,
    bearerChallenge: Boolean = false,
): HttpResponseData {
    val headers = buildList {
        add(HttpHeaders.ContentType to listOf("application/problem+json"))
        if (bearerChallenge) add(HttpHeaders.WWWAuthenticate to listOf("Bearer"))
    }
    return respond(
        """{"type":"about:blank","title":"${status.description}","status":${status.value},"code":"$code"}""",
        status,
        io.ktor.http.headersOf(*headers.toTypedArray()),
    )
}
