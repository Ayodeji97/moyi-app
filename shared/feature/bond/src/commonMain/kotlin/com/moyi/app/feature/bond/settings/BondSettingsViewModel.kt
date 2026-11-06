package com.moyi.app.feature.bond.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moyi.app.core.ApiErrorCode
import com.moyi.app.core.DataError
import com.moyi.app.core.Result
import com.moyi.app.designsystem.presentation.UiText
import com.moyi.app.domain.auth.AuthRepository
import com.moyi.app.domain.bond.BondRepository
import com.moyi.app.domain.bond.BondSettings
import com.moyi.app.domain.bond.BondSettingsChange
import com.moyi.app.domain.bond.BondSettingsRepository
import com.moyi.app.domain.bond.BondType
import com.moyi.app.domain.bond.TimeOfDay
import com.moyi.app.feature.bond.resources.Res
import com.moyi.app.feature.bond.resources.settings_error_conflict
import com.moyi.app.feature.bond.resources.settings_error_generic
import com.moyi.app.feature.bond.resources.settings_error_rate_limited
import com.moyi.app.feature.bond.resources.settings_invite_error_full
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
 * - **Leave** is confirmed first (SKILL.md §3), then `POST …/leave`, then
 *   [BondSettingsEvent.Ended]. A failure keeps the person here, with the reason.
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
            BondSettingsAction.OnRevealTimeClick -> if (canEdit()) _state.update { it.copy(pickingRevealTime = true, error = null) }
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
            BondSettingsAction.OnLeaveClick -> if (canEdit()) _state.update { it.copy(confirmingLeave = true, error = null) }
            BondSettingsAction.OnLeaveConfirm -> leave()
            BondSettingsAction.OnLeaveDismiss -> _state.update { it.copy(confirmingLeave = false) }
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
        if (!canEdit()) return
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
        if (!canEdit()) return
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
        if (!canEdit()) return
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
        if (!canEdit()) return
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
        if (!canEdit()) return
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

    private fun save(field: SettingsField, change: BondSettingsChange, closeEditor: Boolean = false) {
        if (!canEdit()) return
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

    private fun leave() {
        _state.update { it.copy(confirmingLeave = false) }
        if (!canEdit()) return
        _state.update { it.copy(saving = SettingsField.Leave, error = null) }
        viewModelScope.launch {
            when (val result = repository.leave(bondId)) {
                is Result.Success -> send(BondSettingsEvent.Ended)
                // Already ended, or gone: either way the person is out, which is what they asked for.
                is Result.Failure -> if (result.error.isGone()) {
                    send(BondSettingsEvent.Ended)
                } else {
                    _state.update { it.copy(saving = null, error = SettingsError(SettingsField.Leave, result.error.message())) }
                }
            }
        }
    }

    private fun signOut() {
        if (_state.value.signingOut) return
        _state.update { it.copy(signingOut = true) }
        // Always ends the local session, even offline (AuthRepository.logout); the shell routes.
        viewModelScope.launch { auth.logout() }
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
