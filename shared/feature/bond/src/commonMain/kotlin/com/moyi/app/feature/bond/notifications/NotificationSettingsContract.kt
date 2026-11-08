package com.moyi.app.feature.bond.notifications

import androidx.compose.runtime.Immutable
import com.moyi.app.designsystem.platform.NotificationPermission
import com.moyi.app.designsystem.presentation.UiText
import com.moyi.app.domain.bond.MemberSettings
import com.moyi.app.domain.bond.TimeOfDay
import com.moyi.app.domain.notification.NotificationCategory
import com.moyi.app.domain.notification.NotificationPreferences
import com.moyi.app.feature.bond.settings.SettingsLoad

/** What a save or an error belongs to: the row that shows it. */
sealed interface NotificationRow {
    data class Category(val category: NotificationCategory) : NotificationRow

    data object ReminderTime : NotificationRow

    /** The quiet hours toggle and both of its times: one change at a time, shown on the toggle. */
    data object QuietHours : NotificationRow
}

/** Which time the picker is choosing. */
enum class TimeTarget { Reminder, QuietStart, QuietEnd }

@Immutable
data class NotificationRowError(val row: NotificationRow, val message: UiText)

/**
 * Notifications for one bond: the six kinds (`/members/me/notifications`), and the
 * reminder time and quiet hours (`/members/me/settings`), both the caller's own and
 * never the other member's (SKILL.md §3). [permission] is this phone's answer, null
 * until it has been read.
 */
@Immutable
data class NotificationSettingsState(
    val load: SettingsLoad = SettingsLoad.Loading,
    val preferences: NotificationPreferences? = null,
    val member: MemberSettings? = null,
    val permission: NotificationPermission? = null,
    /** The system prompt is up: nothing asks again until it answers. */
    val askingPermission: Boolean = false,
    /** The change in flight; one at a time. Its row says Saving (no spinner, states.md). */
    val saving: NotificationRow? = null,
    val error: NotificationRowError? = null,
    val picking: TimeTarget? = null,
) {
    val quietHoursOn: Boolean get() = member?.quietHoursStart != null && member.quietHoursEnd != null

    /** The time the open picker starts at. */
    val pickerInitial: TimeOfDay?
        get() = when (picking) {
            TimeTarget.Reminder -> member?.reminderTime
            TimeTarget.QuietStart -> member?.quietHoursStart
            TimeTarget.QuietEnd -> member?.quietHoursEnd
            null -> null
        }
}

sealed interface NotificationSettingsAction {
    data object OnRetryClick : NotificationSettingsAction
    data class OnCategoryToggle(val category: NotificationCategory) : NotificationSettingsAction

    /** This phone has never been asked: the person chose to be asked now. */
    data object OnAllowClick : NotificationSettingsAction
    data object OnOpenPhoneSettingsClick : NotificationSettingsAction

    /** The phone's answer: read when the screen shows (and comes back), and after a prompt. */
    data class OnPermissionChecked(val permission: NotificationPermission) : NotificationSettingsAction
    data object OnReminderTimeClick : NotificationSettingsAction
    data object OnQuietHoursToggle : NotificationSettingsAction
    data object OnQuietStartClick : NotificationSettingsAction
    data object OnQuietEndClick : NotificationSettingsAction
    data class OnTimePicked(val hour: Int, val minute: Int) : NotificationSettingsAction
    data object OnTimeDismiss : NotificationSettingsAction
}

sealed interface NotificationSettingsEvent {
    /** Show the system prompt, then answer with [NotificationSettingsAction.OnPermissionChecked]. */
    data object AskPermission : NotificationSettingsEvent
    data object OpenPhoneSettings : NotificationSettingsEvent

    /** Not in this bond any more (`404`, `409 BOND_ARCHIVED`): the shell asks the signed-in start. */
    data object Ended : NotificationSettingsEvent
}

/**
 * When the app asks for the notification permission (doc 13 §7: after the person has
 * seen the value, never on first launch; never twice). The most conservative reading:
 * only on this screen, and only when the person acts for it.
 *
 * - They turn a kind of notification on while the phone has never been asked: the
 *   prompt follows the choice it makes useful.
 * - They tap Allow notifications ([NotificationSettingsAction.OnAllowClick]), which is
 *   shown only while the phone has never been asked.
 *
 * Never at launch, in onboarding, or on turning something off; never while a prompt is
 * up; never once the phone has answered (granted, or denied: that is said, not asked).
 */
object NotificationPermissionPolicy {
    fun shouldAskOnToggle(permission: NotificationPermission?, turningOn: Boolean, asking: Boolean): Boolean =
        turningOn && permission == NotificationPermission.NotAsked && !asking

    fun offersAllow(permission: NotificationPermission?): Boolean = permission == NotificationPermission.NotAsked

    fun saysItIsOff(permission: NotificationPermission?): Boolean = permission == NotificationPermission.Denied
}
