package com.moyi.app.data.repository

import com.moyi.app.api.apis.SupportControllerApi
import com.moyi.app.api.models.SupportMessageRequest
import com.moyi.app.core.DataError
import com.moyi.app.core.Result
import com.moyi.app.data.network.ApiConfig
import com.moyi.app.data.network.AuthedHttpClient
import com.moyi.app.data.network.safeApiCall
import com.moyi.app.domain.support.SupportRepository
import com.moyi.app.domain.support.SupportTicket

/**
 * [SupportRepository] over the generated support API, on the AUTHED client: the route is
 * behind the bearer, so a signed-out person cannot reach it (ADR-0057 decision 1).
 *
 * The `Idempotency-Key` is declared optional by the contract and sent every time, so a
 * retry after a lost answer is answered with the same ticket (`Idempotency-Replayed`).
 * Nothing here logs the subject or the body.
 */
class SupportRepositoryImpl(
    authedClient: AuthedHttpClient,
    config: ApiConfig,
) : SupportRepository {
    private val api = SupportControllerApi(config.origin, authedClient.client)

    override suspend fun send(subject: String, body: String, idempotencyKey: String): Result<SupportTicket, DataError.Network> =
        safeApiCall { api.sendSupportMessage(SupportMessageRequest(body = body, subject = subject), idempotencyKey) }
            .mapOrSerialization { SupportTicket(id = it.id, createdAt = it.createdAt) }
}
