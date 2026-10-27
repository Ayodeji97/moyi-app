package com.moyi.app.data.session

import com.moyi.app.core.DataError
import io.ktor.client.engine.darwin.DarwinHttpRequestException
import platform.Foundation.NSError
import platform.Foundation.NSURLErrorCannotConnectToHost
import platform.Foundation.NSURLErrorCannotFindHost
import platform.Foundation.NSURLErrorDNSLookupFailed
import platform.Foundation.NSURLErrorDomain
import platform.Foundation.NSURLErrorNetworkConnectionLost
import platform.Foundation.NSURLErrorNotConnectedToInternet
import kotlin.test.Test
import kotlin.test.assertEquals

/** The NSURLError codes the Darwin engine wraps in DarwinHttpRequestException. */
class RefreshFailureKindIosTest {
    private fun darwin(code: Long) = DarwinHttpRequestException(NSError.errorWithDomain(NSURLErrorDomain, code, null))

    private fun kind(code: Long) = classifyRefreshFailure(darwin(code), DataError.Network.NoInternet)

    @Test
    fun cannotConnectIsSafe() = assertEquals(RefreshFailureKind.Safe, kind(NSURLErrorCannotConnectToHost))

    @Test
    fun cannotFindHostIsSafe() = assertEquals(RefreshFailureKind.Safe, kind(NSURLErrorCannotFindHost))

    @Test
    fun dnsLookupFailedIsSafe() = assertEquals(RefreshFailureKind.Safe, kind(NSURLErrorDNSLookupFailed))

    @Test
    fun notConnectedIsSafe() = assertEquals(RefreshFailureKind.Safe, kind(NSURLErrorNotConnectedToInternet))

    @Test
    fun aLostConnectionIsAmbiguous() = assertEquals(RefreshFailureKind.Ambiguous, kind(NSURLErrorNetworkConnectionLost))
}
