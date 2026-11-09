package com.moyi.app.data.repository

import com.moyi.app.api.apis.LegalControllerApi
import com.moyi.app.api.apis.MeControllerApi
import com.moyi.app.api.models.LegalDocumentResponse
import com.moyi.app.api.models.LegalResponse
import com.moyi.app.api.models.ProfileResponse
import com.moyi.app.core.DataError
import com.moyi.app.core.EmptyResult
import com.moyi.app.core.Result
import com.moyi.app.core.map
import com.moyi.app.data.network.ApiConfig
import com.moyi.app.data.network.AuthedHttpClient
import com.moyi.app.data.network.PublicHttpClient
import com.moyi.app.data.network.safeApiCall
import com.moyi.app.data.network.safeApiCallEmpty
import com.moyi.app.domain.legal.AcceptedVersions
import com.moyi.app.domain.legal.LegalDocuments
import com.moyi.app.domain.legal.LegalRepository
import com.moyi.app.domain.legal.LegalStatus
import com.moyi.app.domain.legal.PublishedDocument

/**
 * [LegalRepository] over the generated APIs.
 *
 * `GET /meta/legal` goes over the PUBLIC client: the route needs no token, and one that
 * is sent is checked and may be refused (`401`), so sending none is the only safe way.
 * `GET /me` and `POST /me/legal-consent` need the person, so they go over the authed one.
 *
 * No `If-None-Match`: the body is a few hundred bytes, read at sign-up, from settings,
 * and once per launch for the re-consent check, so a `304` path would be more code to
 * test than bytes saved.
 */
class LegalRepositoryImpl(
    publicClient: PublicHttpClient,
    authedClient: AuthedHttpClient,
    config: ApiConfig,
) : LegalRepository {
    private val legalApi = LegalControllerApi(config.origin, publicClient.client)
    private val meApi = MeControllerApi(config.origin, authedClient.client)

    override suspend fun documents(): Result<LegalDocuments, DataError.Network> =
        safeApiCall { legalApi.legalDocuments() }.map { it.toDomain() }

    override suspend fun status(): Result<LegalStatus, DataError.Network> {
        val documents = when (val result = documents()) {
            is Result.Failure -> return result
            is Result.Success -> result.data
        }
        return safeApiCall { meApi.me() }.map { LegalStatus(documents, it.acceptedVersions()) }
    }

    override suspend fun acceptCurrent(): EmptyResult<DataError.Network> =
        safeApiCallEmpty { meApi.acceptLegalDocuments() }
}

internal fun LegalResponse.toDomain(): LegalDocuments = LegalDocuments(
    terms = terms.toDomain(),
    privacy = privacy.toDomain(),
    dataProcessingSummary = dataProcessingSummary.toDomain(),
)

private fun LegalDocumentResponse.toDomain(): PublishedDocument = PublishedDocument(version = version, url = url)

internal fun ProfileResponse.acceptedVersions(): AcceptedVersions =
    AcceptedVersions(terms = legalAccepted.terms?.version, privacy = legalAccepted.privacy?.version)
