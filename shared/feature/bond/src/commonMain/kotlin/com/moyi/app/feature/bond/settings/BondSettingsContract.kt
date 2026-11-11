package com.moyi.app.feature.bond.settings

import androidx.compose.runtime.Immutable
import com.moyi.app.designsystem.presentation.UiText
import com.moyi.app.domain.bond.BondSettings
import com.moyi.app.domain.bond.BondType
import com.moyi.app.feature.onboarding.createbond.CreateBondViewModel
import kotlinx.datetime.LocalDate

/** Whether the settings are on screen yet. Loading draws the bare `surface`: no spinner. */
enum class SettingsLoad { Loading, Failed, Ready }

/** What a save, a leave or an error belongs to: the row (or editor) that shows it. */
enum class SettingsField { Name, Type, RevealTime, RestDays, Leave, TimeZone, Invite, Block, CloseBox }

/**
 * Which of the three time-zone screens (Figma 397:22, 397:67, 397:102) the pending
 * change calls for: none waiting is Propose; one the person made is Proposed (they can
 * only cancel it); one the other member made is Confirm (BR-6: never the proposer's).
 */
enum class TimeZoneStep { Propose, Proposed, Confirm }

/**
 * Which close-the-box screen (FR-028; Figma 399:38, 399:1525) the bond calls for:
 * nothing asked is Ask; this person asked is Asked (they can only withdraw it); the other
 * member asked is Agree (their own ask is the agreement); both asked is CoolingOff.
 */
enum class CloseBoxStep { Ask, Asked, Agree, CoolingOff }

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
    /** FR-029a on the leave confirm (Figma 398:53): off by default, so leaving keeps the entries. */
    val withdrawOnLeave: Boolean = false,
    /** FR-029a on the block confirm (612:1835): on by default (`26` §3.3); 661:1949 is it turned off. */
    val withdrawOnBlock: Boolean = true,
    val signingOut: Boolean = false,
    /**
     * Sign out would delete entries still waiting to send (the session wipe is total), and
     * a last try could not send them: the person is asked first.
     */
    val confirmingUnsentSignOut: Boolean = false,
    /** The zone chosen on Propose, an IANA id; starts as the bond's own. */
    val zoneDraft: String? = null,
    /** The picker's search text, and every region zone the platform knows, sorted by city. */
    val zoneQuery: String = "",
    val zones: List<String> = emptyList(),
    /** The platform gave no zones: `field.timezone.error` on the field, and reopening it retries. */
    val zonesFailed: Boolean = false,
    /** The invite code was copied: InviteCode's Copied state, until the invite changes. */
    val inviteCopied: Boolean = false,
    /** Which part of a time-zone screen is in flight: the primary (propose, agree) or the secondary (cancel, decline). */
    val zoneSecondaryInFlight: Boolean = false,
    /**
     * The first day the shared zone can change again, when that is still ahead of today in
     * the bond's zone; set when Propose opens, null when a change is allowed now. While set,
     * the screen says the day up front and the change cannot start.
     */
    val zoneChangeFrom: LocalDate? = null,
    /** The same for a close-the-box screen: true while its `DELETE` is the call in flight. */
    val closeBoxSecondaryInFlight: Boolean = false,
) {
    val leaving: Boolean get() = saving == SettingsField.Leave
    val blocking: Boolean get() = saving == SettingsField.Block

    val closeBoxStep: CloseBoxStep
        get() {
            val settings = settings ?: return CloseBoxStep.Ask
            return when {
                settings.coolingOff -> CloseBoxStep.CoolingOff
                settings.pendingDeletion?.requestedByMe == true -> CloseBoxStep.Asked
                settings.pendingDeletion != null -> CloseBoxStep.Agree
                else -> CloseBoxStep.Ask
            }
        }

    val timeZoneStep: TimeZoneStep
        get() = when (settings?.pendingTimezone?.proposedByMe) {
            null -> TimeZoneStep.Propose
            true -> TimeZoneStep.Proposed
            false -> TimeZoneStep.Confirm
        }

    /** The zones the picker shows: those whose city or id holds the search text, ignoring case. */
    val visibleZones: List<String>
        get() {
            val query = zoneQuery.trim()
            if (query.isEmpty()) return zones
            return zones.filter { cityOf(it).contains(query, ignoreCase = true) || it.contains(query, ignoreCase = true) }
        }
}

/** `Africa/Lagos` is Lagos; `America/Argentina/Buenos_Aires` is Buenos Aires: onboarding's rule, not a copy of it. */
fun cityOf(zoneId: String): String = CreateBondViewModel.cityOf(zoneId)

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
    data object OnNotificationsClick : BondSettingsAction
    data object OnAccountClick : BondSettingsAction

    /** Sign out anyway: the unsent entries are deleted with the session. */
    data object OnSignOutUnsentConfirm : BondSettingsAction

    /** Stay signed in: the entries keep waiting for a connection. */
    data object OnSignOutUnsentDismiss : BondSettingsAction
    data object OnLeaveClick : BondSettingsAction
    data object OnLeaveWithdrawToggle : BondSettingsAction
    data object OnLeaveConfirm : BondSettingsAction

    // Block and leave (FR-029, FR-029a)
    data object OnBlockClick : BondSettingsAction
    data object OnBlockWithdrawToggle : BondSettingsAction
    data object OnBlockConfirm : BondSettingsAction

    /** Report the other member (FR-100): the report flow, about the member. */
    data object OnReportClick : BondSettingsAction

    // Close the box (FR-028)
    data object OnCloseBoxClick : BondSettingsAction
    /** Ask, or agree to the other member's ask: the same `POST`. */
    data object OnCloseBoxConfirm : BondSettingsAction
    /** Withdraw your ask, decline theirs, or call off the cooling-off: the same `DELETE`. */
    data object OnCloseBoxWithdraw : BondSettingsAction

    /** An editor closed without saving (back): its error goes with it. */
    data object OnEditorClosed : BondSettingsAction

    // The shared time zone (FR-027)
    data object OnTimeZoneClick : BondSettingsAction
    data object OnZoneFieldClick : BondSettingsAction
    data class OnZoneQueryChange(val value: String) : BondSettingsAction
    data class OnZonePicked(val zoneId: String) : BondSettingsAction
    data object OnProposeClick : BondSettingsAction
    /** Proposed: the proposer withdraws it. Confirm: the other member declines it. Both are the `DELETE`. */
    data object OnWithdrawProposalClick : BondSettingsAction
    data object OnAgreeClick : BondSettingsAction

    // The invite (states.md §2)
    data object OnInviteClick : BondSettingsAction
    data object OnShareInviteClick : BondSettingsAction
    data object OnCopyInviteClick : BondSettingsAction
    data object OnCancelInviteClick : BondSettingsAction
    data object OnCreateInviteClick : BondSettingsAction
}

sealed interface BondSettingsEvent {
    data object OpenName : BondSettingsEvent
    data object OpenType : BondSettingsEvent
    data object OpenTimeZone : BondSettingsEvent
    data object OpenZonePicker : BondSettingsEvent

    /** A zone was chosen in the picker: back to Propose, which shows it. */
    data object ZonePicked : BondSettingsEvent
    data object OpenInvite : BondSettingsEvent
    data object OpenLeave : BondSettingsEvent
    data object OpenBlock : BondSettingsEvent
    data object OpenCloseBox : BondSettingsEvent

    /** The report flow about the other member of [bondId], named [partnerName]. */
    data class OpenReport(val bondId: String, val partnerName: String) : BondSettingsEvent
    data object OpenNotifications : BondSettingsEvent

    /** Account (export, delete your account): needs nothing the bond holds, so it opens even when the bond did not load. */
    data object OpenAccount : BondSettingsEvent

    /**
     * Open the OS share sheet with the invite. Fire and forget: the sheet closing is never
     * proof the invite was sent (states.md §8), so nothing waits on it or moves on.
     */
    data class ShareInvite(val link: String, val code: String) : BondSettingsEvent
    data class CopyInviteCode(val code: String) : BondSettingsEvent

    /** An editor's change saved: back to the root, which shows it. */
    data object Saved : BondSettingsEvent

    /**
     * The person is in this bond no more: they left, or a write found it ended
     * (`409 BOND_ARCHIVED`) or gone (`404`). The shell asks the signed-in start.
     */
    data object Ended : BondSettingsEvent
}
