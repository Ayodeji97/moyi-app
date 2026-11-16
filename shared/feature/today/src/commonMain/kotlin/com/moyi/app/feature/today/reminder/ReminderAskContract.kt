package com.moyi.app.feature.today.reminder

import androidx.compose.runtime.Immutable
import com.moyi.app.designsystem.platform.NotificationPermission
import com.moyi.app.domain.bond.MemberSettings
import com.moyi.app.domain.bond.TimeOfDay

/** Whether the reminder time the server holds for this member has been read. */
enum class ReminderTimeLoad {
    /** Not yet: the Field shows no time and does not open, so no time is guessed. */
    Loading,
    Ready,

    /** It could not be read: no Field, a line says where to choose it, and nothing is saved. */
    Failed,
}

/** Where the screen is in its one job. */
enum class ReminderAskPhase {
    /** Turn on reminders and Not now are offered. */
    Offering,

    /** A changed time is being saved: the PrimaryAction says Saving (no spinner, states.md). */
    Saving,

    /** The phone's own prompt is up: nothing asks again until it answers. */
    Prompting,

    /**
     * The phone answered, and something is left to say: it said no ([ReminderAskState.denied]),
     * or the time did not save ([ReminderAskState.saveFailed]). The screen says so and offers
     * the way on to Today.
     */
    Answered,
}

/**
 * The reminder screen (owner rulings 2026-11-15): once per phone, at the first arrival on
 * Today. [member] is the caller's own settings in the bond (`/members/me/settings`), read
 * for the reminder time it holds; [chosen] is a time picked here, not saved yet.
 */
@Immutable
data class ReminderAskState(
    val load: ReminderTimeLoad = ReminderTimeLoad.Loading,
    val member: MemberSettings? = null,
    val chosen: TimeOfDay? = null,
    /** The time picker is open. */
    val picking: Boolean = false,
    val phase: ReminderAskPhase = ReminderAskPhase.Offering,
    /** The phone said no to the prompt: the line says how to turn notifications on. */
    val denied: Boolean = false,
    /** A changed time did not save: the line says reminders stay at the time the server holds. */
    val saveFailed: Boolean = false,
) {
    /** The time shown: the one picked here, else the server's, else none (never a guess). */
    val time: TimeOfDay? get() = chosen ?: member?.reminderTime

    /** A time was picked that the server does not hold yet: Turn on reminders saves it first. */
    val changed: Boolean get() = member != null && chosen != null && chosen != member.reminderTime

    /** The Field opens the picker only once the server's time is known, and only before Turn on. */
    val canPick: Boolean get() = member != null && phase == ReminderAskPhase.Offering
}

sealed interface ReminderAskAction {
    data object OnTimeClick : ReminderAskAction
    data class OnTimePicked(val hour: Int, val minute: Int) : ReminderAskAction
    data object OnTimeDismiss : ReminderAskAction
    data object OnTurnOnClick : ReminderAskAction

    /** The phone's answer to the prompt the screen showed for [ReminderAskEvent.AskPermission]. */
    data class OnPermissionAnswered(val permission: NotificationPermission) : ReminderAskAction

    /** The phone's permission, read on every resume: back from the phone's settings, it may be on now. */
    data class OnPermissionChecked(val permission: NotificationPermission) : ReminderAskAction
    data object OnNotNowClick : ReminderAskAction
    data object OnOpenPhoneSettingsClick : ReminderAskAction
    data object OnContinueClick : ReminderAskAction
}

sealed interface ReminderAskEvent {
    /** Show the phone's own prompt, then answer with [ReminderAskAction.OnPermissionAnswered]. */
    data object AskPermission : ReminderAskEvent

    /** This app's notification settings in the phone's Settings. */
    data object OpenPhoneSettings : ReminderAskEvent

    /** On to Today. */
    data object Done : ReminderAskEvent
}
