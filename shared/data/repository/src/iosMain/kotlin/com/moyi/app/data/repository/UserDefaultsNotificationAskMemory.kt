package com.moyi.app.data.repository

import com.moyi.app.domain.notification.NotificationAskMemory
import platform.Foundation.NSUserDefaults

/**
 * [NotificationAskMemory] in the app's standard NSUserDefaults: plain, per phone, kept
 * across sign-out (unlike the Keychain session and the database), and removed with the
 * app, so a reinstall asks again.
 */
class UserDefaultsNotificationAskMemory(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : NotificationAskMemory {
    override fun isDismissed(): Boolean = defaults.boolForKey(KEY_DISMISSED)

    override fun dismiss() {
        defaults.setBool(true, forKey = KEY_DISMISSED)
    }

    override fun reminderScreenShownOn(): String? = defaults.stringForKey(KEY_SCREEN_SHOWN_ON)

    override fun rememberReminderScreenShown(date: String) {
        defaults.setObject(date, forKey = KEY_SCREEN_SHOWN_ON)
    }

    private companion object {
        const val KEY_DISMISSED = "moyi.notificationAsk.dismissed"
        const val KEY_SCREEN_SHOWN_ON = "moyi.notificationAsk.reminderScreenShownOn"
    }
}
