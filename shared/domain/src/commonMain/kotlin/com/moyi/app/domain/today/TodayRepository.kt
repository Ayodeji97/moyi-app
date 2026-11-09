package com.moyi.app.domain.today

import com.moyi.app.core.DataError
import com.moyi.app.core.EmptyResult
import com.moyi.app.core.Result
import kotlin.time.Instant

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
     * [promptId] is today's prompt, unless the person dismissed it. [intendedAt] is the
     * instant of the Send tap (BR-3a), sent on the first try as on every retry, because the
     * server binds the key to the whole body: a first try without it and a queued retry
     * with it would be two different requests under one key.
     *
     * [imageMediaId] is a photo already uploaded and processed (`READY`); the server
     * attaches it in the entry's own transaction, or refuses the entry with
     * `422 VALIDATION_FAILED` on `imageMediaId` (`MEDIA_NOT_ATTACHABLE`). [voiceMediaId] is the
     * same for a voice note, on `voiceMediaId`. An entry may carry one of each (FR-042, FR-043).
     */
    suspend fun submitEntry(
        bondId: String,
        text: String,
        promptId: String?,
        idempotencyKey: String,
        intendedAt: Instant? = null,
        imageMediaId: String? = null,
        voiceMediaId: String? = null,
    ): Result<MyEntry, DataError.Network>

    /** Replaces the words of my entry before the reveal. [idempotencyKey] as for [submitEntry]. */
    suspend fun editEntry(
        entryId: String,
        text: String,
        idempotencyKey: String,
    ): Result<MyEntry, DataError.Network>

    /** Deletes my entry: before the reveal it is gone, after it a tombstone stays (doc 06 §3.4). */
    suspend fun deleteEntry(entryId: String): EmptyResult<DataError.Network>

    /**
     * Leaves [reaction] on the partner's entry [entryId] (`PUT /entries/{id}/reaction`),
     * replacing any I left, or takes mine back when [reaction] is null (`DELETE`). Both are
     * repeatable `204`s. The refusals a caller can meet: `409 REACTION_ON_OWN_ENTRY`,
     * `409 ENTRY_IMMUTABLE` (erased meanwhile), `409 BOND_ARCHIVED` (the bond ended, for
     * either verb), `409 REACTION_BUSY` (another request of mine holds it: retry), and the
     * one `404` for an entry I was never shown.
     */
    suspend fun setReaction(entryId: String, reaction: Reaction?): EmptyResult<DataError.Network>
}
