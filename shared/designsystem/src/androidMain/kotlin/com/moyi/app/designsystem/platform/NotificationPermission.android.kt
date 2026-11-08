package com.moyi.app.designsystem.platform

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CompletableDeferred

/**
 * Android: `POST_NOTIFICATIONS` is a runtime permission from Android 13 (API 33). Below
 * 13 it is granted at install, and the only switch is the app's notification setting.
 *
 * Android cannot tell "never asked" from "said no for good": both answer not granted
 * with no rationale. So the first request is remembered (a plain flag in the app's
 * preferences: not a secret), and a phone that was asked and is not granted is
 * [NotificationPermission.Denied]. That also means Moyi asks once, never twice, though
 * Android would allow a second ask.
 */
@Composable
actual fun rememberNotificationPermission(): NotificationPermissionController {
    val context = LocalContext.current
    val answer = remember { AnswerSlot() }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        answer.pending?.complete(granted)
    }
    return remember(context, launcher) { AndroidNotificationPermission(context.applicationContext, launcher, answer) }
}

/** The request in flight, answered by the launcher's callback. */
private class AnswerSlot {
    var pending: CompletableDeferred<Boolean>? = null
}

private class AndroidNotificationPermission(
    private val context: Context,
    private val launcher: ActivityResultLauncher<String>,
    private val answer: AnswerSlot,
) : NotificationPermissionController {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override suspend fun status(): NotificationPermission = when {
        // On 13+ this is the runtime permission; below, the app's notification switch.
        NotificationManagerCompat.from(context).areNotificationsEnabled() -> NotificationPermission.Granted
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU -> NotificationPermission.Denied
        prefs.getBoolean(KEY_ASKED, false) -> NotificationPermission.Denied
        else -> NotificationPermission.NotAsked
    }

    override suspend fun request(): NotificationPermission {
        val now = status()
        if (now != NotificationPermission.NotAsked || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return now
        val pending = CompletableDeferred<Boolean>()
        answer.pending = pending
        prefs.edit().putBoolean(KEY_ASKED, true).apply()
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        pending.await()
        answer.pending = null
        return status()
    }

    override fun openSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        }
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            // No settings screen to open: the line on screen still says where to go.
        }
    }

    private companion object {
        const val PREFS = "moyi_notification_permission"
        const val KEY_ASKED = "asked"
    }
}
