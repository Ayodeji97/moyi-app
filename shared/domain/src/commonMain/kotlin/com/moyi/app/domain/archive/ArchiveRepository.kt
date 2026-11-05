package com.moyi.app.domain.archive

import com.moyi.app.core.DataError
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
     */
    suspend fun days(bondId: String, cursor: String? = null): Result<ArchivePage, DataError.Network>

    /**
     * One day, [date] in ISO form. `404 DAY_NOT_FOUND` for every date not in the
     * caller's archive, whatever the reason.
     */
    suspend fun day(bondId: String, date: String): Result<ArchiveDay, DataError.Network>
}
