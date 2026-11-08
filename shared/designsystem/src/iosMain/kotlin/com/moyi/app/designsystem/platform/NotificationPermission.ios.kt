package com.moyi.app.designsystem.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatus
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusEphemeral
import platform.UserNotifications.UNAuthorizationStatusNotDetermined
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume

/**
 * iOS: `UNUserNotificationCenter` authorisation. iOS shows its prompt once, while the
 * status is not determined; after that only Settings can change it, which is why the
 * status is read again whenever the screen comes back.
 *
 * Alerts and sounds only, no badge: Moyi never invents a reason to be opened (doc 14 §2.4),
 * and a count on the icon is one.
 */
@Composable
actual fun rememberNotificationPermission(): NotificationPermissionController = remember { IosNotificationPermission() }

private class IosNotificationPermission : NotificationPermissionController {
    private val center get() = UNUserNotificationCenter.currentNotificationCenter()

    override suspend fun status(): NotificationPermission = suspendCancellableCoroutine { continuation ->
        center.getNotificationSettingsWithCompletionHandler { settings ->
            continuation.resume(settings?.authorizationStatus.toPermission())
        }
    }

    override suspend fun request(): NotificationPermission {
        if (status() != NotificationPermission.NotAsked) return status()
        suspendCancellableCoroutine { continuation ->
            center.requestAuthorizationWithOptions(UNAuthorizationOptionAlert or UNAuthorizationOptionSound) { _, _ ->
                continuation.resume(Unit)
            }
        }
        return status()
    }

    override fun openSettings() {
        val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return
        UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any>(), completionHandler = null)
    }
}

private fun UNAuthorizationStatus?.toPermission(): NotificationPermission = when (this) {
    UNAuthorizationStatusAuthorized, UNAuthorizationStatusProvisional, UNAuthorizationStatusEphemeral -> NotificationPermission.Granted
    UNAuthorizationStatusNotDetermined, null -> NotificationPermission.NotAsked
    else -> NotificationPermission.Denied
}
