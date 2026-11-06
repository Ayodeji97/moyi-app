package com.moyi.app.feature.bond.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moyi.app.core.ApiErrorCode
import com.moyi.app.core.DataError
import com.moyi.app.core.Result
import com.moyi.app.designsystem.presentation.UiText
import com.moyi.app.domain.auth.AuthRepository
import com.moyi.app.domain.bond.BondSettingsChange
import com.moyi.app.domain.bond.BondSettingsRepository
import com.moyi.app.domain.bond.BondType
import com.moyi.app.domain.bond.TimeOfDay
import com.moyi.app.feature.bond.resources.Res
import com.moyi.app.feature.bond.resources.settings_error_conflict
import com.moyi.app.feature.bond.resources.settings_error_generic
import com.moyi.app.feature.bond.resources.settings_error_rate_limited
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
 */
class BondSettingsViewModel(
    private val bondId: String,
    private val repository: BondSettingsRepository,
    private val auth: AuthRepository,
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
        }
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

    /** Not a member any more (`404`), or the bond has ended (`409 BOND_ARCHIVED`). */
    private fun DataError.Network.isGone(): Boolean =
        (this as? DataError.Network.Api)?.problem?.status == 404 || code() == ApiErrorCode.BOND_ARCHIVED

    private fun DataError.Network.message(): UiText = UiText.Resource(
        if (code() == ApiErrorCode.RATE_LIMITED) Res.string.settings_error_rate_limited else Res.string.settings_error_generic,
    )

    private val tooLong = UiText.Resource(OnboardingRes.string.bond_name_error_too_long)

    companion object {
        /** `PatchBondRequest.name` maxLength, counted as the backend counts it (UTF-16 units). */
        const val MAX_NAME_LENGTH = 60
    }
}
