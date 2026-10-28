package com.moyi.app.data.network

import com.moyi.app.core.DataError
import com.moyi.app.core.Result
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SafeApiCallTest {
    @Test
    fun anIOExceptionIsNoInternet() = runTest {
        val result = safeApiCall<String> { throw IOException("Connection refused") }
        assertEquals(Result.Failure(DataError.Network.NoInternet), result)
    }

    @Test
    fun aRequestTimeoutIsTimeout() = runTest {
        val result = safeApiCall<String> { throw HttpRequestTimeoutException("http://localhost:8080/api/v1/auth/login", 30_000) }
        assertEquals(Result.Failure(DataError.Network.Timeout), result)
    }

    @Test
    fun cancellationIsRethrownNotMapped() = runTest {
        assertFailsWith<CancellationException> {
            safeApiCall<String> { throw CancellationException("screen closed") }
        }
        assertFailsWith<CancellationException> {
            safeApiCallEmpty { throw CancellationException("screen closed") }
        }
    }

    @Test
    fun anythingElseIsUnknown() = runTest {
        val result = safeApiCall<String> { throw IllegalStateException("boom") }
        assertEquals(Result.Failure(DataError.Network.Unknown), result)
    }
}
