package com.moyi.app.feature.today.common

import com.moyi.app.domain.today.Prompt
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.LocalDate

/**
 * The day the three screens share: Today reads it, Write and delete change it, and
 * the reveal plays it. One instance per app (a Koin `single`), so a send from Write
 * moves Today to waiting without a second fetch.
 *
 * Two implementations: [RepositoryDailyLoop], the real one over the domain's
 * repositories, and `PreviewDailyLoop` in :shared, bound only in the
 * `-PmoyiUiPreview=today` debug build (KoinInit). Nothing else ever sees the preview.
 *
 * Every call reports its outcome as a value; none throws.
 */
interface DailyLoop {
    val state: StateFlow<DailyLoopState>

    /** Loads the day, or reloads it behind what is already shown (states.md §3). */
    suspend fun refresh()

    /** Writes today's entry. [idempotencyKey] is the caller's, minted at the tap and reused on a retry. */
    suspend fun submit(text: String, promptId: String?, idempotencyKey: String): WriteResult

    /** Replaces my entry's words before the reveal. */
    suspend fun edit(text: String, idempotencyKey: String): WriteResult

    /** Optimistic (states.md §4: not irreversible yet): Today shows not-written at once; a failure restores it. */
    suspend fun deleteBeforeReveal(): DeleteResult

    /** Not optimistic: irreversible, so the tombstone shows only after the server's 204. */
    suspend fun deleteAfterReveal(): DeleteResult

    /** Forgets the day: nothing of one person's day may outlive the screen that showed it (a sign-out). */
    fun clear()

    /** DEBUG ONLY: the preview steps to its next drawn layout. The real loop does nothing. */
    fun debugNextLayout() = Unit
}

sealed interface DailyLoopState {
    /** Nothing to show yet: the screen is the bare `surface`, as at launch (states.md §0). */
    data object Loading : DailyLoopState

    /** The first load failed: the Generic error (states.md §10). A failed refresh keeps the day instead. */
    data object Failed : DailyLoopState

    /** The person is in no open bond any more (left, or ended, on the other phone): the shell re-routes. */
    data object NoOpenBond : DailyLoopState

    data class Ready(val day: DayView) : DailyLoopState
}

/**
 * One day, already mapped for the screens ([toDayView]). [partnerEntry] holds the
 * partner's words ONLY in [TodayStatus.Revealed], [TodayStatus.Tombstone] and
 * [TodayStatus.SoloTheyWrote]; in every other layout it is null whatever the
 * server sent, so no screen can show what it was never given.
 */
data class DayView(
    val bondId: String,
    val date: LocalDate,
    val status: TodayStatus,
    /** The other member; null while nobody has joined. Every layout that names them has one. */
    val partnerName: String?,
    val prompt: Prompt?,
    /** Mine, live or deleted: the id the edit and delete calls take. */
    val myEntryId: String?,
    /** My words; null when I have not written, or deleted them. */
    val myEntry: String?,
    val partnerEntry: String?,
) {
    /** Never the words: a data class would print both entries into any log that printed this. */
    override fun toString(): String =
        "DayView(bondId=$bondId, date=$date, status=$status, myEntryId=$myEntryId, " +
            "myEntry=${myEntry?.let { "(redacted)" }}, partnerEntry=${partnerEntry?.let { "(redacted)" }})"
}

enum class WriteResult {
    /** The server has the words. */
    Saved,

    /** It did not: the composer keeps them (states.md §10, Save failed). */
    Failed,
}

enum class DeleteResult {
    Deleted,

    /** `404`: already gone, or never the caller's. Today has been refreshed; nothing to say. */
    Gone,

    /** It did not go through, and Today shows the entry still there. */
    Failed,
}
