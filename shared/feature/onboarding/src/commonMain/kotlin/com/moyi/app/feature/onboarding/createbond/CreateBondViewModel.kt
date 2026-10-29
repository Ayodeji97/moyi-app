package com.moyi.app.feature.onboarding.createbond

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moyi.app.core.ApiErrorCode
import com.moyi.app.core.DataError
import com.moyi.app.core.Result
import com.moyi.app.designsystem.presentation.UiText
import com.moyi.app.domain.bond.Bond
import com.moyi.app.domain.bond.BondRepository
import com.moyi.app.domain.bond.Invite
import com.moyi.app.feature.onboarding.common.RelationshipType
import com.moyi.app.feature.onboarding.common.code
import com.moyi.app.feature.onboarding.common.formMessage
import com.moyi.app.feature.onboarding.common.isUnverified
import com.moyi.app.feature.onboarding.resources.Res
import com.moyi.app.feature.onboarding.resources.bond_create_error_limit
import com.moyi.app.feature.onboarding.resources.bond_name_error_blank
import com.moyi.app.feature.onboarding.resources.bond_name_error_too_long
import com.moyi.app.feature.onboarding.resources.field_timezone_error_unsupported
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Onboarding 5. Creates the bond with `POST /bonds` and hands its invite to
 * the handoff screen. The name is checked here first (non-blank, at most 60),
 * so a `422` on it means the rules drifted; it is still shown on the field.
 *
 * Every failure keeps the name and the time zone (394:1441). The time zone is
 * the device's ([deviceTimeZone], an IANA id); the picker is not built.
 */
class CreateBondViewModel(
    type: RelationshipType,
    private val bonds: BondRepository,
    private val deviceTimeZone: String,
) : ViewModel() {
    private val _state = MutableStateFlow(
        CreateBondState(type = type, timeZoneCity = cityOf(deviceTimeZone)),
    )
    val state: StateFlow<CreateBondState> = _state.asStateFlow()

    private val _events = Channel<CreateBondEvent>()
    val events = _events.receiveAsFlow()

    fun onAction(action: CreateBondAction) {
        when (action) {
            // Over the limit is said while typing, so the person sees it before pressing Create.
            is CreateBondAction.OnNameChange -> _state.update {
                it.copy(name = action.value, nameError = if (action.value.length > MAX_NAME_LENGTH) tooLong else null)
            }
            // TODO(logic): open a time-zone picker (the platform's zone list; no endpoint). A load failure
            //  shows `field.timezone.error` in the field, and reopening it retries (strings-proposed.md).
            CreateBondAction.OnTimeZoneClick -> Unit
            CreateBondAction.OnCreateClick -> create()
            CreateBondAction.OnBackClick -> viewModelScope.launch { _events.send(CreateBondEvent.NavigateBack) }
        }
    }

    private fun create() {
        val current = _state.value
        if (current.status == CreateBondStatus.Sending) return
        if (current.name.isBlank()) {
            _state.update { it.copy(nameError = UiText.Resource(Res.string.bond_name_error_blank)) }
            return
        }
        if (current.name.length > MAX_NAME_LENGTH) {
            _state.update { it.copy(nameError = tooLong) }
            return
        }
        if (!isRegionZone(deviceTimeZone)) {
            _state.update { it.copy(timeZoneError = unsupportedZone) }
            return
        }
        _state.update { it.copy(status = CreateBondStatus.Sending, error = null) }
        val existing = createdBond
        viewModelScope.launch {
            if (existing != null) return@launch handOff(existing)
            when (val created = bonds.createBond(current.name, current.type.bondType, deviceTimeZone)) {
                is Result.Success -> handOff(created.data)
                is Result.Failure -> fail(created.error)
            }
        }
    }

    /**
     * The `201` carries the bond's first invite. If it ever came without one,
     * one is asked for; if that fails too, the bond exists anyway, so the
     * person is told to try again and a retry asks only for the invite.
     */
    private suspend fun handOff(bond: Bond) {
        createdBond = bond
        val invite: Invite = bond.invite ?: when (val asked = bonds.createInvite(bond.id)) {
            is Result.Success -> asked.data
            is Result.Failure -> return fail(asked.error)
        }
        _events.send(CreateBondEvent.NavigateToInvite(invite.code, invite.link))
    }

    /** Set once the bond exists, so Create after a failed invite request does not make a second bond. */
    private var createdBond: Bond? = null

    private suspend fun fail(error: DataError) {
        when {
            error.isUnverified() -> {
                _state.update { it.copy(status = CreateBondStatus.Idle) }
                _events.send(CreateBondEvent.NavigateToGate)
            }
            error.code() == ApiErrorCode.VALIDATION_FAILED && error.violates("name") -> _state.update {
                val message = if (it.name.length > MAX_NAME_LENGTH) tooLong else UiText.Resource(Res.string.bond_name_error_blank)
                it.copy(status = CreateBondStatus.Idle, nameError = message)
            }
            error.code() == ApiErrorCode.VALIDATION_FAILED && (error.violates("anchorTimezone") || error.violates("reminderTimezone")) ->
                _state.update { it.copy(status = CreateBondStatus.Idle, timeZoneError = unsupportedZone) }
            error.code() == ApiErrorCode.BOND_LIMIT_REACHED ->
                _state.update { it.copy(status = CreateBondStatus.Failed, error = UiText.Resource(Res.string.bond_create_error_limit)) }
            else -> _state.update { it.copy(status = CreateBondStatus.Failed, error = error.formMessage()) }
        }
    }

    private fun DataError.violates(field: String): Boolean =
        (this as? DataError.Network.Api)?.problem?.fieldErrors?.any { it.field == field } == true

    private val tooLong = UiText.Resource(Res.string.bond_name_error_too_long)
    private val unsupportedZone = UiText.Resource(Res.string.field_timezone_error_unsupported)

    companion object {
        /**
         * `CreateBondRequest.name` maxLength. Counted as String.length (UTF-16 units), as the
         * backend's Bean Validation `@Size` counts it, so the two agree on every name.
         */
        const val MAX_NAME_LENGTH = 60

        /**
         * The city a person recognises in an IANA id: `Africa/Lagos` is Lagos,
         * `America/Argentina/Buenos_Aires` is Buenos Aires. Not localised yet
         * (the platform's own zone names are owed with the picker).
         */
        /**
         * The backend takes only a region zone (`Africa/Lagos`), never `UTC`, `GMT`
         * or `Etc/…`, which some devices and emulators report. Checked before
         * sending, so the person is told on the field instead of seeing a generic
         * error they can retry forever. The backend stays the judge: its `422` on
         * the zone lands on the same field.
         */
        fun isRegionZone(zoneId: String): Boolean =
            '/' in zoneId && !zoneId.startsWith("Etc/") && !zoneId.startsWith("SystemV/")

        fun cityOf(zoneId: String): String = zoneId.substringAfterLast('/').replace('_', ' ')
    }
}
