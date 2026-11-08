package com.moyi.app.designsystem.platform

import androidx.compose.runtime.Composable

/**
 * Whether this phone lets Moyi show notifications.
 *
 * - [Granted]: it does (on iOS, provisional and ephemeral authorisation count).
 * - [NotAsked]: nobody has been asked yet, so the system prompt can still be shown.
 * - [Denied]: the person said no, or turned them off in the phone's settings. The app
 *   never asks again (it never nags); it says where to turn them back on.
 */
enum class NotificationPermission { Granted, NotAsked, Denied }

/**
 * The platform's notification permission: Android 13+'s `POST_NOTIFICATIONS` runtime
 * permission (granted at install below 13, where only the app's notification setting
 * can turn them off), and iOS's `UNUserNotificationCenter` authorisation.
 */
interface NotificationPermissionController {
    /** Where the phone stands now. Read again on every return to the screen: Settings may have changed it. */
    suspend fun status(): NotificationPermission

    /**
     * Shows the system prompt and answers what the person chose. Only when [status] is
     * [NotificationPermission.NotAsked]; otherwise it shows nothing and answers [status].
     */
    suspend fun request(): NotificationPermission

    /** Opens this app's notification settings in the phone's Settings. */
    fun openSettings()
}

@Composable
expect fun rememberNotificationPermission(): NotificationPermissionController
