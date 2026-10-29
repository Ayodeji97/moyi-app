package com.moyi.app.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.uikit.LocalUIViewController
import platform.UIKit.UIAlertAction
import platform.UIKit.UIAlertActionStyleCancel
import platform.UIKit.UIAlertActionStyleDefault
import platform.UIKit.UIAlertActionStyleDestructive
import platform.UIKit.UIAlertController
import platform.UIKit.UIAlertControllerStyleAlert
import platform.UIKit.UIViewController

/**
 * A `UIAlertController` alert, presented over the Compose view controller. iOS
 * draws it, so it is the system's look, type and Dynamic Type, and VoiceOver reads
 * it as an alert. Cancel takes the cancel style (iOS places it and makes it the
 * default for Escape); a destructive confirm is red, as iOS draws it.
 *
 * Presented when it enters the composition and dismissed if it leaves before an
 * answer (the screen went away under it). An answer dismisses it on its own.
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
    val host = LocalUIViewController.current
    val currentOnConfirm by rememberUpdatedState(onConfirm)
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    DisposableEffect(host, title, message, confirmLabel, dismissLabel, destructive) {
        var answered = false
        val alert = UIAlertController.alertControllerWithTitle(title, message, UIAlertControllerStyleAlert)
        alert.addAction(
            UIAlertAction.actionWithTitle(dismissLabel, UIAlertActionStyleCancel) { _ ->
                answered = true
                currentOnDismiss()
            },
        )
        alert.addAction(
            UIAlertAction.actionWithTitle(
                confirmLabel,
                if (destructive) UIAlertActionStyleDestructive else UIAlertActionStyleDefault,
            ) { _ ->
                answered = true
                currentOnConfirm()
            },
        )
        host.topmostPresented().presentViewController(alert, animated = true, completion = null)
        onDispose {
            if (!answered) alert.dismissViewControllerAnimated(true, completion = null)
        }
    }
}

/** The view controller currently on top: an alert can only be presented from one that presents nothing. */
private fun UIViewController.topmostPresented(): UIViewController {
    var top = this
    while (true) top = top.presentedViewController ?: return top
}
