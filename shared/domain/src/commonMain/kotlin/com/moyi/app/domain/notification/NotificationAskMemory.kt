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
 * Not a secret, and it holds nothing about anyone: one flag.
 */
interface NotificationAskMemory {
    /** True once "Not now" has been chosen on this phone. */
    fun isDismissed(): Boolean

    /** Remembers "Not now" on this phone, for good. */
    fun dismiss()
}
