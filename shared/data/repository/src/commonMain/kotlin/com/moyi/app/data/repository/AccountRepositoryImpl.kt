package com.moyi.app.data.repository

import com.moyi.app.api.apis.MeControllerApi
import com.moyi.app.core.DataError
import com.moyi.app.core.Result
import com.moyi.app.data.network.ApiConfig
import com.moyi.app.data.network.AuthedHttpClient
import com.moyi.app.data.network.safeApiCall
import com.moyi.app.data.session.toUser
import com.moyi.app.domain.auth.AccountRepository
import com.moyi.app.domain.auth.User

/** [AccountRepository] over `GET /me`, on the authed client. */
class AccountRepositoryImpl(
    authedClient: AuthedHttpClient,
    config: ApiConfig,
) : AccountRepository {
    private val meApi = MeControllerApi(config.origin, authedClient.client)

    override suspend fun currentUser(): Result<User, DataError.Network> =
        safeApiCall { meApi.me() }.mapOrSerialization { it.toUser() }
}
