package com.moyi.app.data.session

import io.ktor.client.engine.darwin.DarwinHttpRequestException
import platform.Foundation.NSURLErrorCannotConnectToHost
import platform.Foundation.NSURLErrorCannotFindHost
import platform.Foundation.NSURLErrorDNSLookupFailed
import platform.Foundation.NSURLErrorDomain
import platform.Foundation.NSURLErrorNotConnectedToInternet

/**
 * The Darwin engine wraps NSURLSession errors in [DarwinHttpRequestException]
 * (and turns `NSURLErrorTimedOut` into a SocketTimeoutException, Ambiguous).
 *
 * Safe here: host not found, DNS failed, cannot connect, not connected.
 * NSURLSession reports these before a connection exists.
 *
 * Where this is uncertain, and why it falls to Ambiguous:
 * - NSURLSession has no separate connect timeout. A timeout during connect is
 *   the same `NSURLErrorTimedOut` as one after the request was sent, so every
 *   iOS timeout counts as Ambiguous, including some that were really safe.
 * - `NSURLErrorNotConnectedToInternet` is documented as "no connection", but
 *   whether iOS can raise it for a connection that dropped mid-request is not
 *   documented. Treated as Safe; if the measurement shows reuse after it,
 *   move it to Ambiguous.
 * - `NSURLErrorNetworkConnectionLost` (-1005), secure-connection failures and
 *   any other code: Ambiguous.
 */
internal actual fun platformRefreshFailureKind(thrown: Throwable): RefreshFailureKind? {
    if (thrown !is DarwinHttpRequestException) return null
    val error = thrown.origin
    if (error.domain != NSURLErrorDomain) return RefreshFailureKind.Ambiguous
    return when (error.code) {
        NSURLErrorCannotFindHost,
        NSURLErrorDNSLookupFailed,
        NSURLErrorCannotConnectToHost,
        NSURLErrorNotConnectedToInternet,
        -> RefreshFailureKind.Safe
        else -> RefreshFailureKind.Ambiguous
    }
}
