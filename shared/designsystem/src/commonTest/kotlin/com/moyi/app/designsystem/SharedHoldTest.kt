package com.moyi.app.designsystem

import com.moyi.app.designsystem.platform.SharedHold
import kotlin.test.Test
import kotlin.test.assertEquals

/** Capture protection (R3) is on while any partner card is shown, and off only after the last. */
class SharedHoldTest {
    private val calls = mutableListOf<String>()
    private val hold = SharedHold(onFirst = { calls += "on" }, onLast = { calls += "off" })

    @Test
    fun turnsOnOnceAndOffOnlyAfterTheLastHolder() {
        hold.acquire()
        hold.acquire()
        hold.release()
        assertEquals(listOf("on"), calls, "one card left: still protected")
        hold.release()
        assertEquals(listOf("on", "off"), calls)
    }

    @Test
    fun anExtraReleaseNeverSwitchesOffALaterHolder() {
        hold.release()
        hold.acquire()
        hold.release()
        hold.release()
        hold.acquire()
        assertEquals(listOf("on", "off", "on"), calls)
        assertEquals(1, hold.holders)
    }
}
