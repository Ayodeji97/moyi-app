package com.moyi.app.data.repository

import com.moyi.app.api.apis.DaysControllerApi
import com.moyi.app.api.apis.FavouritesControllerApi
import com.moyi.app.api.apis.SearchControllerApi
import com.moyi.app.core.ApiErrorCode
import com.moyi.app.core.ApiProblem
import com.moyi.app.core.DataError
import com.moyi.app.core.EmptyResult
import com.moyi.app.core.Result
import com.moyi.app.data.network.ApiConfig
import com.moyi.app.data.network.AuthedHttpClient
import com.moyi.app.data.network.safeApiCall
import com.moyi.app.data.network.safeApiCallEmpty
import com.moyi.app.domain.archive.ArchiveDay
import com.moyi.app.domain.archive.ArchivePage
import com.moyi.app.domain.archive.ArchiveRepository
import kotlinx.datetime.LocalDate

/**
 * [ArchiveRepository] over the generated `DaysControllerApi`, on the AUTHED client.
 *
 * Online only, like [TodayRepositoryImpl]: no cache, and no `If-None-Match` yet (the
 * ETag is worth keeping only once there is somewhere to keep the body, doc 13 §5).
 * Nothing here logs; entry text never reaches a log (CLAUDE.md, Code).
 */
class ArchiveRepositoryImpl(
    authedClient: AuthedHttpClient,
    config: ApiConfig,
) : ArchiveRepository {
    private val daysApi = DaysControllerApi(config.origin, authedClient.client)
    private val favouritesApi = FavouritesControllerApi(config.origin, authedClient.client)
    private val searchApi = SearchControllerApi(config.origin, authedClient.client)

    override suspend fun days(
        bondId: String,
        cursor: String?,
        favourites: Boolean,
        until: String?,
    ): Result<ArchivePage, DataError.Network> {
        // The domain holds dates as ISO text; the generated call takes a LocalDate. Only the
        // month jump builds one (from a LocalDate), so a value that does not parse is a bug.
        val untilDate = until?.let { runCatching { LocalDate.parse(it) }.getOrNull() ?: return Result.Failure(DataError.Network.Serialization) }
        return safeApiCall {
            // `favourites` and `until` only when asked for: the unfiltered request stays exactly as it was.
            daysApi.days(bondId, limit = PAGE_SIZE, cursor = cursor, until = untilDate, favourites = favourites.takeIf { it })
        }.mapOrSerialization { it.toArchivePage() }
    }

    // The same DaysResponse as the feed, so the same mappers: a search result is read
    // exactly as a listed day is, and the partner's words exactly as Today reads them.
    override suspend fun search(bondId: String, query: String, cursor: String?): Result<ArchivePage, DataError.Network> =
        safeApiCall { searchApi.search(bondId, q = query, limit = PAGE_SIZE, cursor = cursor) }
            .mapOrSerialization { it.toArchivePage() }

    override suspend fun setFavourite(entryId: String, favourite: Boolean): EmptyResult<DataError.Network> =
        safeApiCallEmpty {
            if (favourite) favouritesApi.favouriteEntry(entryId) else favouritesApi.unfavouriteEntry(entryId)
        }

    override suspend fun day(bondId: String, date: String): Result<ArchiveDay, DataError.Network> {
        // The contract answers 404 DAY_NOT_FOUND for "a value that is not a date"; the
        // generated call takes a LocalDate, so the same answer is given here without a call.
        val parsed = runCatching { LocalDate.parse(date) }.getOrNull()
            ?: return Result.Failure(DataError.Network.Api(ApiProblem(404, ApiErrorCode.NOT_FOUND)))
        return safeApiCall { daysApi.day(bondId, parsed) }.mapOrSerialization { it.toArchiveDay() }
    }

    private companion object {
        /** The contract's default, sent explicitly so a server default change cannot change paging. */
        const val PAGE_SIZE = 20
    }
}
