package com.moyi.app.designsystem.platform

import androidx.compose.runtime.Composable

/*
 * Screen capture is blocked only where the partner's words appear (owner ruling R3,
 * 2026-10-29; docs/figma/figma-deviations.md). Everything else, the person's own
 * entry and the write screen included, can be captured.
 */

/**
 * Protects the screen from capture while this is in the composition.
 *
 * - **Android**: `FLAG_SECURE` on the activity's window. Screenshots and recordings
 *   come out black, the recents card is blank, and it is the only Content Capture
 *   opt-out left at API 37 (P2).
 * - **iOS**: screenshots cannot be blocked. While the app is not active, the window
 *   is covered with `surface`, so the app switcher's snapshot holds no words.
 *
 * Called by [com.moyi.app.designsystem.component.EntryCard] for the `Partner`
 * variant, so every place that draws the partner's words is protected without
 * remembering to ask. Any number may be in the composition at once (an Archive
 * list, a reveal mid-transition): protection ends when the last one leaves.
 */
@Composable
internal expect fun ProtectFromCapture()

/**
 * A switch that several holders share: [onFirst] runs when the first holder
 * arrives, [onLast] when the last one leaves. Main thread only, like the
 * composition that drives it.
 */
internal class SharedHold(private val onFirst: () -> Unit, private val onLast: () -> Unit) {
    var holders: Int = 0
        private set

    fun acquire() {
        holders++
        if (holders == 1) onFirst()
    }

    fun release() {
        // A release with no holder is a bug elsewhere; it must not turn protection off
        // under a holder that arrives later, so the count never goes below zero.
        if (holders == 0) return
        holders--
        if (holders == 0) onLast()
    }
}

/**
 * One [SharedHold] per [K] (on Android, per window): holders of the same key share
 * the switch, and the entry is dropped when its last holder leaves. Nothing here
 * outlives its holders, so a key such as a destroyed activity's window is not kept
 * alive by this registry. Main thread only.
 */
internal class KeyedHolds<K : Any>(
    private val onFirst: (K) -> Unit,
    private val onLast: (K) -> Unit,
) {
    private val holds = HashMap<K, SharedHold>()

    /** How many keys are held right now. */
    val size: Int get() = holds.size

    fun acquire(key: K) {
        holds.getOrPut(key) { SharedHold(onFirst = { onFirst(key) }, onLast = { onLast(key) }) }.acquire()
    }

    fun release(key: K) {
        val hold = holds[key] ?: return
        hold.release()
        if (hold.holders == 0) holds.remove(key)
    }
}
