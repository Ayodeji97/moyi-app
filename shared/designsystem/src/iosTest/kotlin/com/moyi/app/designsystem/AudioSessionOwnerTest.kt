package com.moyi.app.designsystem

import com.moyi.app.designsystem.platform.AudioSessionOwner
import kotlin.test.Test
import kotlin.test.assertEquals

/** Only whoever last made the audio session active may deactivate it. */
class AudioSessionOwnerTest {
    private var deactivations = 0
    private val owner = AudioSessionOwner { deactivations++ }
    private val player = Any()
    private val recorder = Any()

    @Test
    fun record_again_old_player_let_go_after_the_recorder_started_leaves_the_session_on() {
        owner.take(player) // listened back to the first recording
        owner.take(recorder) // Record again: the recorder starts first
        owner.letGo(player) // then the old player leaves the composition
        assertEquals(0, deactivations, "the recording keeps its session")

        owner.letGo(recorder)
        assertEquals(1, deactivations)
    }

    @Test
    fun the_owner_letting_go_deactivates_once() {
        owner.take(player)
        owner.letGo(player)
        owner.letGo(player)
        assertEquals(1, deactivations)
    }

    @Test
    fun a_player_that_never_played_does_not_deactivate() {
        owner.take(recorder)
        owner.letGo(player)
        assertEquals(0, deactivations)
    }
}
