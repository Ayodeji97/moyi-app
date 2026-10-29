@file:OptIn(ExperimentalForeignApi::class)

package com.moyi.app.designsystem.platform

import androidx.compose.runtime.Composable
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import androidx.compose.runtime.remember
import androidx.compose.ui.uikit.LocalUIViewController
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIPasteboard
import platform.UIKit.popoverPresentationController
import platform.UIKit.UIViewController

/** `UIActivityViewController`, presented over whatever is on top. */
@Composable
actual fun rememberShareSheet(): ShareSheet {
    val host = LocalUIViewController.current
    return remember(host) {
        ShareSheet { text ->
            val top = host.topmostForShare()
            // A second tap while the sheet is up opens nothing on top of it.
            if (top is UIActivityViewController) return@ShareSheet
            val sheet = UIActivityViewController(activityItems = listOf(text), applicationActivities = null)
            // On iPad the sheet is a popover and needs an anchor, or UIKit throws.
            sheet.popoverPresentationController?.let { popover ->
                popover.sourceView = top.view
                popover.sourceRect = top.view.bounds.useContents { CGRectMake(size.width / 2, size.height / 2, 0.0, 0.0) }
            }
            top.presentViewController(sheet, animated = true, completion = null)
        }
    }
}

@Composable
actual fun rememberTextClipboard(): TextClipboard = remember {
    TextClipboard { text -> UIPasteboard.generalPasteboard.string = text }
}

private fun UIViewController.topmostForShare(): UIViewController {
    var top = this
    while (true) top = top.presentedViewController ?: return top
}
