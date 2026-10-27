package com.moyi.app.data.session

import com.moyi.app.core.ApiErrorCode
import com.moyi.app.core.ApiProblem
import com.moyi.app.core.DataError
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.util.network.UnresolvedAddressException
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Safe: the refresh request never left, so the server cannot have rotated.
 * Ambiguous: it may have been received and rotated, and only the answer was lost.
 */
class RefreshFailureKindTest {
    private val noInternet = DataError.Network.NoInternet
    private val timeout = DataError.Network.Timeout

    @Test
    fun aConnectTimeoutIsSafe() =
        assertEquals(RefreshFailureKind.Safe, classifyRefreshFailure(ConnectTimeoutException("connect timed out"), timeout))

    @Test
    fun anUnresolvedHostIsSafe() =
        assertEquals(RefreshFailureKind.Safe, classifyRefreshFailure(UnresolvedAddressException(), DataError.Network.Unknown))

    @Test
    fun aSocketTimeoutIsAmbiguous() =
        assertEquals(RefreshFailureKind.Ambiguous, classifyRefreshFailure(SocketTimeoutException("read timed out"), timeout))

    @Test
    fun aRequestTimeoutIsAmbiguous() = assertEquals(
        RefreshFailureKind.Ambiguous,
        classifyRefreshFailure(HttpRequestTimeoutException("/api/v1/auth/refresh", 30_000), timeout),
    )

    @Test
    fun anUnrecognisedIoFailureIsAmbiguous() =
        assertEquals(RefreshFailureKind.Ambiguous, classifyRefreshFailure(IOException("Connection reset"), noInternet))

    @Test
    fun aProblemAnswerFromTheAppIsSafe() = assertEquals(
        RefreshFailureKind.Safe,
        classifyRefreshFailure(null, DataError.Network.Api(ApiProblem(429, ApiErrorCode.Unknown))),
    )

    @Test
    fun aSuccessWhoseBodyCouldNotBeReadIsAmbiguous() =
        assertEquals(RefreshFailureKind.Ambiguous, classifyRefreshFailure(null, DataError.Network.Serialization))

    @Test
    fun aGatewayErrorPageIsAmbiguous() =
        assertEquals(RefreshFailureKind.Ambiguous, classifyRefreshFailure(null, DataError.Network.Server(504)))
}
