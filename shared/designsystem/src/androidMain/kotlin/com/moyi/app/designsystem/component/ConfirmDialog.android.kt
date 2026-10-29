package com.moyi.app.designsystem.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.moyi.app.designsystem.MoyiTheme

/**
 * Material 3's `AlertDialog`, in Rosewood: `MoyiTheme` maps Material's colour,
 * type and shape roles to the tokens, so the container is `surfaceRaised`, the
 * title `title`, the line `body` in `inkMuted`, the buttons `action`. Dismiss
 * first, confirm last, as Material orders them.
 */
@Composable
actual fun ConfirmDialog(
    title: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    message: String?,
    destructive: Boolean,
) {
    val colors = MoyiTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = if (destructive) colors.danger else colors.ink)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(dismissLabel, color = colors.ink) }
        },
        title = { Text(title) },
        text = message?.let { { Text(it) } },
        containerColor = colors.surfaceRaised,
        titleContentColor = colors.ink,
        textContentColor = colors.inkMuted,
    )
}
