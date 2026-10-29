package com.moyi.app.designsystem.platform

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberShareSheet(): ShareSheet {
    val context = LocalContext.current
    return remember(context) {
        ShareSheet { text ->
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            // A context that is not an Activity needs a new task to start one.
            val chooser = Intent.createChooser(send, null).apply {
                if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(chooser)
            } catch (_: ActivityNotFoundException) {
                // Nothing on the device can take text: nothing to open.
            }
        }
    }
}

@Composable
actual fun rememberTextClipboard(): TextClipboard {
    val context = LocalContext.current
    return remember(context) {
        TextClipboard { text ->
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            // The label is never shown to the person; Android 13+ shows its own copied confirmation.
            clipboard?.setPrimaryClip(ClipData.newPlainText("Moyi", text))
        }
    }
}
