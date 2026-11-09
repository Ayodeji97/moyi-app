package com.moyi.app.data.repository

import com.moyi.app.api.apis.DataExportControllerApi
import com.moyi.app.api.models.DataExportResponse
import com.moyi.app.core.DataError
import com.moyi.app.core.Result
import com.moyi.app.core.map
import com.moyi.app.data.network.ApiConfig
import com.moyi.app.data.network.AuthedHttpClient
import com.moyi.app.data.network.safeApiCall
import com.moyi.app.domain.account.DataExport
import com.moyi.app.domain.account.DataExportRepository
import com.moyi.app.domain.account.DataExportStatus

/**
 * [DataExportRepository] over `/me/export`, on the authed client. The download URL a READY
 * export carries is a credential: it is passed to the domain as it is and never logged (the
 * Http logger logs no bodies, and [DataExport.toString] leaves it out).
 */
class DataExportRepositoryImpl(
    authedClient: AuthedHttpClient,
    config: ApiConfig,
) : DataExportRepository {
    private val api = DataExportControllerApi(config.origin, authedClient.client)

    override suspend fun latest(): Result<DataExport?, DataError.Network> =
        when (val result = safeApiCall { api.getLatestDataExport() }) {
            is Result.Success -> Result.Success(result.data.toDomain())
            // 404: never asked for one. Not a failure, the screen's starting state.
            is Result.Failure -> if ((result.error as? DataError.Network.Api)?.problem?.status == NOT_FOUND) Result.Success(null) else result
        }

    override suspend fun request(): Result<DataExport, DataError.Network> =
        safeApiCall { api.requestDataExport() }.map { it.toDomain() }

    override suspend fun export(id: String): Result<DataExport, DataError.Network> =
        safeApiCall { api.getDataExport(id) }.map { it.toDomain() }
}

private const val NOT_FOUND = 404

internal fun DataExportResponse.toDomain(): DataExport = DataExport(
    id = id,
    status = when (status) {
        DataExportResponse.Status.PREPARING -> DataExportStatus.PREPARING
        DataExportResponse.Status.READY -> DataExportStatus.READY
        DataExportResponse.Status.EXPIRED -> DataExportStatus.EXPIRED
        DataExportResponse.Status.FAILED -> DataExportStatus.FAILED
    },
    requestedAt = requestedAt,
    readyAt = readyAt,
    expiresAt = expiresAt,
    sizeBytes = sizeBytes,
    downloadUrl = downloadUrl,
)
