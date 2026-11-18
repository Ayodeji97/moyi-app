package com.moyi.app.data.repository

import com.moyi.app.api.apis.RememberControllerApi
import com.moyi.app.core.ApiErrorCode
import com.moyi.app.core.DataError
import com.moyi.app.core.Result
import com.moyi.app.data.network.ApiConfig
import com.moyi.app.data.network.AuthedHttpClient
import com.moyi.app.data.network.safeApiCall
import com.moyi.app.domain.archive.ArchiveDay
import com.moyi.app.domain.archive.REMEMBER_LIMIT
import com.moyi.app.domain.archive.RememberError
import com.moyi.app.domain.archive.RememberRepository

/**
 * [RememberRepository] over the generated `RememberControllerApi`, on the AUTHED client.
 *
 * Online only, by contract: the answer is never to be stored, so there is no cache to read
 * or write. Nothing here logs; the question travels only as `?q=`, which the HTTP log cuts
 * (`redactQueries`).
 */
class RememberRepositoryImpl(
    authedClient: AuthedHttpClient,
    config: ApiConfig,
) : RememberRepository {
    private val api = RememberControllerApi(config.origin, authedClient.client)

    override suspend fun remember(bondId: String, question: String): Result<List<ArchiveDay>, RememberError> {
        // The same DayResponse as the feed, so the same mapper: a result day is read exactly as
        // a listed day is, and the partner's words exactly as Today reads them.
        val answer = safeApiCall { api.remember(bondId, q = question, limit = REMEMBER_LIMIT) }
            .mapOrSerialization { response -> response.items.map { it.toArchiveDay() ?: return@mapOrSerialization null } }
        return when (answer) {
            is Result.Success -> answer
            is Result.Failure -> Result.Failure(answer.error.toRememberError())
        }
    }

    private companion object {
        /**
         * The wait when a `429` comes without a readable `Retry-After`. The backend always sends
         * one (whole seconds, at least 1), so this is a guard, not a policy: a minute is long
         * enough not to spend another question at once, short enough not to strand the screen.
         */
        const val FALLBACK_RETRY_AFTER_SECONDS = 60

        fun DataError.Network.toRememberError(): RememberError {
            val problem = (this as? DataError.Network.Api)?.problem
            return if (problem != null && (problem.status == 429 || problem.code == ApiErrorCode.RATE_LIMITED)) {
                RememberError.RateLimited(problem.retryAfterSeconds ?: FALLBACK_RETRY_AFTER_SECONDS)
            } else {
                RememberError.Failed(this)
            }
        }
    }
}
