package com.moyi.app.designsystem

import com.moyi.app.designsystem.platform.WhenActive
import platform.Foundation.NSNotificationCenter
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The first recording after the microphone permission alert waits until the app is active
 * again. A private notification centre, so nothing here reaches the real app.
 */
class WhenActiveTest {
    private val center = NSNotificationCenter()
    private var active = false
    private val gate = WhenActive(center) { active }
    private var runs = 0

    private fun becomeActive() {
        active = true
        center.postNotificationName(UIApplicationDidBecomeActiveNotification!!, `object` = null)
    }

    @Test
    fun runsAtOnceWhenTheAppIsActive() {
        active = true
        gate.run { runs++ }
        assertEquals(1, runs)
        assertFalse(gate.isWaiting)
    }

    @Test
    fun waitsWhileInactiveAndRunsOnceOnBecomingActive() {
        gate.run { runs++ }
        assertEquals(0, runs, "not while the permission alert is closing")
        assertTrue(gate.isWaiting)
        becomeActive()
        assertEquals(1, runs)
        becomeActive()
        assertEquals(1, runs, "only once")
        assertFalse(gate.isWaiting)
    }

    @Test
    fun cancelledBeforeBecomingActiveNeverRuns() {
        gate.run { runs++ }
        gate.cancel()
        becomeActive()
        assertEquals(0, runs)
    }

    @Test
    fun aSecondRequestReplacesTheFirst() {
        gate.run { runs++ }
        gate.run { runs += 10 }
        becomeActive()
        assertEquals(10, runs)
    }
}
