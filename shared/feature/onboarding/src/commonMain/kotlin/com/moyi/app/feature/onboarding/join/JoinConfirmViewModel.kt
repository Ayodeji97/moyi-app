package com.moyi.app.feature.onboarding.join

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moyi.app.core.ApiErrorCode
import com.moyi.app.core.DataError
import com.moyi.app.core.Result
import com.moyi.app.designsystem.presentation.UiText
import com.moyi.app.domain.bond.BondRepository
import com.moyi.app.domain.bond.EmptyBondExit
import com.moyi.app.domain.bond.InvitePreview
import com.moyi.app.domain.bond.joinedByLostAccept
import com.moyi.app.domain.today.TodayCache
import com.moyi.app.feature.onboarding.common.RelationshipType
import com.moyi.app.feature.onboarding.common.code
import com.moyi.app.feature.onboarding.common.formMessage
import com.moyi.app.feature.onboarding.common.isUnverified
import com.moyi.app.feature.onboarding.resources.Res
import com.moyi.app.feature.onboarding.resources.join_error_limit
import com.moyi.app.feature.onboarding.resources.join_error_own_code
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Join · confirm. Shows the preview the code screen resolved, and joins with
 * `POST /invites/{code}/accept`, which spends the invite (single use, FR-023).
 *
 * - `200`: Today, Day 1 for both.
 * - `404 INVITE_NOT_USABLE`, or a `422`: cannot be used. The code may have
 *   been revoked, spent or filled since it resolved; nothing says which.
 * - `409 ALREADY_MEMBER`: the person is in this bond already (usually the
 *   creator trying their own code), so they are where joining would take
 *   them: Today. Except when joining from their own empty bond ([leaving]):
 *   there the code is that bond's own, so it is said, and nothing is ended.
 * - `409 BOND_LIMIT_REACHED`: said, because it is the caller's own state.
 * - `403 EMAIL_NOT_VERIFIED`: the gate.
 * - Offline, `429`, anything else: inline, and they can try again.
 *
 * **From an empty bond** ([leaving], owner ruling 2026-11-13): the join comes first, and only
 * once it has succeeded is the empty bond ended ([EmptyBondExit], which reads the bonds again
 * and ends it only while nobody has joined it). A join that fails ends nothing. A leave that
 * fails does not hold the person here: it is kept on the device and tried again, and Today
 * shows the bond they joined meanwhile.
 *
 * **A join whose answer was lost** (from an empty bond only): a timeout or a dropped
 * connection can hide an accept the server did make, and pressing Join again then gets `404
 * INVITE_NOT_USABLE` (the invite is spent), so the empty bond would stay. So the bonds are read
 * once when the screen opens, before anything is accepted, and when an accept gets no answer
 * (or a later one is refused as spent) they are read again: a bond that was not there before,
 * someone else's and the invite's own ([joinedByLostAccept]), is the join that happened. Then
 * it finishes as a join that was answered, and the leave still checks the bonds itself. With
 * no first reading, or any doubt, nothing is assumed: the failure is shown, as before.
 *
 * **The day on this device** ([TodayCache]) is another bond's once a join succeeds, so it is
 * forgotten before Today opens: Today would otherwise draw the empty bond's day first, and
 * ask for it, before the bonds came back with the joined one.
 */
class JoinConfirmViewModel(
    private val code: String,
    private val preview: InvitePreview,
    private val leaving: LeavingBond?,
    private val bonds: BondRepository,
    private val emptyBonds: EmptyBondExit,
    private val cachedDay: TodayCache,
) : ViewModel() {
    private val _state = MutableStateFlow(
        JoinConfirmState(
            inviterName = preview.inviterName,
            bondName = preview.bondName,
            type = RelationshipType.of(preview.bondType),
            leaving = leaving,
        ),
    )
    val state: StateFlow<JoinConfirmState> = _state.asStateFlow()

    private val _events = Channel<JoinConfirmEvent>()
    val events = _events.receiveAsFlow()

    /** From an empty bond: every bond id the person was in before any accept was sent; null until read. */
    private var bondsBefore: Set<String>? = null

    /** Once an accept has gone, the bonds are never read as "before" again: the join may be in them. */
    private var acceptSent = false

    /** An accept on this screen got no answer, so a later "spent" may be this person's own join. */
    private var answerLost = false

    /** Read while the person reads the screen, so Join waits for no extra round trip. */
    private val firstLook: Job? = leaving?.let { viewModelScope.launch { lookBefore() } }

    fun onAction(action: JoinConfirmAction) {
        when (action) {
            JoinConfirmAction.OnJoinClick -> join()
            JoinConfirmAction.OnNotNowClick, JoinConfirmAction.OnBackClick ->
                if (!_state.value.joining) viewModelScope.launch { _events.send(JoinConfirmEvent.NavigateBack) }
        }
    }

    private fun join() {
        if (_state.value.joining) return
        _state.update { it.copy(joining = true, failure = null) }
        viewModelScope.launch {
            if (leaving != null && !acceptSent) {
                firstLook?.join()
                if (bondsBefore == null) lookBefore()
            }
            acceptSent = true
            val accepted = bonds.acceptInvite(code)
            // Joined (or already in it): the button stays Joining until the screen goes,
            // so a second tap cannot send a second accept while Finish is on its way.
            if (accepted is Result.Success) {
                joined(accepted.data.id)
                return@launch
            }
            val error = (accepted as Result.Failure).error
            // No answer, or "spent" after one that had none: the join may have happened after all.
            val noAnswer = error !is DataError.Network.Api
            if (noAnswer) answerLost = true
            if (leaving != null && (noAnswer || (answerLost && error.code() == ApiErrorCode.INVITE_NOT_USABLE))) {
                val happened = joinedAfterAll(leaving.id)
                if (happened != null) {
                    joined(happened)
                    return@launch
                }
            }
            if (error.code() == ApiErrorCode.ALREADY_MEMBER && leaving == null) {
                _events.send(JoinConfirmEvent.Finish)
                return@launch
            }
            _state.update { it.copy(joining = false) }
            when {
                // Their own bond's code: joining it would change nothing, and nothing is ended.
                error.code() == ApiErrorCode.ALREADY_MEMBER ->
                    _state.update { it.copy(failure = UiText.Resource(Res.string.join_error_own_code)) }
                error.code() == ApiErrorCode.INVITE_NOT_USABLE || error.code() == ApiErrorCode.VALIDATION_FAILED ->
                    _events.send(JoinConfirmEvent.NavigateToCannotBeUsed)
                error.isUnverified() -> _events.send(JoinConfirmEvent.NavigateToGate)
                error.code() == ApiErrorCode.BOND_LIMIT_REACHED ->
                    _state.update { it.copy(failure = UiText.Resource(Res.string.join_error_limit)) }
                else -> _state.update { it.copy(failure = error.formMessage()) }
            }
        }
    }

    /** Joined [joinedBondId]: only now is the empty bond ended. Awaited, so Today, next, lists the bonds after it. */
    private suspend fun joined(joinedBondId: String) {
        leaving?.let { emptyBonds.leaveAfterJoining(emptyBondId = it.id, joinedBondId = joinedBondId) }
        cachedDay.clear()
        _events.send(JoinConfirmEvent.Finish)
    }

    /** The bonds before any accept. A reading that came back after one was sent is not used. */
    private suspend fun lookBefore() {
        val listed = bonds.bonds() as? Result.Success ?: return
        if (!acceptSent && bondsBefore == null) bondsBefore = listed.data.map { it.id }.toSet()
    }

    /** The id of the bond a lost accept joined, or null when the bonds do not show one beyond doubt. */
    private suspend fun joinedAfterAll(leavingBondId: String): String? {
        val before = bondsBefore ?: return null
        val listed = bonds.bonds() as? Result.Success ?: return null
        return listed.data.joinedByLostAccept(preview, before, leavingBondId)?.id
    }
}
