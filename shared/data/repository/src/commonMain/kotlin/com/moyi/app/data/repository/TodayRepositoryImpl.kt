package com.moyi.app.data.repository

import com.moyi.app.api.apis.EntriesControllerApi
import com.moyi.app.api.apis.EntryChangesControllerApi
import com.moyi.app.api.models.PatchEntryRequest
import com.moyi.app.api.models.SubmitEntryRequest
import com.moyi.app.core.DataError
import com.moyi.app.core.EmptyResult
import com.moyi.app.core.Result
import com.moyi.app.data.network.ApiConfig
import com.moyi.app.data.network.AuthedHttpClient
import com.moyi.app.data.network.safeApiCall
import com.moyi.app.data.network.safeApiCallEmpty
import com.moyi.app.domain.today.MyEntry
import com.moyi.app.domain.today.Today
import com.moyi.app.domain.today.TodayRepository

/**
 * [TodayRepository] over the generated entry APIs, on the AUTHED client (a 401
 * refreshes once and retries; see BondRepositoryImpl).
 *
 * Online only for now. The local cache, drafts and outbox of doc 13 §5 are owed:
 * SQLDelight is not in the project yet (docs/figma/today-check.md, "Owed").
 *
 * Nothing here logs: the HTTP logger prints method, URL and status only, so
 * entry text never reaches a log (CLAUDE.md, Code).
 */
class TodayRepositoryImpl(
    authedClient: AuthedHttpClient,
    config: ApiConfig,
) : TodayRepository {
    private val entriesApi = EntriesControllerApi(config.origin, authedClient.client)
    private val entryChangesApi = EntryChangesControllerApi(config.origin, authedClient.client)

    override suspend fun today(bondId: String): Result<Today, DataError.Network> =
        safeApiCall { entriesApi.today(bondId) }.mapOrSerialization { it.toToday() }

    override suspend fun submitEntry(
        bondId: String,
        text: String,
        promptId: String?,
        idempotencyKey: String,
    ): Result<MyEntry, DataError.Network> {
        // No intendedAt: that belongs to an entry queued offline (BR-3a), and there is no outbox yet.
        val request = SubmitEntryRequest(text = text, promptId = promptId)
        return safeApiCall { entriesApi.submitEntry(bondId, idempotencyKey, request) }.mapOrSerialization { it.toMyEntry() }
    }

    override suspend fun editEntry(entryId: String, text: String, idempotencyKey: String): Result<MyEntry, DataError.Network> =
        safeApiCall { entryChangesApi.patchEntry(entryId, PatchEntryRequest(text = text), idempotencyKey) }
            .mapOrSerialization { it.toMyEntry() }

    override suspend fun deleteEntry(entryId: String): EmptyResult<DataError.Network> =
        safeApiCallEmpty { entryChangesApi.deleteEntry(entryId) }
}
