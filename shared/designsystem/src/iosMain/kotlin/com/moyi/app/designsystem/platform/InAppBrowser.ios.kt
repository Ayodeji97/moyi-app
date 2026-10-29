package com.moyi.app.designsystem.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.uikit.LocalUIViewController
import com.moyi.app.designsystem.MoyiTheme
import platform.Foundation.NSURL
import platform.SafariServices.SFSafariViewController
import platform.UIKit.UIColor
import platform.UIKit.UIViewController

/**
 * `SFSafariViewController`, presented modally over whatever is on top. Its own
 * Done button returns to the app; there is nothing to dismiss from here.
 */
@Composable
actual fun rememberInAppBrowser(): InAppBrowser {
    val host = LocalUIViewController.current
    val surface = MoyiTheme.colors.surface
    val ink = MoyiTheme.colors.ink
    return remember(host, surface, ink) {
        InAppBrowser { url ->
            if (!isWebUrl(url)) return@InAppBrowser
            val nsUrl = NSURL.URLWithString(url) ?: return@InAppBrowser
            val safari = SFSafariViewController(uRL = nsUrl).apply {
                preferredBarTintColor = surface.toUIColor()
                preferredControlTintColor = ink.toUIColor()
            }
            val top = host.topmost()
            // A second tap while a page is already up opens nothing on top of it.
            if (top is SFSafariViewController) return@InAppBrowser
            top.presentViewController(safari, animated = true, completion = null)
        }
    }
}

private fun UIViewController.topmost(): UIViewController {
    var top = this
    while (true) top = top.presentedViewController ?: return top
}

private fun Color.toUIColor(): UIColor =
    UIColor.colorWithRed(red.toDouble(), green.toDouble(), blue.toDouble(), alpha.toDouble())
