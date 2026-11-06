package com.moyi.app.designsystem.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import com.moyi.app.designsystem.MoyiTheme

/**
 * A time of day, chosen in a dialog: Material 3's `TimePicker` (the clock dial) in an
 * `AlertDialog`, on both platforms, in Rosewood through `MoyiTheme`'s Material roles.
 *
 * STAND-IN (docs/missing-components.md): states.md §8 asks for the **OS time picker**
 * for the reveal time and the reminder. Android's own is this same Material dial; iOS's
 * is a `UIDatePicker` in time mode, which is not built, so iOS shows the Material dial
 * too until the owner rules. Twelve or twenty-four hours follows the device.
 *
 * Each answer closes the dialog and calls exactly one of [onConfirm] or [onDismiss].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeOfDayPicker(
    title: String,
    initialHour: Int,
    initialMinute: Int,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MoyiTheme.colors
    val state = rememberTimePickerState(initialHour = initialHour, initialMinute = initialMinute)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour, state.minute) }) { Text(confirmLabel, color = colors.ink) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(dismissLabel, color = colors.ink) }
        },
        title = { Text(title) },
        text = { TimePicker(state = state) },
        containerColor = colors.surfaceRaised,
        titleContentColor = colors.ink,
        textContentColor = colors.ink,
    )
}
