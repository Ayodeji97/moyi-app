package com.moyi.app.domain.notification

/**
 * Whether this **phone** has been told "Not now" to Today's note asking for notifications
 * (owner ruling 2026-11-13). Once it has, the note never shows again on this phone.
 *
 * Per phone, not per account, on purpose: the notification permission it leads to is the
 * phone's, so a second account on the same phone is not asked again, and signing out keeps
 * the answer. That is why it lives in the platform's plain preferences (Android's
 * SharedPreferences, iOS's NSUserDefaults) and never in the token store or the local
 * database, which every sign-out wipes. Removing the app removes it, so a reinstall asks again.
 *
 * It also remembers the day the full reminder screen was shown on this phone (owner ruling
 * 2026-11-15): that screen shows once per phone, at the first arrival on Today, and after it
 * the small note waits until the next calendar day.
 *
 * Not a secret, and it holds nothing about anyone: one flag and one date.
 */
interface NotificationAskMemory {
    /** True once "Not now" has been chosen on this phone. */
    fun isDismissed(): Boolean

    /** Remembers "Not now" on this phone, for good. */
    fun dismiss()

    /**
     * The phone's local date the reminder screen was shown on, as ISO `YYYY-MM-DD` (a string
     * because the domain carries no date library), or null when it never has been on this phone.
     */
    fun reminderScreenShownOn(): String?

    /** Remembers that the reminder screen was shown on [date] (ISO `YYYY-MM-DD`), so it never shows again. */
    fun rememberReminderScreenShown(date: String)
}
