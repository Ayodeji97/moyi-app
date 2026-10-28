package com.moyi.app.data.session

import com.moyi.app.core.DataError
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.UnknownHostException
import kotlin.test.Test
import kotlin.test.assertEquals

/** The JVM exceptions OkHttp surfaces through Ktor. */
class RefreshFailureKindAndroidTest {
    private val offline = DataError.Network.NoInternet

    @Test
    fun aRefusedConnectionIsSafe() =
        assertEquals(RefreshFailureKind.Safe, classifyRefreshFailure(ConnectException("Connection refused"), offline))

    @Test
    fun anUnknownHostIsSafe() =
        assertEquals(RefreshFailureKind.Safe, classifyRefreshFailure(UnknownHostException("api.example"), offline))

    @Test
    fun noRouteToHostIsSafe() =
        assertEquals(RefreshFailureKind.Safe, classifyRefreshFailure(NoRouteToHostException("no route"), offline))

    @Test
    fun aResetConnectionIsAmbiguous() =
        assertEquals(RefreshFailureKind.Ambiguous, classifyRefreshFailure(SocketException("Connection reset"), offline))
}
