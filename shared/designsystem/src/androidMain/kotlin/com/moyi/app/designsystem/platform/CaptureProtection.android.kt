package com.moyi.app.designsystem.platform

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/**
 * One hold per window: two protected cards on one screen share the flag, and the
 * flag is cleared only when the last leaves. The entry goes with its last holder,
 * so a destroyed or recreated activity's window is not retained here.
 */
private val holds = KeyedHolds<Window>(
    onFirst = { it.addFlags(WindowManager.LayoutParams.FLAG_SECURE) },
    onLast = { it.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) },
)

@Composable
internal actual fun ProtectFromCapture() {
    // A preview or a test host has no activity: nothing to protect, nothing to do.
    val window = LocalContext.current.findActivity()?.window ?: return
    DisposableEffect(window) {
        holds.acquire(window)
        onDispose { holds.release(window) }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
