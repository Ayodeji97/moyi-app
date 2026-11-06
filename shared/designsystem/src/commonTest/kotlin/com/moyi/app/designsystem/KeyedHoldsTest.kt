package com.moyi.app.designsystem

import com.moyi.app.designsystem.platform.KeyedHolds
import kotlin.test.Test
import kotlin.test.assertEquals

/** Capture protection per window (R3): shared per key, and nothing kept once a key's last holder leaves. */
class KeyedHoldsTest {
    private val calls = mutableListOf<String>()
    private val holds = KeyedHolds<String>(onFirst = { calls += "on $it" }, onLast = { calls += "off $it" })

    @Test
    fun holdersOfOneKeyShareTheSwitch() {
        holds.acquire("a")
        holds.acquire("a")
        holds.release("a")
        assertEquals(listOf("on a"), calls, "one card left: still protected")
        holds.release("a")
        assertEquals(listOf("on a", "off a"), calls)
    }

    @Test
    fun keysAreIndependent() {
        holds.acquire("a")
        holds.acquire("b")
        holds.release("a")
        assertEquals(listOf("on a", "on b", "off a"), calls)
        assertEquals(1, holds.size)
    }

    @Test
    fun theLastReleaseDropsTheEntrySoTheKeyIsNotRetained() {
        holds.acquire("a")
        holds.acquire("a")
        holds.release("a")
        assertEquals(1, holds.size)
        holds.release("a")
        assertEquals(0, holds.size, "a recreated activity's old window must not stay in the registry")
    }

    @Test
    fun aKeyCanBeHeldAgainAfterItWasDropped() {
        holds.acquire("a")
        holds.release("a")
        holds.acquire("a")
        assertEquals(listOf("on a", "off a", "on a"), calls)
        assertEquals(1, holds.size)
    }

    @Test
    fun aReleaseWithNoHolderIsIgnored() {
        holds.release("a")
        assertEquals(emptyList(), calls)
        assertEquals(0, holds.size)
    }
}
