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
    /** The anchor time zone's city. Defaults to the device's (states.md §1). */
    val timeZoneCity: String,
    /** The zone cannot be sent (not a region zone); shown on the time-zone field. */
    val timeZoneError: UiText? = null,
    val status: CreateBondStatus = CreateBondStatus.Idle,
    /** What [CreateBondStatus.Failed] says, above the button. */
    val error: UiText? = null,
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
