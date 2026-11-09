package com.moyi.app.data.repository

import com.moyi.app.api.apis.DeletionRequestControllerApi
import com.moyi.app.api.apis.MeControllerApi
import com.moyi.app.api.models.DeletionRequestBody
import com.moyi.app.api.models.DeletionRequestResponse
import com.moyi.app.core.DataError
import com.moyi.app.core.EmptyResult
import com.moyi.app.core.Result
import com.moyi.app.core.map
import com.moyi.app.data.network.ApiConfig
import com.moyi.app.data.network.AuthedHttpClient
import com.moyi.app.data.network.safeApiCall
import com.moyi.app.data.network.safeApiCallEmpty
import com.moyi.app.domain.account.AccountDeletionRepository
import com.moyi.app.domain.account.DeletionRequest

/**
 * [AccountDeletionRepository] over `GET /me` and `/me/deletion-request`, on the authed client.
 *
 * The password goes in the request body and nowhere else: the Http logger never logs bodies.
 * A wrong one answers `401 INVALID_CREDENTIALS` without a `WWW-Authenticate` challenge, which
 * the authed client does not take for a refused token (`isPasswordRefusal`, network), so it
 * reaches the caller once, with no refresh and no sign-out (docs/backend-contract-gaps.md, row 33).
 */
class AccountDeletionRepositoryImpl(
    authedClient: AuthedHttpClient,
    config: ApiConfig,
) : AccountDeletionRepository {
    private val meApi = MeControllerApi(config.origin, authedClient.client)
    private val deletionApi = DeletionRequestControllerApi(config.origin, authedClient.client)

    override suspend fun pending(): Result<DeletionRequest?, DataError.Network> =
        safeApiCall { meApi.me() }.map { it.deletionRequest?.toDomain() }

    override suspend fun request(password: String): Result<DeletionRequest, DataError.Network> =
        safeApiCall { deletionApi.requestAccountDeletion(DeletionRequestBody(password = password)) }.map { it.toDomain() }

    override suspend fun cancel(): EmptyResult<DataError.Network> = safeApiCallEmpty { deletionApi.cancelAccountDeletion() }
}

internal fun DeletionRequestResponse.toDomain(): DeletionRequest =
    DeletionRequest(requestedAt = requestedAt, cancellableUntil = cancellableUntil, deleteBy = deleteBy)
