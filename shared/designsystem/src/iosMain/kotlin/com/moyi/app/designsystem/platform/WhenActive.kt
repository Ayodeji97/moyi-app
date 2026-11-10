package com.moyi.app.designsystem.platform

import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationState
import platform.darwin.NSObjectProtocol

/**
 * Runs an action now if the app is active, or once it next becomes active.
 *
 * The microphone permission alert leaves the app inactive for a moment after it closes, and
 * iOS refuses to start a recording audio session for an app that is not active. Starting the
 * first recording straight from the permission's answer failed on a real iPhone ("That
 * recording didn't work"), while the second tap worked, so the first recording waits here.
 */
internal class WhenActive(
    private val center: NSNotificationCenter = NSNotificationCenter.defaultCenter,
    private val isActive: () -> Boolean = {
        UIApplication.sharedApplication.applicationState == UIApplicationState.UIApplicationStateActive
    },
) {
    private var waiting: NSObjectProtocol? = null

    val isWaiting: Boolean get() = waiting != null

    fun run(action: () -> Unit) {
        cancel()
        if (isActive()) {
            action()
            return
        }
        waiting = center.addObserverForName(UIApplicationDidBecomeActiveNotification, null, NSOperationQueue.mainQueue) { _ ->
            cancel()
            action()
        }
    }

    /** Drops a waiting action: the person left Write before the app became active. */
    fun cancel() {
        waiting?.let(center::removeObserver)
        waiting = null
    }
}
