package com.moyi.app.feature.today.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moyi.app.core.ApiErrorCode
import com.moyi.app.core.DataError
import com.moyi.app.core.Result
import com.moyi.app.domain.bond.BondRepository
import com.moyi.app.domain.bond.Invite
import com.moyi.app.domain.today.OutboxSender
import com.moyi.app.domain.today.Reaction
import com.moyi.app.feature.today.common.DailyLoop
import com.moyi.app.feature.today.common.DailyLoopState
import com.moyi.app.feature.today.common.DeleteResult
import com.moyi.app.feature.today.common.PartnerVisible
import com.moyi.app.feature.today.common.ReactResult
import com.moyi.app.feature.today.common.nextReaction
import com.moyi.app.feature.today.common.RepositoryDailyLoop
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
 * - **Loading**: the first load draws the cached day at once when there is one for
 *   today's Bond-day, and the bare surface (no spinner) when there is not; every load
 *   refreshes behind what is shown ([RepositoryDailyLoop]). The screen asks on every foreground
 *   ([TodayAction.OnForeground]), and the loop refreshes after each write and delete.
 * - **The outbox**: every foreground also tries the queued entries again, and whenever the
 *   sender sends or refuses one (here or in the background), Today reloads: the server wins.
 * - **The reveal**: when a day turns up revealed with the partner's words, and the
 *   [RevealLedger] has not played that bond-day in this process, Today opens the reveal.
 *
 * Today is the root of the signed-in app, so it owns the loop's lifetime: the day in
 * memory is forgotten when this ViewModel is created and when it is cleared. The copy
 * on disk is the session's to wipe, when it ends (SessionManager).
 */
class TodayViewModel(
    private val loop: DailyLoop,
    private val ledger: RevealLedger,
    /** For "Share the invite again": the bond's live invite, or a new one when it has none. */
    private val bonds: BondRepository,
    /** The queue of entries sent offline: tried again on every foreground, and Today reloads when it moves. */
    private val outbox: OutboxSender,
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
        // A queued entry sent (or refused) in the background: show what the server holds now.
        viewModelScope.launch { outbox.changes.collect { loop.refresh() } }
    }

    fun onAction(action: TodayAction) {
        when (action) {
            TodayAction.OnForeground, TodayAction.OnRetryClick -> {
                ui.update { it.copy(failed = false) }
                outbox.retrySoon()
                viewModelScope.launch { loop.refresh() }
            }
            TodayAction.OnWriteClick -> {
                // Write opens on the words a refused entry left in the draft: the line has been read.
                viewModelScope.launch { loop.dismissRefusal() }
                send(TodayEvent.NavigateToWrite(editing = false))
            }
            TodayAction.OnEditClick -> {
                ui.update { it.copy(failed = false) }
                send(TodayEvent.NavigateToWrite(editing = true))
            }
            TodayAction.OnDeleteBeforeRevealClick -> ask(DeleteQuestion.BeforeReveal)
            TodayAction.OnDeleteAfterRevealClick -> ask(DeleteQuestion.AfterReveal)
            TodayAction.OnDeleteConfirm -> when (ui.value.confirming) {
                DeleteQuestion.BeforeReveal -> deleteBeforeReveal()
                DeleteQuestion.AfterReveal -> deleteAfterReveal()
                // No question is open (a second answer, or one from a dialog already gone): nothing to do.
                null -> Unit
            }
            TodayAction.OnDeleteDismiss -> ui.update { it.copy(confirming = null) }
            is TodayAction.OnReactionClick -> react(action.reaction)
            TodayAction.OnArchiveClick -> send(TodayEvent.NavigateToArchive)
            // The target is drawn only on a loaded day, so there is always a bond to open.
            TodayAction.OnSettingsClick -> (loop.state.value as? DailyLoopState.Ready)?.let { send(TodayEvent.NavigateToSettings(it.day.bondId)) }
            TodayAction.OnShareInviteClick -> shareInvite()
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

    /**
     * "Share the invite again" (states.md §8): the OS sheet with the bond's **live** invite.
     * Only when the bond is known to hold none is a new one issued, because issuing revokes
     * the old code (doc 06), which the partner may already have. If the bonds cannot be
     * read, nothing is issued: the Generic error line says so. `409 BOND_FULL` (they joined)
     * and `BOND_ARCHIVED` refresh Today, which then shows what is true. It navigates nowhere,
     * and the sheet closing is never proof the invite was sent.
     */
    private fun shareInvite() {
        if (ui.value.sharing) return
        val bondId = (loop.state.value as? DailyLoopState.Ready)?.day?.bondId ?: return
        ui.update { it.copy(sharing = true, failed = false) }
        viewModelScope.launch {
            val invite = when (val list = bonds.bonds()) {
                is Result.Failure -> null.also { ui.update { it.copy(sharing = false, failed = true) } }
                is Result.Success -> list.data.firstOrNull { it.id == bondId }?.invite ?: issueInvite(bondId)
            }
            if (invite != null) {
                ui.update { it.copy(sharing = false) }
                send(TodayEvent.ShareInvite(link = invite.link, code = invite.code))
            }
        }
    }

    private suspend fun issueInvite(bondId: String): Invite? = when (val created = bonds.createInvite(bondId)) {
        is Result.Success -> created.data
        is Result.Failure -> {
            val code = (created.error as? DataError.Network.Api)?.problem?.code
            val moved = code == ApiErrorCode.BOND_FULL || code == ApiErrorCode.BOND_ARCHIVED
            ui.update { it.copy(sharing = false, failed = !moved) }
            if (moved) loop.refresh()
            null
        }
    }

    /** Opens the delete question, unless a delete is already on its way. Nothing is deleted yet. */
    private fun ask(question: DeleteQuestion) {
        if (!ui.value.deleting) ui.update { it.copy(confirming = question, failed = false) }
    }

    /**
     * Only after the question is answered (owner ruling 2026-11-09). Then optimistic, as
     * before: the loop shows not-written at once and restores the entry if the server
     * refuses, when the Generic error line says so. The dialog has closed by then (an iOS
     * alert closes on any answer), so there is no control left to say "Deleting".
     */
    private fun deleteBeforeReveal() {
        if (ui.value.deleting) return
        ui.update { it.copy(confirming = null, deleting = true, failed = false) }
        viewModelScope.launch {
            // TODO(logic): whether the deleted words come back as a draft is not in states.md: owner ruling owed.
            val result = loop.deleteBeforeReveal()
            ui.update { it.copy(deleting = false, failed = result == DeleteResult.Failed) }
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
        ui.update { TodayUi(deleting = true, reacting = it.reacting) }
        viewModelScope.launch {
            val result = loop.deleteAfterReveal()
            ui.update { TodayUi(failed = result == DeleteResult.Failed, reacting = it.reacting) }
        }
    }

    /**
     * A chip of the reaction set (FR-048). **Optimistic**, as a favourite: the loop shows the
     * new choice at once and puts the old one back if the server refuses, and the line under
     * the set says so. A tap while one is on its way is ignored, so the calls cannot cross
     * and the last tap shown is the last one sent.
     */
    private fun react(tapped: Reaction) {
        if (ui.value.reacting) return
        val view = (loop.state.value as? DailyLoopState.Ready)?.day ?: return
        if (!view.canReact) return
        ui.update { it.copy(reacting = true, reactionFailed = false) }
        viewModelScope.launch {
            val result = loop.react(nextReaction(view.myReaction, tapped))
            ui.update { it.copy(reacting = false, reactionFailed = result == ReactResult.Failed) }
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
                myPhoto = view.myPhoto,
                partnerPhoto = view.partnerPhoto.takeIf { view.status in PartnerVisible },
                myVoice = view.myVoice,
                partnerVoice = view.partnerVoice.takeIf { view.status in PartnerVisible },
                queuedDay = view.queuedDay,
                refusal = view.refusal,
                // Only a revealed entry can be deleted this way: a dialog left from a day
                // that has since moved on must not open over a different layout.
                confirmingDelete = ui.confirming == DeleteQuestion.AfterReveal && view.status == TodayStatus.Revealed,
                // The same lock before the reveal: only over the waiting layout it was asked from.
                confirmingDeleteBeforeReveal = ui.confirming == DeleteQuestion.BeforeReveal && view.status == TodayStatus.Waiting,
                deleting = ui.deleting,
                actionFailed = ui.failed,
                canReact = view.canReact,
                myReaction = view.myReaction.takeIf { view.canReact },
                partnerReaction = view.partnerReaction,
                reactionFailed = ui.reactionFailed && view.canReact,
            )
        }
    }

    private fun send(event: TodayEvent) {
        viewModelScope.launch { _events.send(event) }
    }

    override fun onCleared() {
        loop.clear()
    }

    /** Which of my entries the open delete dialog asks about: what deleting means differs. */
    private enum class DeleteQuestion { BeforeReveal, AfterReveal }

    private data class TodayUi(
        /** The open delete question, or null when no dialog is open. */
        val confirming: DeleteQuestion? = null,
        val deleting: Boolean = false,
        val failed: Boolean = false,
        /** An invite is being found or issued for the share sheet: a second tap waits for it. */
        val sharing: Boolean = false,
        /** A reaction is on its way: a second tap is ignored until it answers. */
        val reacting: Boolean = false,
        /** The last reaction did not go through. */
        val reactionFailed: Boolean = false,
    )
}
