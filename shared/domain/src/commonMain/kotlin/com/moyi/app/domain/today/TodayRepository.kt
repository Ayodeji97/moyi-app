package com.moyi.app.domain.today

import com.moyi.app.core.DataError
import com.moyi.app.core.EmptyResult
import com.moyi.app.core.Result

/**
 * The daily loop (doc 06 §3.4). Every call needs a signed-in person.
 *
 * Refusals carry the server's code in [DataError.Network.Api]; the caller
 * switches on it. The ones the daily loop acts on:
 * - `409 ENTRY_ALREADY_EXISTS` from [submitEntry]: one entry a day.
 * - `409 ENTRY_IMMUTABLE` or `BOND_ARCHIVED` from [editEntry]: the day opened, or
 *   the bond ended, while the composer was open.
 * - `404` from [deleteEntry]: already gone, or never the caller's.
 */
interface TodayRepository {
    /** Today in [bondId], for the person asking. */
    suspend fun today(bondId: String): Result<Today, DataError.Network>

    /**
     * Writes today's entry. [idempotencyKey] is minted by the caller at the tap and
     * passed again, unchanged, on every retry of the same send (doc 06 §1), so a
     * retry after a lost response is the same write, never a second one.
     * [promptId] is today's prompt, unless the person dismissed it.
     */
    suspend fun submitEntry(
        bondId: String,
        text: String,
        promptId: String?,
        idempotencyKey: String,
    ): Result<MyEntry, DataError.Network>

    /** Replaces the words of my entry before the reveal. [idempotencyKey] as for [submitEntry]. */
    suspend fun editEntry(
        entryId: String,
        text: String,
        idempotencyKey: String,
    ): Result<MyEntry, DataError.Network>

    /** Deletes my entry: before the reveal it is gone, after it a tombstone stays (doc 06 §3.4). */
    suspend fun deleteEntry(entryId: String): EmptyResult<DataError.Network>
}
