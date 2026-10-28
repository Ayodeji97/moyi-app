package com.moyi.app.data.session

import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.UnknownHostException

/**
 * OkHttp's failures before any byte of the request is written: DNS
 * ([UnknownHostException]), a refused or failed connect ([ConnectException],
 * which Ktor's ConnectTimeoutException also extends on the JVM) and no route.
 * Everything else, notably `SocketException: Connection reset` and
 * `EOFException`, can happen after the request was sent: Ambiguous.
 */
internal actual fun platformRefreshFailureKind(thrown: Throwable): RefreshFailureKind? = when (thrown) {
    is UnknownHostException, is ConnectException, is NoRouteToHostException -> RefreshFailureKind.Safe
    else -> null
}
