package com.moyi.app.feature.bond.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moyi.app.core.ApiErrorCode
import com.moyi.app.core.DataError
import com.moyi.app.core.Result
import com.moyi.app.designsystem.presentation.UiText
import com.moyi.app.domain.auth.AuthRepository
import com.moyi.app.domain.bond.BondRepository
import com.moyi.app.domain.today.EntryOutbox
import com.moyi.app.domain.today.OutboxSender
import com.moyi.app.domain.bond.BondSettings
import com.moyi.app.domain.bond.BondSettingsChange
import com.moyi.app.domain.bond.BondSettingsRepository
import com.moyi.app.domain.bond.BondStatus
import com.moyi.app.domain.bond.BondType
import com.moyi.app.domain.bond.TimeOfDay
import com.moyi.app.feature.bond.resources.Res
import com.moyi.app.feature.bond.resources.settings_error_conflict
import com.moyi.app.feature.bond.resources.settings_error_generic
import com.moyi.app.feature.bond.resources.settings_error_rate_limited
import com.moyi.app.feature.bond.resources.settings_invite_error_full
import com.moyi.app.feature.bond.resources.settings_leave_error_ended
import com.moyi.app.feature.bond.resources.settings_tz_error_too_soon
import com.moyi.app.feature.bond.resources.settings_tz_error_unsupported
import com.moyi.app.feature.onboarding.createbond.CreateBondViewModel
import kotlinx.datetime.TimeZone
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds
import com.moyi.app.feature.onboarding.resources.Res as OnboardingRes
import com.moyi.app.feature.onboarding.resources.bond_name_error_blank
import com.moyi.app.feature.onboarding.resources.bond_name_error_too_long

/**
 * Bond settings (states.md §8) for [bondId]: the root and its two editors share
 * this one ViewModel, scoped to the settings graph.
 *
 * - **Load**: `GET /bonds/{id}`, keeping its `ETag` as the version. A failure is
 *   the Generic error with Try again; a `404` means the person is not in the bond
 *   any more, which is [BondSettingsEvent.Ended].
 * - **Each change** is a `PATCH` with `If-Match`, one at a time. A change to what
 *   is already there sends nothing. Every failure keeps the input and says so
 *   inline, at the row or editor it came from.
 * - **`412`** (the other member changed something first): reload, show what is
 *   there now, and say "This changed while you were looking at it." The change is
 *   not retried blindly; the editor keeps what the person typed, to save again.
 * - **Leave** and **Block and leave** are confirmed on their own screens (SKILL.md §3),
 *   each with FR-029a's offer to withdraw the person's entries (off on leave, on on
 *   block), then the `POST`, then [BondSettingsEvent.Ended]. A failure keeps the person
 *   there, with the reason.
 * - **Close the box** (FR-028): one `POST` asks or agrees, one `DELETE` withdraws, declines
 *   or calls off the cooling-off ([BondSettingsState.closeBoxStep]).
 * - **Sign out** ends the session; the shell routes to sign in on its own.
 * - **The shared time zone** (FR-027, ADR-0030) is three screens over one pending
 *   change ([BondSettingsState.timeZoneStep]): propose a zone, cancel your own proposal,
 *   or agree to (or decline) the other member's. Proposing on a bond nobody has joined
 *   applies at once, and goes back to the root. The 30-day rule is a `409
 *   TIMEZONE_CHANGE_TOO_SOON`, said plainly and never retried. A `404` from agree or
 *   cancel means nothing is waiting any more (cancelled or lapsed): reload, not Ended.
 * - **The invite** (states.md §2): share and copy the live one, cancel it (`DELETE`), or
 *   create a new one, which replaces any other. `409 BOND_FULL` means someone joined:
 *   reload and say so. A `404` from cancel means it was already dead: done.
 */
class BondSettingsViewModel(
    private val bondId: String,
    private val repository: BondSettingsRepository,
    private val auth: AuthRepository,
    private val bonds: BondRepository,
    /** Entries sent while offline: a sign-out tries them once more before the session wipe takes them. */
    private val outbox: EntryOutbox,
    private val sender: OutboxSender,
    /** Every zone id the platform knows; kotlinx-datetime's list unless a test gives one. */
    private val zoneIds: () -> Set<String> = { TimeZone.availableZoneIds },
) : ViewModel() {
    private val _state = MutableStateFlow(BondSettingsState())
    val state: StateFlow<BondSettingsState> = _state.asStateFlow()

    private val _events = Channel<BondSettingsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        load()
    }

    fun onAction(action: BondSettingsAction) {
        when (action) {
            BondSettingsAction.OnRetryClick -> load()
            BondSettingsAction.OnNameClick -> {
                val name = _state.value.settings?.name ?: return
                _state.update { it.copy(nameDraft = name, nameError = null, error = null) }
                send(BondSettingsEvent.OpenName)
            }
            is BondSettingsAction.OnNameChange -> _state.update {
                it.copy(nameDraft = action.value, nameError = if (action.value.length > MAX_NAME_LENGTH) tooLong else null)
            }
            BondSettingsAction.OnNameSave -> saveName()
            BondSettingsAction.OnTypeClick -> {
                _state.update { it.copy(error = null) }
                send(BondSettingsEvent.OpenType)
            }
            is BondSettingsAction.OnTypeSelect -> {
                if (_state.value.settings?.type == action.type) return send(BondSettingsEvent.Saved)
                save(SettingsField.Type, BondSettingsChange(type = action.type), closeEditor = true)
            }
            BondSettingsAction.OnRevealTimeClick -> if (canChangeSettings()) _state.update { it.copy(pickingRevealTime = true, error = null) }
            is BondSettingsAction.OnRevealTimePicked -> {
                _state.update { it.copy(pickingRevealTime = false) }
                val time = TimeOfDay(action.hour, action.minute)
                if (_state.value.settings?.revealTime == time) return
                save(SettingsField.RevealTime, BondSettingsChange(revealTime = time))
            }
            BondSettingsAction.OnRevealTimeDismiss -> _state.update { it.copy(pickingRevealTime = false) }
            BondSettingsAction.OnRestDaysToggle -> {
                val settings = _state.value.settings ?: return
                // Rest days on is strict mode off (states.md §8), so toggling one flips the other.
                save(SettingsField.RestDays, BondSettingsChange(strictMode = !settings.strictMode))
            }
            BondSettingsAction.OnSignOutClick -> signOut()
            BondSettingsAction.OnNotificationsClick -> if (canEdit()) {
                _state.update { it.copy(error = null) }
                send(BondSettingsEvent.OpenNotifications)
            }
            BondSettingsAction.OnAccountClick -> if (!_state.value.signingOut) send(BondSettingsEvent.OpenAccount)
            BondSettingsAction.OnSignOutUnsentConfirm -> {
                _state.update { it.copy(confirmingUnsentSignOut = false, signingOut = true) }
                viewModelScope.launch { auth.logout() }
            }
            BondSettingsAction.OnSignOutUnsentDismiss -> _state.update { it.copy(confirmingUnsentSignOut = false) }
            BondSettingsAction.OnLeaveClick -> if (canEdit()) {
                // Every visit starts from the default: entries kept (FR-029a, off on leave).
                _state.update { it.copy(withdrawOnLeave = false, error = null) }
                send(BondSettingsEvent.OpenLeave)
            }
            BondSettingsAction.OnLeaveWithdrawToggle -> if (_state.value.saving == null) _state.update { it.copy(withdrawOnLeave = !it.withdrawOnLeave) }
            BondSettingsAction.OnLeaveConfirm -> endBond(SettingsField.Leave)
            BondSettingsAction.OnBlockClick -> if (canEdit() && _state.value.settings?.partnerName != null) {
                // On by default on block (`26` §3.3), every visit.
                _state.update { it.copy(withdrawOnBlock = true, error = null) }
                send(BondSettingsEvent.OpenBlock)
            }
            BondSettingsAction.OnBlockWithdrawToggle -> if (_state.value.saving == null) _state.update { it.copy(withdrawOnBlock = !it.withdrawOnBlock) }
            BondSettingsAction.OnBlockConfirm -> endBond(SettingsField.Block)
            BondSettingsAction.OnCloseBoxClick -> if (canEdit()) {
                _state.update { it.copy(error = null) }
                send(BondSettingsEvent.OpenCloseBox)
            }
            BondSettingsAction.OnCloseBoxConfirm -> requestDeletion()
            BondSettingsAction.OnCloseBoxWithdraw -> cancelDeletion()
            BondSettingsAction.OnEditorClosed -> _state.update { it.copy(error = null, nameError = null) }
            BondSettingsAction.OnTimeZoneClick -> {
                val settings = _state.value.settings ?: return
                _state.update { it.copy(zoneDraft = settings.anchorTimezone, zoneQuery = "", error = null) }
                send(BondSettingsEvent.OpenTimeZone)
            }
            BondSettingsAction.OnZoneFieldClick -> openZonePicker()
            is BondSettingsAction.OnZoneQueryChange -> _state.update { it.copy(zoneQuery = action.value) }
            is BondSettingsAction.OnZonePicked -> {
                _state.update { it.copy(zoneDraft = action.zoneId, zoneQuery = "", error = null) }
                send(BondSettingsEvent.ZonePicked)
            }
            BondSettingsAction.OnProposeClick -> propose()
            BondSettingsAction.OnAgreeClick -> agree()
            BondSettingsAction.OnWithdrawProposalClick -> withdrawProposal()
            BondSettingsAction.OnInviteClick -> {
                _state.update { it.copy(error = null, inviteCopied = false) }
                send(BondSettingsEvent.OpenInvite)
            }
            BondSettingsAction.OnShareInviteClick -> _state.value.settings?.invite?.let { send(BondSettingsEvent.ShareInvite(it.link, it.code)) }
            BondSettingsAction.OnCopyInviteClick -> _state.value.settings?.invite?.let { invite ->
                _state.update { it.copy(inviteCopied = true) }
                send(BondSettingsEvent.CopyInviteCode(invite.code))
            }
            BondSettingsAction.OnCancelInviteClick -> cancelInvite()
            BondSettingsAction.OnCreateInviteClick -> createInvite()
        }
    }

    // ----- the shared time zone -----

    /** Loads the zones on first open; a platform that gives none shows the field's error, and reopening retries. */
    private fun openZonePicker() {
        if (_state.value.zones.isEmpty()) {
            val zones = zoneIds().filter(CreateBondViewModel::isRegionZone).sortedWith(compareBy({ cityOf(it) }, { it }))
            _state.update { it.copy(zones = zones, zonesFailed = zones.isEmpty()) }
            if (zones.isEmpty()) return
        }
        _state.update { it.copy(zoneQuery = "", error = null) }
        send(BondSettingsEvent.OpenZonePicker)
    }

    private fun propose() {
        if (!canChangeSettings()) return
        val current = _state.value.settings ?: return
        val zone = _state.value.zoneDraft ?: return
        // The zone it already is: nothing to ask the other member.
        if (zone == current.anchorTimezone) return send(BondSettingsEvent.Saved)
        _state.update { it.copy(saving = SettingsField.TimeZone, zoneSecondaryInFlight = false, error = null) }
        viewModelScope.launch {
            when (val result = repository.proposeTimezone(bondId, zone)) {
                is Result.Success -> {
                    _state.update { it.copy(settings = result.data, saving = null) }
                    // Nobody to agree (a bond waiting for its second member): it applied at once.
                    if (result.data.pendingTimezone == null) send(BondSettingsEvent.Saved)
                }
                is Result.Failure -> when (result.error.code()) {
                    // Someone proposed first: show theirs (or mine, from another phone) and say so.
                    ApiErrorCode.PROPOSAL_PENDING -> conflict(SettingsField.TimeZone)
                    ApiErrorCode.VALIDATION_FAILED -> zoneFailed(unsupportedZone)
                    else -> zoneFailure(result.error)
                }
            }
        }
    }

    private fun agree() {
        if (!canChangeSettings()) return
        val pending = _state.value.settings?.pendingTimezone?.takeUnless { it.proposedByMe } ?: return
        _state.update { it.copy(saving = SettingsField.TimeZone, zoneSecondaryInFlight = false, error = null) }
        viewModelScope.launch {
            when (val result = repository.confirmTimezone(bondId, pending.id)) {
                is Result.Success -> {
                    _state.update { it.copy(settings = result.data, saving = null) }
                    send(BondSettingsEvent.Saved)
                }
                // Nothing waiting any more (cancelled or lapsed), or it was mine after all.
                is Result.Failure -> if (result.error.isNotFound() || result.error.code() == ApiErrorCode.PROPOSAL_NEEDS_OTHER_MEMBER) {
                    conflict(SettingsField.TimeZone)
                } else {
                    zoneFailure(result.error)
                }
            }
        }
    }

    /** The proposer cancels, or the other member declines: either way the `DELETE`, then the root. */
    private fun withdrawProposal() {
        if (!canChangeSettings()) return
        if (_state.value.settings?.pendingTimezone == null) return
        _state.update { it.copy(saving = SettingsField.TimeZone, zoneSecondaryInFlight = true, error = null) }
        viewModelScope.launch {
            val result = repository.cancelTimezoneChange(bondId)
            // A 404 is "nothing was waiting": what the person asked for is already true.
            if (result is Result.Failure && !result.error.isNotFound()) return@launch zoneFailure(result.error)
            refreshAfter { it.copy(pendingTimezone = null) }
            send(BondSettingsEvent.Saved)
        }
    }

    private suspend fun zoneFailure(error: DataError.Network) {
        when {
            error.isGone() -> {
                _state.update { it.copy(saving = null) }
                send(BondSettingsEvent.Ended)
            }
            error.code() == ApiErrorCode.TIMEZONE_CHANGE_TOO_SOON -> zoneFailed(UiText.Resource(Res.string.settings_tz_error_too_soon))
            else -> zoneFailed(error.message())
        }
    }

    private fun zoneFailed(message: UiText) =
        _state.update { it.copy(saving = null, zoneSecondaryInFlight = false, error = SettingsError(SettingsField.TimeZone, message)) }

    // ----- the invite -----

    private fun cancelInvite() {
        if (!canChangeSettings()) return
        val invite = _state.value.settings?.invite ?: return
        _state.update { it.copy(saving = SettingsField.Invite, error = null) }
        viewModelScope.launch {
            val result = bonds.revokeInvite(bondId, invite.id)
            // A 404 is an invite already dead: cancelled, as asked.
            if (result is Result.Failure && !result.error.isNotFound()) return@launch inviteFailure(result.error)
            refreshAfter { it.copy(invite = null) }
            _state.update { it.copy(inviteCopied = false) }
        }
    }

    private fun createInvite() {
        if (!canChangeSettings()) return
        _state.update { it.copy(saving = SettingsField.Invite, error = null) }
        viewModelScope.launch {
            when (val result = bonds.createInvite(bondId)) {
                is Result.Success -> _state.update {
                    it.copy(settings = it.settings?.copy(invite = result.data), saving = null, inviteCopied = false)
                }
                is Result.Failure -> inviteFailure(result.error)
            }
        }
    }

    private suspend fun inviteFailure(error: DataError.Network) {
        when {
            error.isGone() -> {
                _state.update { it.copy(saving = null) }
                send(BondSettingsEvent.Ended)
            }
            // Someone joined while this screen was open: the bond needs no invite now.
            error.code() == ApiErrorCode.BOND_FULL -> {
                refreshAfter { it.copy(invite = null) }
                _state.update { it.copy(error = SettingsError(SettingsField.Invite, UiText.Resource(Res.string.settings_invite_error_full))) }
            }
            else -> _state.update { it.copy(saving = null, error = SettingsError(SettingsField.Invite, error.message())) }
        }
    }

    /**
     * Reads the bond again after a change whose answer carries no bond (a `204`), so the
     * version and the rest are what is there now. If that read fails, [assumed] is shown:
     * what the change certainly did, on top of what was there.
     */
    private suspend fun refreshAfter(assumed: (BondSettings) -> BondSettings) {
        val fresh = (repository.settings(bondId) as? Result.Success)?.data
        _state.update { it.copy(settings = fresh ?: it.settings?.let(assumed), saving = null, zoneSecondaryInFlight = false) }
    }

    private fun load() {
        _state.update { it.copy(load = SettingsLoad.Loading, error = null) }
        viewModelScope.launch {
            when (val result = repository.settings(bondId)) {
                is Result.Success -> _state.update { it.copy(load = SettingsLoad.Ready, settings = result.data) }
                is Result.Failure -> if (result.error.isGone()) {
                    send(BondSettingsEvent.Ended)
                } else {
                    _state.update { it.copy(load = SettingsLoad.Failed) }
                }
            }
        }
    }

    private fun saveName() {
        val draft = _state.value.nameDraft
        when {
            draft.isBlank() -> return _state.update { it.copy(nameError = UiText.Resource(OnboardingRes.string.bond_name_error_blank)) }
            draft.length > MAX_NAME_LENGTH -> return _state.update { it.copy(nameError = tooLong) }
            draft == _state.value.settings?.name -> return send(BondSettingsEvent.Saved)
        }
        save(SettingsField.Name, BondSettingsChange(name = draft), closeEditor = true)
    }

    private fun canEdit(): Boolean = _state.value.let { it.settings != null && it.saving == null && !it.signingOut }

    /**
     * During the cooling-off the bond takes no other write (`409 BOND_ARCHIVED`, states.md §9):
     * nothing is gone, and nothing more can be changed. Leaving, blocking and calling it off stay.
     */
    private fun canChangeSettings(): Boolean = canEdit() && _state.value.settings?.coolingOff != true

    private fun save(field: SettingsField, change: BondSettingsChange, closeEditor: Boolean = false) {
        if (!canChangeSettings()) return
        val current = _state.value.settings ?: return
        _state.update { it.copy(saving = field, error = null) }
        viewModelScope.launch {
            when (val result = repository.update(current.bondId, current.version, change)) {
                is Result.Success -> {
                    _state.update { it.copy(settings = result.data, saving = null) }
                    if (closeEditor) send(BondSettingsEvent.Saved)
                }
                is Result.Failure -> failed(field, result.error)
            }
        }
    }

    private suspend fun failed(field: SettingsField, error: DataError.Network) {
        when {
            error.isGone() -> {
                _state.update { it.copy(saving = null) }
                send(BondSettingsEvent.Ended)
            }
            error.code() == ApiErrorCode.PRECONDITION_FAILED -> conflict(field)
            field == SettingsField.Name && error.code() == ApiErrorCode.VALIDATION_FAILED -> _state.update {
                val message = if (it.nameDraft.length > MAX_NAME_LENGTH) tooLong else UiText.Resource(OnboardingRes.string.bond_name_error_blank)
                it.copy(saving = null, nameError = message)
            }
            else -> _state.update { it.copy(saving = null, error = SettingsError(field, error.message())) }
        }
    }

    /**
     * Someone else changed the bond since it was read. Read it again, so what the
     * screen shows (and the version the next change sends) is what is there now.
     */
    private suspend fun conflict(field: SettingsField) {
        when (val fresh = repository.settings(bondId)) {
            is Result.Success -> _state.update {
                it.copy(settings = fresh.data, saving = null, error = SettingsError(field, UiText.Resource(Res.string.settings_error_conflict)))
            }
            is Result.Failure -> if (fresh.error.isGone()) {
                _state.update { it.copy(saving = null) }
                send(BondSettingsEvent.Ended)
            } else {
                _state.update { it.copy(saving = null, error = SettingsError(field, fresh.error.message())) }
            }
        }
    }

    /**
     * Leave or block: one `POST`, the person's FR-029a choice sent as it stands, then
     * [BondSettingsEvent.Ended]. The bond ends for both either way, and the other member
     * sees the same archived bond whichever it was (`26` §2.1).
     */
    private fun endBond(field: SettingsField) {
        if (!canEdit()) return
        val withdraw = if (field == SettingsField.Block) _state.value.withdrawOnBlock else _state.value.withdrawOnLeave
        _state.update { it.copy(saving = field, error = null) }
        viewModelScope.launch {
            val result = if (field == SettingsField.Block) repository.block(bondId, withdraw) else repository.leave(bondId, withdraw)
            val error = (result as? Result.Failure)?.error ?: return@launch send(BondSettingsEvent.Ended)
            when {
                // It had already ended, and the leave withdrew nothing (doc 06): say so rather
                // than let the person think their words are gone. Their choice is kept.
                withdraw && field == SettingsField.Leave && error.code() == ApiErrorCode.BOND_ARCHIVED ->
                    _state.update { it.copy(saving = null, error = SettingsError(field, UiText.Resource(Res.string.settings_leave_error_ended))) }
                // Already ended, or gone: either way the person is out, which is what they asked for.
                error.isGone() -> send(BondSettingsEvent.Ended)
                else -> _state.update { it.copy(saving = null, error = SettingsError(field, error.message())) }
            }
        }
    }

    // ----- close the box (FR-028) -----

    /** Ask, or agree: the answer says which, and the screen follows it (Asked or CoolingOff). */
    private fun requestDeletion() {
        if (!canEdit()) return
        if (_state.value.closeBoxStep == CloseBoxStep.Asked || _state.value.closeBoxStep == CloseBoxStep.CoolingOff) return
        _state.update { it.copy(saving = SettingsField.CloseBox, closeBoxSecondaryInFlight = false, error = null) }
        viewModelScope.launch {
            when (val result = repository.requestDeletion(bondId)) {
                is Result.Success -> _state.update { it.copy(settings = result.data, saving = null) }
                is Result.Failure -> closeBoxFailure(result.error)
            }
        }
    }

    /**
     * Withdraw the ask, decline the other member's, or call off the cooling-off: the same
     * `DELETE`, then the root. A `404` is "nothing was pending": already what was asked for.
     */
    private fun cancelDeletion() {
        if (!canEdit()) return
        if (_state.value.closeBoxStep == CloseBoxStep.Ask) return
        _state.update { it.copy(saving = SettingsField.CloseBox, closeBoxSecondaryInFlight = true, error = null) }
        viewModelScope.launch {
            val result = repository.cancelDeletion(bondId)
            if (result is Result.Failure && !result.error.isNotFound()) return@launch closeBoxFailure(result.error)
            refreshAfter {
                // Called off: ACTIVE again (states.md §9; a bond somebody left could not have been asked).
                val status = if (it.status == BondStatus.PENDING_DELETION) BondStatus.ACTIVE else it.status
                it.copy(pendingDeletion = null, deletionScheduledFor = null, status = status)
            }
            _state.update { it.copy(closeBoxSecondaryInFlight = false) }
            send(BondSettingsEvent.Saved)
        }
    }

    private suspend fun closeBoxFailure(error: DataError.Network) {
        if (error.isGone()) {
            _state.update { it.copy(saving = null, closeBoxSecondaryInFlight = false) }
            send(BondSettingsEvent.Ended)
        } else {
            _state.update { it.copy(saving = null, closeBoxSecondaryInFlight = false, error = SettingsError(SettingsField.CloseBox, error.message())) }
        }
    }

    /**
     * Signing out wipes everything on the device (FR-124), entries still waiting to send
     * included. So a voluntary sign-out first tries to send them (one pass, at most
     * [UNSENT_FLUSH_LIMIT]); if any are still waiting, it asks before deleting someone's
     * words. Nothing waiting, it signs out at once, as before. An involuntary end (a refresh
     * refused, a token reused) asks nobody: privacy wins, and the wipe takes them.
     */
    private fun signOut() {
        if (_state.value.signingOut) return
        _state.update { it.copy(signingOut = true) }
        viewModelScope.launch {
            if (unsentAfterLastTry()) {
                _state.update { it.copy(signingOut = false, confirmingUnsentSignOut = true) }
                return@launch
            }
            // Always ends the local session, even offline (AuthRepository.logout); the shell routes.
            auth.logout()
        }
    }

    /** True when entries would be lost: some were waiting, and one more try did not send them all. */
    private suspend fun unsentAfterLastTry(): Boolean {
        val waiting = runCatching { outbox.queued().isNotEmpty() }.getOrDefault(false)
        if (!waiting) return false
        withTimeoutOrNull(UNSENT_FLUSH_LIMIT) { sender.sendQueued() }
        return runCatching { outbox.queued().isNotEmpty() }.getOrDefault(false)
    }

    private fun send(event: BondSettingsEvent) {
        viewModelScope.launch { _events.send(event) }
    }

    private fun DataError.Network.code(): ApiErrorCode? = (this as? DataError.Network.Api)?.problem?.code

    private fun DataError.Network.isNotFound(): Boolean = (this as? DataError.Network.Api)?.problem?.status == 404

    /** Not a member any more (`404`), or the bond has ended (`409 BOND_ARCHIVED`). */
    private fun DataError.Network.isGone(): Boolean =
        (this as? DataError.Network.Api)?.problem?.status == 404 || code() == ApiErrorCode.BOND_ARCHIVED

    private fun DataError.Network.message(): UiText = UiText.Resource(
        if (code() == ApiErrorCode.RATE_LIMITED) Res.string.settings_error_rate_limited else Res.string.settings_error_generic,
    )

    private val tooLong = UiText.Resource(OnboardingRes.string.bond_name_error_too_long)
    private val unsupportedZone = UiText.Resource(Res.string.settings_tz_error_unsupported)

    companion object {
        /** `PatchBondRequest.name` maxLength, counted as the backend counts it (UTF-16 units). */
        const val MAX_NAME_LENGTH = 60
    }
}

/** How long a sign-out waits for one last send of the waiting entries before it asks. */
private val UNSENT_FLUSH_LIMIT = 10.seconds
