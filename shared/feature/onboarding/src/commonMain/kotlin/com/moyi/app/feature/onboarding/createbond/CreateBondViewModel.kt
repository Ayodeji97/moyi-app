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
import com.moyi.app.feature.onboarding.resources.field_timezone_error
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
 * Every failure keeps the name and the time zone (394:1441). The time zone
 * starts as the device's ([deviceTimeZone], an IANA id); the picker lists the
 * platform's zones ([availableZoneIds], no endpoint) and the one chosen there
 * is what is sent.
 */
class CreateBondViewModel(
    type: RelationshipType,
    private val bonds: BondRepository,
    private val deviceTimeZone: String,
    /** The platform's IANA ids. Read each time the picker opens; if it throws, the field says so. */
    private val availableZoneIds: () -> Set<String>,
) : ViewModel() {
    private val _state = MutableStateFlow(
        CreateBondState(type = type, timeZoneId = deviceTimeZone, timeZoneCity = cityOf(deviceTimeZone)),
    )

    /** Every zone the picker offers, in its order, unfiltered. Built when the picker opens. */
    private var allZones: List<TimeZoneOption> = emptyList()
    val state: StateFlow<CreateBondState> = _state.asStateFlow()

    private val _events = Channel<CreateBondEvent>()
    val events = _events.receiveAsFlow()

    fun onAction(action: CreateBondAction) {
        when (action) {
            // Over the limit is said while typing, so the person sees it before pressing Create.
            is CreateBondAction.OnNameChange -> _state.update {
                it.copy(name = action.value, nameError = if (action.value.length > MAX_NAME_LENGTH) tooLong else null)
            }
            CreateBondAction.OnTimeZoneClick -> openPicker()
            is CreateBondAction.OnTimeZoneQueryChange -> _state.update { current ->
                val picker = current.picker ?: return@update current
                current.copy(picker = picker.copy(query = action.value, zones = allZones.matching(action.value)))
            }
            is CreateBondAction.OnTimeZoneSelect -> select(action.id)
            CreateBondAction.OnTimeZoneDismiss -> _state.update { it.copy(picker = null) }
            CreateBondAction.OnCreateClick -> create()
            CreateBondAction.OnBackClick -> viewModelScope.launch { _events.send(CreateBondEvent.NavigateBack) }
        }
    }

    /**
     * Opens the picker on the platform's zones. A load failure says so on the
     * field (`field.timezone.error`) and leaves the picker closed; opening the
     * field again is the retry (strings-proposed.md, decided 2026-09-03).
     */
    private fun openPicker() {
        val current = _state.value
        if (current.status == CreateBondStatus.Sending) return
        val ids = runCatching(availableZoneIds).getOrElse {
            _state.update { it.copy(timeZoneError = loadFailed) }
            return
        }
        allZones = pickerZones(ids, chosen = current.timeZoneId, device = deviceTimeZone)
        _state.update {
            it.copy(
                picker = TimeZonePickerState(zones = allZones),
                // A retry that worked ends the load failure; an unsupported zone stays until one is chosen.
                timeZoneError = it.timeZoneError.takeUnless { error -> error == loadFailed },
            )
        }
    }

    private fun select(id: String) {
        // Only an offered zone can be chosen, so nothing the backend refuses gets in this way.
        if (allZones.none { it.id == id }) return
        _state.update { it.copy(timeZoneId = id, timeZoneCity = cityOf(id), timeZoneError = null, picker = null) }
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
        if (!isRegionZone(current.timeZoneId)) {
            _state.update { it.copy(timeZoneError = unsupportedZone) }
            return
        }
        _state.update { it.copy(status = CreateBondStatus.Sending, error = null) }
        val existing = createdBond
        viewModelScope.launch {
            if (existing != null) return@launch handOff(existing)
            when (val created = bonds.createBond(current.name, current.type.bondType, current.timeZoneId)) {
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
        _events.send(CreateBondEvent.NavigateToInvite(invite.code, invite.link, bond.id, bond.name))
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
    private val loadFailed = UiText.Resource(Res.string.field_timezone_error)

    companion object {
        /**
         * `CreateBondRequest.name` maxLength. Counted as String.length (UTF-16 units), as the
         * backend's Bean Validation `@Size` counts it, so the two agree on every name.
         */
        const val MAX_NAME_LENGTH = 60

        /**
         * The backend takes only a region zone (`Africa/Lagos`), never `UTC`, `GMT`
         * or `Etc/…`, which some devices and emulators report. Checked before
         * sending, so the person is told on the field instead of seeing a generic
         * error they can retry forever. The backend stays the judge: its `422` on
         * the zone lands on the same field.
         */
        fun isRegionZone(zoneId: String): Boolean =
            '/' in zoneId && !zoneId.startsWith("Etc/") && !zoneId.startsWith("SystemV/")

        /**
         * The city a person recognises in an IANA id: `Africa/Lagos` is Lagos,
         * `America/Argentina/Buenos_Aires` is Buenos Aires. Not localised yet
         * (the platform's own localised zone names are not used; see the PR).
         */
        fun cityOf(zoneId: String): String = zoneId.substringAfterLast('/').replace('_', ' ')

        /** What comes before the city, for telling like cities apart: `America/Argentina/Buenos_Aires` is `America, Argentina`. */
        fun regionOf(zoneId: String): String =
            zoneId.substringBeforeLast('/', missingDelimiterValue = "").replace("/", ", ").replace('_', ' ')

        /**
         * The IANA areas whose ids name a place. The platform lists legacy links too
         * (`US/Eastern`, `Canada/Pacific`, `Brazil/East`): the backend accepts them, but
         * their last part is not a city, so the picker leaves them out. The device's
         * own zone and the chosen one are offered whatever their area.
         */
        private val PLACE_AREAS = setOf(
            "Africa", "America", "Antarctica", "Arctic", "Asia",
            "Atlantic", "Australia", "Europe", "Indian", "Pacific",
        )

        /**
         * The picker's rows, in order: the chosen zone (marked), then the device's if
         * it differs, then every other place zone by city. Only region zones: a zone
         * the backend refuses is never offered.
         */
        fun pickerZones(ids: Set<String>, chosen: String, device: String): List<TimeZoneOption> {
            val first = listOf(chosen, device).distinct().filter(::isRegionZone)
            val rest = ids.asSequence()
                .filter { isRegionZone(it) && it.substringBefore('/') in PLACE_AREAS && it !in first }
                .sortedWith(compareBy<String> { cityOf(it).lowercase() }.thenBy { it })
                .toList()
            return (first + rest).map { id ->
                TimeZoneOption(id = id, city = cityOf(id), region = regionOf(id), selected = id == chosen)
            }
        }

        /**
         * Searched by city, case ignored, anywhere in the name ("aires" finds Buenos
         * Aires); the region is searched too, so "Europe" lists Europe. Order is kept.
         */
        fun List<TimeZoneOption>.matching(query: String): List<TimeZoneOption> {
            val q = query.trim()
            if (q.isEmpty()) return this
            return filter { it.city.contains(q, ignoreCase = true) || it.region.contains(q, ignoreCase = true) }
        }
    }
}
