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

/** For endpoints that answer with an empty body (`201`, `202`, `204`): only the status counts. */
suspend fun safeApiCallEmpty(call: suspend () -> HttpResponse<*>): EmptyResult<DataError.Network> =
    execute(call) { Result.Success(Unit) }

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
