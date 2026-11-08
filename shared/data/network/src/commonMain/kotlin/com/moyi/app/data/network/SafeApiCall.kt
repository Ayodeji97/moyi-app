package com.moyi.app.data.network

import com.moyi.app.api.infrastructure.HttpResponse
import com.moyi.app.core.ApiErrorCode
import com.moyi.app.core.DataError
import com.moyi.app.core.EmptyResult
import com.moyi.app.core.Result
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.serialization.ContentConvertException
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException
import kotlinx.serialization.SerializationException

/**
 * Runs one generated API call and returns its body or a typed [DataError.Network].
 *
 * Success is checked BEFORE the body is decoded. Most response schemas declare
 * no `required` fields (docs/backend-contract-gaps.md rows 4 and 5), so a
 * problem body decoded as, say, `LoginResponse` "succeeds" with every field
 * null. Non-2xx responses go to [ProblemDetailParser] instead.
 *
 * [CancellationException] is always rethrown: swallowing it would keep a
 * cancelled coroutine (a closed screen) running as if nothing happened.
 */
suspend fun <T : Any> safeApiCall(call: suspend () -> HttpResponse<T>): Result<T, DataError.Network> =
    execute(call) { response -> Result.Success(response.body()) }

/**
 * [safeApiCall] that also returns the response's `ETag`, or null when there is none.
 * A Bond's `ETag` is its row version (doc 06 §1), sent back as `If-Match` on a change.
 * Read from Ktor's headers, which ignore case, not the generated client's map, which does not.
 */
suspend fun <T : Any> safeApiCallWithETag(call: suspend () -> HttpResponse<T>): Result<Pair<T, String?>, DataError.Network> =
    execute(call) { response -> Result.Success(response.body() to response.response.headers[HttpHeaders.ETag]) }

/** For endpoints that answer with an empty body (`201`, `202`, `204`): only the status counts. */
suspend fun safeApiCallEmpty(call: suspend () -> HttpResponse<*>): EmptyResult<DataError.Network> =
    execute(call) { Result.Success(Unit) }

/**
 * A request to the object storage, not the API: any 2xx is success. A refusal is
 * [DataError.Network.Server] with its status whatever it is, because the storage answers
 * in S3's XML, never problem+json, and its body is not read (it can echo the request).
 */
suspend fun safeStorageCall(call: suspend () -> io.ktor.client.statement.HttpResponse): EmptyResult<DataError.Network> =
    try {
        val status = call().status.value
        if (status in HTTP_OK_RANGE) Result.Success(Unit) else Result.Failure(DataError.Network.Server(status))
    } catch (e: CancellationException) {
        throw e
    } catch (_: HttpRequestTimeoutException) {
        Result.Failure(DataError.Network.Timeout)
    } catch (_: ConnectTimeoutException) {
        Result.Failure(DataError.Network.Timeout)
    } catch (_: SocketTimeoutException) {
        Result.Failure(DataError.Network.Timeout)
    } catch (_: IOException) {
        Result.Failure(DataError.Network.NoInternet)
    } catch (_: Exception) {
        Result.Failure(DataError.Network.Unknown)
    }

private val HTTP_OK_RANGE = 200..299

private suspend fun <R : HttpResponse<*>, T> execute(
    call: suspend () -> R,
    onSuccess: suspend (R) -> Result<T, DataError.Network>,
): Result<T, DataError.Network> =
    try {
        val response = call()
        if (response.success) onSuccess(response) else Result.Failure(response.toFailure())
    } catch (e: CancellationException) {
        throw e
    } catch (e: RefreshUnavailableException) {
        // The request hit a 401 and the refresh to answer it failed; report why.
        Result.Failure(e.error)
    } catch (_: HttpRequestTimeoutException) {
        Result.Failure(DataError.Network.Timeout)
    } catch (_: ConnectTimeoutException) {
        Result.Failure(DataError.Network.Timeout)
    } catch (_: SocketTimeoutException) {
        Result.Failure(DataError.Network.Timeout)
    } catch (_: IOException) {
        // Offline, DNS failure, connection refused (no local backend running) or reset.
        Result.Failure(DataError.Network.NoInternet)
    } catch (_: SerializationException) {
        Result.Failure(DataError.Network.Serialization)
    } catch (_: ContentConvertException) {
        Result.Failure(DataError.Network.Serialization)
    } catch (_: Exception) {
        Result.Failure(DataError.Network.Unknown)
    }

private suspend fun HttpResponse<*>.toFailure(): DataError.Network {
    val problem = ProblemDetailParser.parse(
        status = status,
        body = response.bodyAsText(),
        contentType = response.headers[HttpHeaders.ContentType],
    )
    return if (problem.code == ApiErrorCode.Unknown && status >= 500) {
        DataError.Network.Server(status)
    } else {
        DataError.Network.Api(problem)
    }
}
