package com.moyi.app.domain.archive

import com.moyi.app.core.DataError
import com.moyi.app.core.EmptyResult
import com.moyi.app.core.Result

/**
 * The archive (doc 06 §3.4, `days-controller`). Every call needs a signed-in person.
 * Deleting an entry is not here: it is the same call wherever it starts
 * (`TodayRepository.deleteEntry`).
 */
interface ArchiveRepository {
    /**
     * The page of [bondId]'s days that [cursor] points at, or the newest page when it
     * is null. [cursor] is a previous page's `nextCursor`, unchanged.
     *
     * With [favourites], only the days holding an entry the caller has favourited and
     * can still read. Such a page can be **empty with a cursor** (the server reads at
     * most about a thousand days per request): only a null cursor is the end.
     */
    suspend fun days(
        bondId: String,
        cursor: String? = null,
        favourites: Boolean = false,
    ): Result<ArchivePage, DataError.Network>

    /**
     * One day, [date] in ISO form. `404 DAY_NOT_FOUND` for every date not in the
     * caller's archive, whatever the reason.
     */
    suspend fun day(bondId: String, date: String): Result<ArchiveDay, DataError.Network>

    /**
     * Marks ([favourite] true, `PUT /entries/{id}/favourite`) or unmarks (`DELETE`) the
     * entry as the caller's own favourite. Both are repeatable. The other member is never
     * shown it. Only a revealed entry can be marked (`409 ENTRY_NOT_REVEALED`, and
     * `409 ENTRY_IMMUTABLE` for a tombstone).
     */
    suspend fun setFavourite(entryId: String, favourite: Boolean): EmptyResult<DataError.Network>
}
