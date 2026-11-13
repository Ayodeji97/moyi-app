package com.moyi.app.data.repository

import android.content.Context
import com.moyi.app.domain.notification.NotificationAskMemory

/**
 * [NotificationAskMemory] in the same plain preferences file the notification permission
 * already keeps its "asked" flag in (`moyi_notification_permission`, designsystem's
 * NotificationPermission.android.kt): one phone-level file for one phone-level question.
 * Never the Keystore-encrypted token file or the database, which sign-out wipes. The app
 * turns backup off (AndroidManifest), so a reinstall starts without it and asks again.
 */
class PreferencesNotificationAskMemory(context: Context) : NotificationAskMemory {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun isDismissed(): Boolean = prefs.getBoolean(KEY_DISMISSED, false)

    override fun dismiss() {
        prefs.edit().putBoolean(KEY_DISMISSED, true).apply()
    }

    private companion object {
        /** The permission controller's file; its own key ("asked") is left alone. */
        const val PREFS = "moyi_notification_permission"
        const val KEY_DISMISSED = "ask_dismissed"
    }
}
