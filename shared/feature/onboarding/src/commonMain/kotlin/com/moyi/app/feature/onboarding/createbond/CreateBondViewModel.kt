package com.moyi.app.feature.onboarding.createbond

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moyi.app.designsystem.presentation.UiText
import com.moyi.app.feature.onboarding.common.FakeOnboardingData
import com.moyi.app.feature.onboarding.common.RelationshipType
import com.moyi.app.feature.onboarding.resources.Res
import com.moyi.app.feature.onboarding.resources.bond_name_error_blank
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** FAKE: creating always succeeds at once and hands over [FakeOnboardingData.INVITE_CODE]. */
class CreateBondViewModel(type: RelationshipType) : ViewModel() {
    private val _state = MutableStateFlow(
        CreateBondState(
            type = type,
            // TODO(logic): the device's IANA time zone via expect/actual (TimeZone.currentSystemDefault()),
            //  shown as its city; sent as `anchorTimezone` (and `reminderTimezone`) on `POST /api/v1/bonds`.
            timeZoneCity = FakeOnboardingData.TIME_ZONE_CITY,
        ),
    )
    val state: StateFlow<CreateBondState> = _state.asStateFlow()

    private val _events = Channel<CreateBondEvent>()
    val events = _events.receiveAsFlow()

    fun onAction(action: CreateBondAction) {
        when (action) {
            is CreateBondAction.OnNameChange -> _state.update { it.copy(name = action.value, nameError = null) }
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
        // TODO(logic): `POST /api/v1/bonds` with { name, type (COUPLE/FRIENDS/FAMILY), anchorTimezone,
        //  reminderTimezone } (no revealTimeLocal: it lives in Bond settings, states.md §1). Show Sending
        //  while in flight; on failure set Failed and keep the fields (394:1441). On 201 the invite is
        //  BondResponse.invite (or `POST /api/v1/bonds/{bondId}/invites` if it is absent).
        viewModelScope.launch { _events.send(CreateBondEvent.NavigateToInvite(FakeOnboardingData.INVITE_CODE)) }
    }
}
