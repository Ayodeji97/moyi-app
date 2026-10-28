package com.moyi.app.feature.today.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moyi.app.feature.today.common.FakeCopy
import com.moyi.app.feature.today.common.FakeDailyLoop
import com.moyi.app.feature.today.common.TodayStatus
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Today, over the FAKE day (PR #8: UI only).
 *
 * TODO(logic): read the day from a TodayRepository: render from cache at once and
 * refresh behind with `GET /api/v1/bonds/{bondId}/today` (states.md §3; no chained
 * round trips), refresh again on every foreground (states.md §10: never rely on a
 * push having arrived), and map `bondDay.status` as `TodayStatus` documents.
 * Which bond: the member's one active bond, from `GET /api/v1/bonds`.
 */
class TodayViewModel(
    private val loop: FakeDailyLoop,
) : ViewModel() {

    val state: StateFlow<TodayState> = loop.day
        .map { day ->
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
                // TODO(logic): DELETE /api/v1/entries/{entryId}, optimistic, no confirmation
                //  (states.md §4: not irreversible yet). A failure restores the entry and says so.
                //  Whether the deleted words come back as a draft is not in states.md: owner ruling owed.
                loop.setStatus(TodayStatus.NotWritten)
            }
            TodayAction.OnDeleteAfterRevealClick -> send(TodayEvent.NavigateToDeleteConfirm)
            TodayAction.OnArchiveClick -> {
                // TODO(logic): navigate to Archive (GET /api/v1/bonds/{bondId}/days), not built yet.
            }
            TodayAction.OnSettingsClick -> send(TodayEvent.NavigateToSettings)
            TodayAction.OnShareInviteClick -> {
                // TODO(logic): open the OS share sheet with the invite link (POST /api/v1/bonds/{bondId}/invites
                //  issues a fresh one if the old one expired). It navigates nowhere, and the sheet closing is
                //  never proof the invite was sent (SKILL.md §9).
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

    private fun send(event: TodayEvent) {
        viewModelScope.launch { _events.send(event) }
    }

    private companion object {
        /** The only layouts that may hold the partner's words: the day is open, or it closed SOLO with theirs. */
        val PartnerVisible = setOf(TodayStatus.Revealed, TodayStatus.Tombstone, TodayStatus.SoloTheyWrote)
    }
}
