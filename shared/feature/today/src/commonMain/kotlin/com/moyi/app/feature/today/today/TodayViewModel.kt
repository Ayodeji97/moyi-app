package com.moyi.app.feature.today.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moyi.app.feature.today.common.DailyLoop
import com.moyi.app.feature.today.common.DailyLoopState
import com.moyi.app.feature.today.common.DeleteResult
import com.moyi.app.feature.today.common.PartnerVisible
import com.moyi.app.feature.today.common.RevealLedger
import com.moyi.app.feature.today.common.TodayStatus
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Today, over the shared [DailyLoop].
 *
 * - **Loading**: the first load shows the bare surface (no spinner); every later one
 *   refreshes behind what is shown. The screen asks on every foreground
 *   ([TodayAction.OnForeground]), and the loop refreshes after each write and delete.
 * - **The reveal**: when a day turns up revealed with the partner's words, and the
 *   [RevealLedger] has not played that bond-day in this process, Today opens the reveal.
 *
 * Today is the root of the signed-in app, so it owns the loop's lifetime: the day is
 * forgotten when this ViewModel is created and when it is cleared (a sign-out), so no
 * one's words outlive the session that loaded them.
 *
 * TODO(logic): render from a local cache at once (states.md §3, doc 13 §5), owed with SQLDelight.
 */
class TodayViewModel(
    private val loop: DailyLoop,
    private val ledger: RevealLedger,
) : ViewModel() {

    /** The delete dialog, the delete in flight and a failed delete: Today's own, not the day's (D11). */
    private val ui = MutableStateFlow(TodayUi())

    init {
        loop.clear()
    }

    val state: StateFlow<TodayState> = combine(loop.state, ui, ::toState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), toState(loop.state.value, ui.value))

    private val _events = Channel<TodayEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch { loop.state.collect(::onDay) }
    }

    fun onAction(action: TodayAction) {
        when (action) {
            TodayAction.OnForeground, TodayAction.OnRetryClick -> {
                ui.update { it.copy(failed = false) }
                viewModelScope.launch { loop.refresh() }
            }
            TodayAction.OnWriteClick -> send(TodayEvent.NavigateToWrite(editing = false))
            TodayAction.OnEditClick -> {
                ui.update { it.copy(failed = false) }
                send(TodayEvent.NavigateToWrite(editing = true))
            }
            TodayAction.OnDeleteBeforeRevealClick -> deleteBeforeReveal()
            TodayAction.OnDeleteAfterRevealClick -> {
                if (!ui.value.deleting) ui.update { it.copy(confirming = true, failed = false) }
            }
            TodayAction.OnDeleteConfirm -> deleteAfterReveal()
            TodayAction.OnDeleteDismiss -> ui.update { it.copy(confirming = false) }
            TodayAction.OnArchiveClick -> send(TodayEvent.NavigateToArchive)
            TodayAction.OnSettingsClick -> send(TodayEvent.NavigateToSettings)
            TodayAction.OnShareInviteClick -> {
                // TODO(logic): open the OS share sheet with the invite link. POST /api/v1/bonds/{bondId}/invites
                //  (no body, 201) issues a new invite and revokes any outstanding one (doc 06); 409 is
                //  BOND_FULL or BOND_ARCHIVED. It navigates nowhere, and the sheet closing is never proof the
                //  invite was sent (SKILL.md §9).
            }
            TodayAction.OnDebugCycle -> debugCycle()
        }
    }

    private fun onDay(day: DailyLoopState) {
        when (day) {
            is DailyLoopState.Ready -> {
                val view = day.day
                // Only a day that opened with both entries plays the moment; never PENDING_REVEAL
                // (states.md §5a), never a tombstone, and never a second time for the same day.
                if (view.status == TodayStatus.Revealed && view.partnerEntry != null && ledger.firstShowing(view.bondId, view.date)) {
                    send(TodayEvent.NavigateToReveal)
                }
            }
            DailyLoopState.NoOpenBond -> send(TodayEvent.NavigateToStart)
            DailyLoopState.Loading, DailyLoopState.Failed -> Unit
        }
    }

    /** Optimistic: the loop shows not-written at once and restores the entry if the server refuses. */
    private fun deleteBeforeReveal() {
        if (ui.value.deleting) return
        ui.update { it.copy(failed = false) }
        viewModelScope.launch {
            // TODO(logic): whether the deleted words come back as a draft is not in states.md: owner ruling owed.
            if (loop.deleteBeforeReveal() == DeleteResult.Failed) ui.update { it.copy(failed = true) }
        }
    }

    /**
     * Not optimistic: it is irreversible, so the tombstone shows only after the 204. The
     * dialog has closed by then (an iOS alert closes on any answer), so a failure shows the
     * Generic error line on Today with the entry still there; a 404 (already gone, or never
     * the caller's) refreshes Today instead and says nothing.
     */
    private fun deleteAfterReveal() {
        if (ui.value.deleting) return
        ui.value = TodayUi(deleting = true)
        viewModelScope.launch {
            val result = loop.deleteAfterReveal()
            ui.value = TodayUi(failed = result == DeleteResult.Failed)
        }
    }

    /**
     * DEBUG ONLY. Waiting steps through the reveal, the way a real day opens: the preview
     * day turns to revealed, the reveal plays over it, and it settles into Revealed. The
     * ledger is told first, so the trigger above does not open it a second time.
     */
    private fun debugCycle() {
        // A delete question left open must not come back when the cycle returns to Revealed.
        ui.value = TodayUi()
        val ready = loop.state.value as? DailyLoopState.Ready
        loop.debugNextLayout()
        if (ready?.day?.status == TodayStatus.Waiting) {
            val now = loop.state.value as? DailyLoopState.Ready
            if (now != null) ledger.firstShowing(now.day.bondId, now.day.date)
            send(TodayEvent.NavigateToReveal)
        }
    }

    private fun toState(day: DailyLoopState, ui: TodayUi): TodayState = when (day) {
        DailyLoopState.Loading, DailyLoopState.NoOpenBond -> TodayState(load = TodayLoad.Loading)
        DailyLoopState.Failed -> TodayState(load = TodayLoad.Failed)
        is DailyLoopState.Ready -> {
            val view = day.day
            TodayState(
                load = TodayLoad.Ready,
                status = view.status,
                date = view.date,
                partnerName = view.partnerName,
                prompt = view.prompt?.text,
                myEntry = view.myEntry.orEmpty(),
                // The loop already holds them only in these layouts; this is the second lock on the door.
                partnerEntry = view.partnerEntry.takeIf { view.status in PartnerVisible },
                queuedForDate = view.date,
                // Only a revealed entry can be deleted this way: a dialog left from a day
                // that has since moved on must not open over a different layout.
                confirmingDelete = ui.confirming && view.status == TodayStatus.Revealed,
                deleting = ui.deleting,
                actionFailed = ui.failed,
            )
        }
    }

    private fun send(event: TodayEvent) {
        viewModelScope.launch { _events.send(event) }
    }

    override fun onCleared() {
        loop.clear()
    }

    private data class TodayUi(val confirming: Boolean = false, val deleting: Boolean = false, val failed: Boolean = false)
}
