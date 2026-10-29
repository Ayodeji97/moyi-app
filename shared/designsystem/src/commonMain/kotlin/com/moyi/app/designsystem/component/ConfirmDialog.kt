package com.moyi.app.designsystem.component

import androidx.compose.runtime.Composable

/**
 * A confirmation in the platform's own dialog (D10, D11): a Material `AlertDialog`
 * on Android, a `UIAlertController` alert on iOS. Shown while it is in the
 * composition; the caller decides when, from its state.
 *
 * SKILL.md §3: **confirm the irreversible, only.** Deleting a revealed entry and
 * discarding typed words qualify; nothing reversible does.
 *
 * Each answer closes the dialog and calls exactly one of [onConfirm] or
 * [onDismiss]. Android Back, a tap outside on Android, and Cancel on iOS all
 * call [onDismiss].
 *
 * @param destructive the confirm action destroys something: `danger` on Android
 *   (with its label saying so, so colour is never the only cue, NFR-104), the
 *   destructive style on iOS.
 */
@Composable
expect fun ConfirmDialog(
    title: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    message: String? = null,
    destructive: Boolean = false,
)
