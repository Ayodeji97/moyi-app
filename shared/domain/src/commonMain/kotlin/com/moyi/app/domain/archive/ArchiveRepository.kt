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
     *
     * With [until] (an ISO date, `YYYY-MM-DD`), the list starts at that date or the
     * nearest earlier day: the month jump (states.md §6, route 1). With [cursor] as well,
     * both apply, so every later page of a jump is asked with the same [until].
     */
    suspend fun days(
        bondId: String,
        cursor: String? = null,
        favourites: Boolean = false,
        until: String? = null,
    ): Result<ArchivePage, DataError.Network>

    /**
     * The days on which an entry the caller can read in full holds every word of [query]
     * (`GET /bonds/{bondId}/search`, FR-091), newest first, in the same day shape as [days].
     * Whole words, without case or accents. **The partner's words are matched only once
     * revealed**: the server decides, and the client never searches anything itself.
     *
     * [query] is the trimmed text, 1 to [SEARCH_MAX_LENGTH] characters (`422
     * VALIDATION_FAILED` otherwise). [cursor] is an earlier page's `nextCursor` of this same
     * search, unchanged; **only a null cursor is the end**. The contract says the response
     * is never to be stored, so it is held in memory only, as long as the Archive is open.
     */
    suspend fun search(
        bondId: String,
        query: String,
        cursor: String? = null,
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

/**
 * The contract's cap on `q`: 100 graphemes of the trimmed, NFKC-normalised text. Capping
 * the field at 100 UTF-16 characters keeps every query under both that and the 1024-octet cap
 * (a grapheme is at least one character, and 100 characters are at most 300 UTF-8 octets).
 */
const val SEARCH_MAX_LENGTH = 100
