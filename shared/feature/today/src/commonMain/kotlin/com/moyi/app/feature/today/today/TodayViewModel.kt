package com.moyi.app.feature.today.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moyi.app.feature.today.common.FakeCopy
import com.moyi.app.feature.today.common.FakeDailyLoop
import com.moyi.app.feature.today.common.TodayStatus
import com.moyi.app.feature.today.common.FakeLatency
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration

/**
 * Today, over the FAKE day (PR #8: UI only).
 *
 * TODO(logic): read the day from a TodayRepository: render from cache at once and
 * refresh behind with `GET /api/v1/bonds/{bondId}/today` (states.md §3; no chained
 * round trips), refresh again on every foreground (states.md §10: never rely on a
 * push having arrived), and map `bondDay.status` as `TodayStatus` documents.
 * `partnerEntry` in that response is a oneOf on `status` (SUBMITTED, REVEALED, DELETED,
 * LOCKED, REMOVED). It becomes [TodayState.partnerEntry] only on the three layouts that
 * show it (Revealed, Tombstone, SoloTheyWrote); a LOCKED one carries no text, and every
 * other layout maps to null whatever the response holds.
 * Which bond: the member's one active bond, from `GET /api/v1/bonds`.
 */
class TodayViewModel(
    private val loop: FakeDailyLoop,
    private val fakeLatency: Duration = FakeLatency,
) : ViewModel() {

    /** The delete dialog and the delete in flight: Today's own, not the day's (D11). */
    private val deleteUi = MutableStateFlow(DeleteUi())

    val state: StateFlow<TodayState> = combine(loop.day, deleteUi) { day, delete ->
            TodayState(
                status = day.status,
                date = if (day.status == TodayStatus.SoloYouWrote || day.status == TodayStatus.SoloTheyWrote) {
                    // A solo day is shown after rollover, so it is the day before (188:491).
                    FakeCopy.SOLO_DATE
                } else {
                    FakeCopy.DATE
                },
                myEntry = day.myEntry,
                partnerEntry = day.partnerEntry.takeIf { day.status in PartnerVisible },
                // Only a revealed entry can be deleted this way: a dialog left from a day
                // that has since moved on must not open over a different layout.
                confirmingDelete = delete.confirming && day.status == TodayStatus.Revealed,
                deleting = delete.deleting,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayState(status = loop.day.value.status))

    private val _events = Channel<TodayEvent>()
    val events = _events.receiveAsFlow()

    fun onAction(action: TodayAction) {
        when (action) {
            TodayAction.OnWriteClick -> send(TodayEvent.NavigateToWrite(editing = false))
            TodayAction.OnEditClick -> send(TodayEvent.NavigateToWrite(editing = true))
            TodayAction.OnDeleteBeforeRevealClick -> {
                // TODO(logic): DELETE /api/v1/entries/{entryId} (no body, no Idempotency-Key, 204), optimistic, no confirmation
                //  (states.md §4: not irreversible yet). A failure restores the entry and says so.
                //  Whether the deleted words come back as a draft is not in states.md: owner ruling owed.
                loop.setStatus(TodayStatus.NotWritten)
            }
            TodayAction.OnDeleteAfterRevealClick -> {
                if (!deleteUi.value.deleting) deleteUi.update { it.copy(confirming = true) }
            }
            TodayAction.OnDeleteConfirm -> deleteAfterReveal()
            TodayAction.OnDeleteDismiss -> deleteUi.update { it.copy(confirming = false) }
            TodayAction.OnArchiveClick -> {
                // TODO(logic): navigate to Archive (GET /api/v1/bonds/{bondId}/days), not built yet.
            }
            TodayAction.OnSettingsClick -> send(TodayEvent.NavigateToSettings)
            TodayAction.OnShareInviteClick -> {
                // TODO(logic): open the OS share sheet with the invite link. POST /api/v1/bonds/{bondId}/invites
                //  (no body, 201) issues a new invite and revokes any outstanding one (doc 06); 409 is
                //  BOND_FULL or BOND_ARCHIVED. It navigates nowhere, and the sheet closing is never proof the
                //  invite was sent (SKILL.md §9).
            }
            TodayAction.OnDebugCycle -> {
                // Waiting steps through the reveal, the way a real day opens: the partner writes,
                // the reveal plays, and it settles into Revealed.
                if (loop.day.value.status == TodayStatus.Waiting) {
                    send(TodayEvent.NavigateToReveal)
                } else {
                    loop.nextStatus()
                }
            }
        }
    }

    private fun deleteAfterReveal() {
        if (deleteUi.value.deleting) return
        deleteUi.value = DeleteUi(confirming = false, deleting = true)
        viewModelScope.launch {
            // TODO(logic): DELETE /api/v1/entries/{entryId}. The contract takes no body and no
            //  Idempotency-Key, and lists 204, 401, 403, 404, 429 and 500: no 409, because an
            //  author's delete is a 204 even on a Bond that has ended (doc 06, ADR-0035 decision
            //  15). Not optimistic: it is irreversible, so the tombstone shows only after the 204.
            //  The dialog has closed by then (an iOS alert closes on any answer), so a failure
            //  shows the generic error on Today (states.md §10) with the entry still there; a 404
            //  (already gone, or never the caller's) refreshes Today instead.
            delay(fakeLatency)
            loop.setStatus(TodayStatus.Tombstone)
            deleteUi.value = DeleteUi()
        }
    }

    private fun send(event: TodayEvent) {
        viewModelScope.launch { _events.send(event) }
    }

    private data class DeleteUi(val confirming: Boolean = false, val deleting: Boolean = false)

    private companion object {
        /** The only layouts that may hold the partner's words: the day is open, or it closed SOLO with theirs. */
        val PartnerVisible = setOf(TodayStatus.Revealed, TodayStatus.Tombstone, TodayStatus.SoloTheyWrote)
    }
}
