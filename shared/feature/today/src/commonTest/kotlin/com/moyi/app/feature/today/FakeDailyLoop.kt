package com.moyi.app.feature.today

import com.moyi.app.domain.today.Prompt
import com.moyi.app.feature.today.common.DailyLoop
import com.moyi.app.feature.today.common.DailyLoopState
import com.moyi.app.feature.today.common.DayView
import com.moyi.app.feature.today.common.DeleteResult
import com.moyi.app.feature.today.common.TodayStatus
import com.moyi.app.feature.today.common.WriteResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

const val PARTNER = "Tunde"
const val MINE = "You made the tea."
const val THEIRS = "You stayed up to help me finish it."
val DAY: LocalDate = LocalDate(2026, 9, 14)
val PROMPT = Prompt(id = "prompt-1", text = "What did Tunde do today that you noticed?")

/** A day in [status], holding the partner's words only where a layout may (the loop's own rule). */
fun dayView(
    status: TodayStatus,
    myEntry: String? = MINE,
    partnerEntry: String? = THEIRS,
    date: LocalDate = DAY,
    prompt: Prompt? = PROMPT,
) = DayView(
    bondId = "bond-1",
    date = date,
    status = status,
    partnerName = PARTNER,
    prompt = prompt,
    myEntryId = "entry-1".takeIf { myEntry != null },
    myEntry = myEntry.takeIf { status != TodayStatus.NotWritten },
    partnerEntry = partnerEntry.takeIf {
        status == TodayStatus.Revealed || status == TodayStatus.Tombstone || status == TodayStatus.SoloTheyWrote
    },
)

/**
 * A [DailyLoop] for the ViewModel tests: it holds whatever [show] last put there,
 * answers each call with its `…Result`, and records what it was sent. [refresh]
 * publishes [onRefresh] when set. Set [gate] to hold a write or delete in flight.
 */
class FakeDailyLoop(initial: DailyLoopState = DailyLoopState.Loading) : DailyLoop {
    override val state: MutableStateFlow<DailyLoopState> = MutableStateFlow(initial)

    var onRefresh: DailyLoopState? = null
    var submitResult = WriteResult.Saved
    var editResult = WriteResult.Saved
    var deleteBeforeResult = DeleteResult.Deleted
    var deleteAfterResult = DeleteResult.Deleted
    var gate: CompletableDeferred<Unit>? = null

    data class Submit(val text: String, val promptId: String?, val key: String, val intendedAt: Instant)
    data class Edit(val text: String, val key: String)

    var refreshes = 0
    var clears = 0
    var deletesBefore = 0
    var deletesAfter = 0
    val submits = mutableListOf<Submit>()
    val edits = mutableListOf<Edit>()

    fun show(view: DayView) {
        state.value = DailyLoopState.Ready(view)
    }

    override suspend fun refresh() {
        refreshes++
        onRefresh?.let { state.value = it }
    }

    var refusalDismissals = 0

    override suspend fun dismissRefusal() {
        refusalDismissals++
    }

    override suspend fun submit(text: String, promptId: String?, idempotencyKey: String, intendedAt: Instant): WriteResult {
        submits += Submit(text, promptId, idempotencyKey, intendedAt)
        gate?.await()
        return submitResult
    }

    override suspend fun edit(text: String, idempotencyKey: String): WriteResult {
        edits += Edit(text, idempotencyKey)
        gate?.await()
        return editResult
    }

    override suspend fun deleteBeforeReveal(): DeleteResult {
        deletesBefore++
        gate?.await()
        return deleteBeforeResult
    }

    override suspend fun deleteAfterReveal(): DeleteResult {
        deletesAfter++
        gate?.await()
        return deleteAfterResult
    }

    override fun clear() {
        clears++
        state.value = DailyLoopState.Loading
    }
}

/** The narrow view the shared state gives a test. */
val StateFlow<DailyLoopState>.day: DayView get() = (value as DailyLoopState.Ready).day
