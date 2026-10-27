package com.moyi.app.data.session

import com.moyi.app.core.DataError
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.util.network.UnresolvedAddressException

/**
 * Whether a failed `POST /auth/refresh` could have rotated the refresh token.
 *
 * - [Safe]: the request never left (no route, no DNS, connection refused, the
 *   connect itself timed out). The server never saw the old token.
 * - [Ambiguous]: the request may have been received. The server may have
 *   rotated R1 to R2 and only its answer was lost. Presenting R1 again is
 *   then a reuse (ADR-0021): the family is revoked and the person emailed.
 *
 * The behaviour is the same for both (keep R1, report no token: there is
 * nothing better to present). The difference is logged, so how often a
 * reuse follows an ambiguous failure can be measured (docs/session.md).
 */
internal enum class RefreshFailureKind { Safe, Ambiguous }

/**
 * [thrown] is what the refresh call threw, if it threw; [error] is what
 * `safeApiCall` made of the outcome. When unsure, [RefreshFailureKind.Ambiguous]:
 * calling a lost rotation "safe" would hide exactly what is being measured.
 */
internal fun classifyRefreshFailure(thrown: Throwable?, error: DataError.Network): RefreshFailureKind = when {
    thrown != null -> classifyThrown(thrown)
    // The app itself answered with a problem body: it handled the request and
    // refused it, and a refused refresh rotates nothing.
    error is DataError.Network.Api -> RefreshFailureKind.Safe
    // Serialization: a 2xx whose body could not be read, so the server DID
    // rotate. Server: a 5xx page from a proxy, which can be a gateway timeout
    // that arrived after the app committed. Unknown: no way to tell.
    else -> RefreshFailureKind.Ambiguous
}

private fun classifyThrown(thrown: Throwable): RefreshFailureKind = when (thrown) {
    // Checked before the socket timeout: the connect never completed, so
    // nothing was sent. (Darwin does not raise this one; see the iOS actual.)
    is ConnectTimeoutException -> RefreshFailureKind.Safe
    is UnresolvedAddressException -> RefreshFailureKind.Safe
    // The request (or part of it) was written and the answer did not come.
    is HttpRequestTimeoutException, is SocketTimeoutException -> RefreshFailureKind.Ambiguous
    else -> platformRefreshFailureKind(thrown) ?: RefreshFailureKind.Ambiguous
}

/** The engine-specific exceptions this platform can classify, or null to fall back to Ambiguous. */
internal expect fun platformRefreshFailureKind(thrown: Throwable): RefreshFailureKind?
