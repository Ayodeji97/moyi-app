@file:OptIn(ExperimentalForeignApi::class)

package com.moyi.app.designsystem.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import com.moyi.app.designsystem.MoyiTheme
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationWillResignActiveNotification
import platform.UIKit.UIColor
import platform.UIKit.UIView
import platform.UIKit.UIViewAutoresizingFlexibleHeight
import platform.UIKit.UIViewAutoresizingFlexibleWidth
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.darwin.NSObjectProtocol

/** One app, one cover: every protected card shares it. */
private val appCover by lazy { CaptureCover(NSNotificationCenter.defaultCenter) }

@Composable
internal actual fun ProtectFromCapture() {
    val surface = MoyiTheme.colors.surface
    DisposableEffect(Unit) {
        appCover.acquire(::keyWindow)
        onDispose { appCover.release() }
    }
    // Read at cover time, so a switch between dark and light is followed.
    SideEffect { appCover.color = surface.toUIColor() }
}

/**
 * Covers the window with a plain view while the app is not active, for as long
 * as anyone holds it.
 *
 * **Why resign-active and not enter-background:** iOS takes the app switcher's
 * snapshot after `willResignActive`, and resign-active also fires for the app
 * switcher gesture, Control Center and an incoming call. Covering then is what
 * keeps the words out of the snapshot. The cover comes off at `didBecomeActive`.
 *
 * A UIKit view on top of the window, not a Compose overlay: it is in place the
 * moment the notification is handled, with no recomposition or frame to wait for
 * before the snapshot is taken.
 */
internal class CaptureCover(private val center: NSNotificationCenter) {
    var color: UIColor = UIColor.blackColor

    private var window: () -> UIWindow? = { null }
    private var observers: List<NSObjectProtocol> = emptyList()
    private var cover: UIView? = null

    private val hold = SharedHold(onFirst = ::startObserving, onLast = ::stopObserving)

    val holders: Int get() = hold.holders
    val isCovering: Boolean get() = cover != null

    /** [window] is asked at cover time: a screen may hold this before it is in a window. */
    fun acquire(window: () -> UIWindow?) {
        this.window = window
        hold.acquire()
    }

    fun release() = hold.release()

    private fun startObserving() {
        val main = NSOperationQueue.mainQueue
        observers = listOf(
            center.addObserverForName(UIApplicationWillResignActiveNotification, null, main) { _ -> cover() },
            center.addObserverForName(UIApplicationDidBecomeActiveNotification, null, main) { _ -> uncover() },
        )
    }

    private fun stopObserving() {
        observers.forEach(center::removeObserver)
        observers = emptyList()
        // The last protected screen closed while the app was away: nothing left to hide.
        uncover()
        window = { null }
    }

    private fun cover() {
        if (cover != null) return
        val target = window() ?: return
        cover = UIView(frame = target.bounds).apply {
            backgroundColor = color
            autoresizingMask = UIViewAutoresizingFlexibleWidth or UIViewAutoresizingFlexibleHeight
            target.addSubview(this)
        }
    }

    private fun uncover() {
        cover?.removeFromSuperview()
        cover = null
    }
}

private fun Color.toUIColor(): UIColor {
    val srgb = convert(ColorSpaces.Srgb)
    return UIColor.colorWithRed(srgb.red.toDouble(), srgb.green.toDouble(), srgb.blue.toDouble(), srgb.alpha.toDouble())
}

/**
 * The app's key window, asked only when the cover goes on. Not the Compose host's
 * window: the composition local is absent in UI tests and previews, and the app
 * has one window anyway.
 */
private fun keyWindow(): UIWindow? =
    UIApplication.sharedApplication.connectedScenes
        .filterIsInstance<UIWindowScene>()
        .flatMap { scene -> scene.windows.filterIsInstance<UIWindow>() }
        .firstOrNull { it.isKeyWindow() }
