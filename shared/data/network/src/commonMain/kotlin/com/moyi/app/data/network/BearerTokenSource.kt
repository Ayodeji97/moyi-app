package com.moyi.app.data.network

import com.moyi.app.core.DataError

/**
 * Where the authed client gets its access token, and how it gets a new one.
 *
 * Network defines this port and the session module implements it
 * (`SessionManager`), so the HttpClient can refresh without network depending
 * on session: the dependency points from session to network, never back.
 */
interface BearerTokenSource {
    /** The access token to send now, or null to send the request without one. */
    fun currentAccessToken(): String?

    /**
     * Called when a request answered `401`. [staleAccessToken] is the token
     * THAT request carried (null if it carried none), so an implementation can
     * tell "someone already refreshed past this token" from "this token is the
     * current one and needs replacing".
     *
     * Returns the access token to retry with, or null when there is none: no
     * session, or the server ended it. The original `401` then stands.
     *
     * @throws RefreshUnavailableException when the refresh itself could not
     *   be done (offline, timeout, 5xx). The request fails with that error
     *   instead of a misleading `401`, and the session is kept.
     */
    suspend fun refreshAccessToken(staleAccessToken: String?): String?
}

/**
 * The refresh needed to answer a `401` could not be made. Thrown out of
 * [BearerTokenSource.refreshAccessToken], through Ktor, and turned back into
 * [error] by [safeApiCall], so a request that hit a dead network while
 * refreshing fails as [DataError.Network.NoInternet], not as unauthenticated.
 */
class RefreshUnavailableException(val error: DataError.Network) : Exception("Token refresh unavailable: $error")
