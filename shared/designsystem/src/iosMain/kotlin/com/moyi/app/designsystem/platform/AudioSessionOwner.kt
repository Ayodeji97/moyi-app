@file:OptIn(ExperimentalForeignApi::class)

package com.moyi.app.designsystem.platform

import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation
import platform.AVFAudio.setActive

/**
 * Who last made the app's one `AVAudioSession` active: the recorder or a player. Only that
 * one may deactivate it. Without this, a player let go after a recording had started (Record
 * again: the old listen-back player leaves the composition a frame after the recorder
 * started) deactivated the session under the recorder, which then kept a fraction of a
 * second. Main thread only.
 */
internal class AudioSessionOwner(private val deactivate: () -> Unit) {
    private var owner: Any? = null

    /** [by] is about to make the session active: from now on it is the one to let it go. */
    fun take(by: Any) {
        owner = by
    }

    /** Deactivates the session if [by] still owns it; otherwise someone else is using it, and it is left alone. */
    fun letGo(by: Any) {
        if (owner !== by) return
        owner = null
        deactivate()
    }

    companion object {
        val shared = AudioSessionOwner {
            AVAudioSession.sharedInstance().setActive(false, withOptions = AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation, error = null)
        }
    }
}
