package com.moyi.app.feature.bond.settings

import androidx.compose.runtime.Immutable
import com.moyi.app.designsystem.presentation.UiText
import com.moyi.app.domain.bond.BondSettings
import com.moyi.app.domain.bond.BondType

/** Whether the settings are on screen yet. Loading draws the bare `surface`: no spinner. */
enum class SettingsLoad { Loading, Failed, Ready }

/** What a save, a leave or an error belongs to: the row (or editor) that shows it. */
enum class SettingsField { Name, Type, RevealTime, RestDays, Leave }

/** An inline error, under the row or above the editor's action that caused it. Its input is kept. */
@Immutable
data class SettingsError(val field: SettingsField, val message: UiText)

/**
 * Bond settings, the root and its two editors (bond name, relationship type): one
 * state, because the editors change what the root shows, and one version (`ETag`)
 * that every change must carry.
 */
@Immutable
data class BondSettingsState(
    val load: SettingsLoad = SettingsLoad.Loading,
    val settings: BondSettings? = null,
    /** The change in flight; one at a time, so two saves never race for one version. */
    val saving: SettingsField? = null,
    val error: SettingsError? = null,
    /** The bond name editor's field, kept through a failed save and a conflict. */
    val nameDraft: String = "",
    val nameError: UiText? = null,
    val pickingRevealTime: Boolean = false,
    val confirmingLeave: Boolean = false,
    val signingOut: Boolean = false,
) {
    val leaving: Boolean get() = saving == SettingsField.Leave
}

sealed interface BondSettingsAction {
    data object OnRetryClick : BondSettingsAction
    data object OnNameClick : BondSettingsAction
    data class OnNameChange(val value: String) : BondSettingsAction
    data object OnNameSave : BondSettingsAction
    data object OnTypeClick : BondSettingsAction
    data class OnTypeSelect(val type: BondType) : BondSettingsAction
    data object OnRevealTimeClick : BondSettingsAction
    data class OnRevealTimePicked(val hour: Int, val minute: Int) : BondSettingsAction
    data object OnRevealTimeDismiss : BondSettingsAction
    data object OnRestDaysToggle : BondSettingsAction
    data object OnSignOutClick : BondSettingsAction
    data object OnLeaveClick : BondSettingsAction
    data object OnLeaveConfirm : BondSettingsAction
    data object OnLeaveDismiss : BondSettingsAction

    /** An editor closed without saving (back): its error goes with it. */
    data object OnEditorClosed : BondSettingsAction
}

sealed interface BondSettingsEvent {
    data object OpenName : BondSettingsEvent
    data object OpenType : BondSettingsEvent

    /** An editor's change saved: back to the root, which shows it. */
    data object Saved : BondSettingsEvent

    /**
     * The person is in this bond no more: they left, or a write found it ended
     * (`409 BOND_ARCHIVED`) or gone (`404`). The shell asks the signed-in start.
     */
    data object Ended : BondSettingsEvent
}
