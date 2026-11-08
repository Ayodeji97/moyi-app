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
import kotlin.time.Instant

/**
 * [TodayRepository] over the generated entry APIs, on the AUTHED client (a 401
 * refreshes once and retries; see BondRepositoryImpl).
 *
 * The network half only: the cache, the drafts and the outbox of doc 13 §5 are their own
 * stores (SqlTodayCache, SqlDraftStore, SqlEntryOutbox), and the outbox's sender retries
 * through [submitEntry] with the same key and body.
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
        intendedAt: Instant?,
    ): Result<MyEntry, DataError.Network> {
        val request = SubmitEntryRequest(text = text, promptId = promptId, intendedAt = intendedAt)
        return safeApiCall { entriesApi.submitEntry(bondId, idempotencyKey, request) }.mapOrSerialization { it.toMyEntry() }
    }

    override suspend fun editEntry(entryId: String, text: String, idempotencyKey: String): Result<MyEntry, DataError.Network> =
        safeApiCall { entryChangesApi.patchEntry(entryId, PatchEntryRequest(text = text), idempotencyKey) }
            .mapOrSerialization { it.toMyEntry() }

    override suspend fun deleteEntry(entryId: String): EmptyResult<DataError.Network> =
        safeApiCallEmpty { entryChangesApi.deleteEntry(entryId) }
}
