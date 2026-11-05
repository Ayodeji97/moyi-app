package com.moyi.app.designsystem.platform

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import java.util.WeakHashMap

/**
 * One hold per window: two protected cards on one screen share the flag, and the
 * flag is cleared only when the last leaves. Weak keys, so a destroyed activity's
 * window is not kept alive by this map.
 */
private val holds = WeakHashMap<Window, SharedHold>()

@Composable
internal actual fun ProtectFromCapture() {
    // A preview or a test host has no activity: nothing to protect, nothing to do.
    val window = LocalContext.current.findActivity()?.window ?: return
    DisposableEffect(window) {
        val hold = holds.getOrPut(window) {
            SharedHold(
                onFirst = { window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) },
                onLast = { window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) },
            )
        }
        hold.acquire()
        onDispose { hold.release() }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
