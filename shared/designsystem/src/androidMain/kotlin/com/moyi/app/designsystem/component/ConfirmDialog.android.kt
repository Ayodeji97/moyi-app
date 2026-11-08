package com.moyi.app.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.moyi.app.designsystem.MoyiTheme

/**
 * Material 3's `AlertDialog`, in Rosewood: `MoyiTheme` maps Material's colour,
 * type and shape roles to the tokens, so the container is `surfaceRaised`, the
 * title `title`, the line `body` in `inkMuted`, the buttons `action`. Dismiss
 * first, confirm last, as Material orders them. With a third answer
 * ([alternativeLabel], plain `ink` like dismiss) the three stack, end-aligned: the
 * third, confirm, then dismiss, the same order as the iOS alert.
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
    alternativeLabel: String?,
    onAlternative: () -> Unit,
) {
    val colors = MoyiTheme.colors
    val confirm = @Composable {
        TextButton(onClick = onConfirm) {
            Text(confirmLabel, color = if (destructive) colors.danger else colors.ink)
        }
    }
    val dismiss = @Composable {
        TextButton(onClick = onDismiss) { Text(dismissLabel, color = colors.ink) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            if (alternativeLabel == null) {
                confirm()
            } else {
                // Three answers do not fit one row, and Material's own wrap leaves one alone
                // on a second row out of order. So they stack, end-aligned, as Material
                // stacks buttons that do not fit: in the iOS alert's order, the plain answer,
                // then the destructive one, the safe one last.
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                    TextButton(onClick = onAlternative) { Text(alternativeLabel, color = colors.ink) }
                    confirm()
                    dismiss()
                }
            }
        },
        dismissButton = if (alternativeLabel == null) dismiss else null,
        title = { Text(title) },
        text = message?.let { { Text(it) } },
        containerColor = colors.surfaceRaised,
        titleContentColor = colors.ink,
        textContentColor = colors.inkMuted,
    )
}
