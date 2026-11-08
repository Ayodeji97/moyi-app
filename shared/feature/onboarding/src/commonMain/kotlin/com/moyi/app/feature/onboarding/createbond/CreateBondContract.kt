package com.moyi.app.feature.onboarding.createbond

import androidx.compose.runtime.Immutable
import com.moyi.app.designsystem.presentation.UiText
import com.moyi.app.feature.onboarding.common.RelationshipType

/** Onboarding 5 · create bond: IDLE (342:17), KEYBOARD UP (362:1196), failed · INPUT KEPT (394:1441). */
@Immutable
data class CreateBondState(
    val type: RelationshipType,
    val name: String = "",
    val nameError: UiText? = null,
    /**
     * The anchor time zone, an IANA id (`Africa/Lagos`): what `POST /bonds` sends.
     * Defaults to the device's (states.md §1); the picker changes it.
     */
    val timeZoneId: String,
    /** [timeZoneId]'s city, shown in the field and named by its helper line. */
    val timeZoneCity: String,
    /**
     * Said on the time-zone field: the zone cannot be sent (not a region zone),
     * or the zone list could not be loaded (opening the field again retries).
     */
    val timeZoneError: UiText? = null,
    /** The time-zone picker while it is open; null when it is closed. */
    val picker: TimeZonePickerState? = null,
    val status: CreateBondStatus = CreateBondStatus.Idle,
    /** What [CreateBondStatus.Failed] says, above the button. */
    val error: UiText? = null,
)

/** The time-zone picker: what has been typed in its search, and the zones that match it. */
@Immutable
data class TimeZonePickerState(
    val query: String = "",
    /** In order: the chosen zone, then the device's, then the rest by city. Filtered by [query]. */
    val zones: List<TimeZoneOption>,
)

/** One row of the picker. */
@Immutable
data class TimeZoneOption(
    /** The IANA id, `America/Argentina/Buenos_Aires`. */
    val id: String,
    /** `Buenos Aires`. */
    val city: String,
    /** What the id says before the city, `America, Argentina`, so two like cities can be told apart. */
    val region: String,
    /** Whether this is the zone the bond will be created with. */
    val selected: Boolean,
)

enum class CreateBondStatus {
    Idle,
    Sending,

    /** The request failed; the fields keep their values (394:1441). */
    Failed,
}

sealed interface CreateBondAction {
    data class OnNameChange(val value: String) : CreateBondAction
    data object OnTimeZoneClick : CreateBondAction
    data class OnTimeZoneQueryChange(val value: String) : CreateBondAction
    data class OnTimeZoneSelect(val id: String) : CreateBondAction

    /** Back from the picker, by its arrow or the system's: the choice is unchanged. */
    data object OnTimeZoneDismiss : CreateBondAction
    data object OnCreateClick : CreateBondAction
    data object OnBackClick : CreateBondAction
}

sealed interface CreateBondEvent {
    /** The bond exists; hand off its invite. */
    data class NavigateToInvite(val code: String, val link: String) : CreateBondEvent

    /** `403 EMAIL_NOT_VERIFIED`: the account cannot create a bond yet (FR-002). */
    data object NavigateToGate : CreateBondEvent
    data object NavigateBack : CreateBondEvent
}
