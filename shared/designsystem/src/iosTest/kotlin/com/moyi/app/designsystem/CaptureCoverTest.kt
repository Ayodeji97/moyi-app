@file:OptIn(ExperimentalForeignApi::class)

package com.moyi.app.designsystem

import com.moyi.app.designsystem.platform.CaptureCover
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSNotificationCenter
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationWillResignActiveNotification
import platform.UIKit.UIColor
import platform.UIKit.UIWindow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * R3 on iOS: while a partner card is held, leaving the app covers the window in
 * `surface` before the app switcher's snapshot, and coming back uncovers it.
 * A private notification centre, so nothing here reaches the real app.
 */
class CaptureCoverTest {
    private val center = NSNotificationCenter()
    private val window = UIWindow(frame = CGRectMake(0.0, 0.0, 390.0, 844.0))
    private val cover = CaptureCover(center).apply { color = UIColor.redColor }

    private fun post(name: String?) = center.postNotificationName(name!!, `object` = null)
    private val subviews get() = window.subviews.size

    @Test
    fun resignActiveCoversTheWholeWindowAndBecomeActiveUncovers() {
        val before = subviews
        cover.acquire { window }
        post(UIApplicationWillResignActiveNotification)
        assertTrue(cover.isCovering)
        assertEquals(before + 1, subviews)
        val view = window.subviews.last() as platform.UIKit.UIView
        assertTrue(UIColor.redColor.isEqual(view.backgroundColor), "covered in the colour given")
        post(UIApplicationDidBecomeActiveNotification)
        assertFalse(cover.isCovering)
        assertEquals(before, subviews)
    }

    @Test
    fun withNoHolderLeavingTheAppCoversNothing() {
        cover.acquire { window }
        cover.release()
        post(UIApplicationWillResignActiveNotification)
        assertFalse(cover.isCovering)
    }

    @Test
    fun theLastHolderLeavingWhileAwayTakesTheCoverOff() {
        cover.acquire { window }
        cover.acquire { window }
        post(UIApplicationWillResignActiveNotification)
        cover.release()
        assertTrue(cover.isCovering, "one card still shown")
        cover.release()
        assertFalse(cover.isCovering)
    }

    @Test
    fun aSecondResignAddsNoSecondCover() {
        val before = subviews
        cover.acquire { window }
        post(UIApplicationWillResignActiveNotification)
        post(UIApplicationWillResignActiveNotification)
        assertEquals(before + 1, subviews)
    }
}
