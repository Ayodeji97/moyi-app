package com.moyi.app.data.network

import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.request
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode

/**
 * The signed-in routes that check a password the person typed, so a `401` from them can mean
 * "wrong password" and not "refused token" (docs/backend-contract-gaps.md, row 33). Only
 * `POST /me/deletion-request` (FR-008, ADR-0058) today.
 */
private val passwordCheckingRoutes: Set<Pair<HttpMethod, String>> = setOf(
    HttpMethod.Post to "/me/deletion-request",
)

/**
 * True when [this] `401` refused the password in the body, not the access token: it came from
 * one of [passwordCheckingRoutes] and carries no `WWW-Authenticate` challenge.
 *
 * The backend answers a refused or expired bearer token with `WWW-Authenticate: Bearer`
 * (`ProblemAuthenticationEntryPoint`), and a wrong password on these routes without one
 * (`DeletionPasswordRefusedException`, "the session is fine; do not sign out"). Ktor's bearer
 * provider refreshes on any `401` when it is the only provider, so without this check a wrong
 * password would spend one refresh-token rotation, send the password a second time, and, were
 * that refresh refused, sign the person out.
 *
 * Narrow on purpose: everywhere else a `401` still refreshes, challenge or not, and on these
 * routes an expired token (which does carry the challenge) still refreshes and retries.
 */
internal fun HttpResponse.isPasswordRefusal(): Boolean {
    if (status != HttpStatusCode.Unauthorized || headers[HttpHeaders.WWWAuthenticate] != null) return false
    val path = request.url.encodedPath
    return passwordCheckingRoutes.any { (method, route) -> request.method == method && path.endsWith(route) }
}
