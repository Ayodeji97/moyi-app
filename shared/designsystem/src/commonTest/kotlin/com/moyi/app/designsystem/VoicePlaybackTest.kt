package com.moyi.app.designsystem

import com.moyi.app.designsystem.component.formatClock
import com.moyi.app.designsystem.platform.BaseVoicePlayer
import com.moyi.app.designsystem.platform.PlaybackPhase
import com.moyi.app.designsystem.platform.RecordedVoice
import com.moyi.app.designsystem.platform.VoicePlayback
import com.moyi.app.designsystem.platform.VoiceSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The voice player's state, the one-at-a-time rule and the clock, with a fake platform player. */
@OptIn(ExperimentalCoroutinesApi::class)
class VoicePlaybackTest {
    private val remote = VoiceSource.Remote("v1", "https://storage.test/v1.ogg?sig")

    @Test
    fun playFetchesOnlyAtTheTapThenPlaysAndTheClockFollowsThePlayer() = runTest {
        val player = FakePlayer(this)
        assertEquals(VoicePlayback(), player.playback.value)
        assertTrue(player.opened.isEmpty(), "nothing is fetched before play is tapped")

        player.toggle(remote)
        assertEquals(PlaybackPhase.Loading, player.playback.value.phase)
        assertTrue(player.playback.value.active, "the control shows Pause while it loads")
        assertEquals(listOf("v1"), player.opened)

        player.ready(durationMs = 42_000)
        player.position = 1_500
        advanceTimeBy(250)
        assertEquals(VoicePlayback(PlaybackPhase.Playing, 1_500, 42_000), player.playback.value)
        player.release()
    }

    @Test
    fun pauseKeepsThePlaceAndPlayResumesWithoutFetchingAgain() = runTest {
        val player = FakePlayer(this)
        player.toggle(remote)
        player.ready(42_000)
        player.position = 7_000
        player.toggle(remote)
        assertEquals(VoicePlayback(PlaybackPhase.Paused, 7_000, 42_000), player.playback.value)

        player.toggle(remote)
        assertEquals(PlaybackPhase.Playing, player.playback.value.phase)
        assertEquals(1, player.opened.size)
        assertEquals(1, player.resumed)
        player.release()
    }

    @Test
    fun theEndRewindsAndPlayingAgainNeedsNoSecondDownload() = runTest {
        val player = FakePlayer(this)
        player.toggle(remote)
        player.ready(42_000)
        player.end()
        assertEquals(VoicePlayback(PlaybackPhase.Idle, 0, 42_000), player.playback.value)

        player.toggle(remote)
        assertEquals(1, player.opened.size)
        assertEquals(1, player.resumed)
        player.release()
    }

    @Test
    fun pausingWhileItLoadsDropsTheFetchSoItNeverStartsLate() = runTest {
        val player = FakePlayer(this)
        player.toggle(remote)
        player.toggle(remote)
        assertEquals(PlaybackPhase.Idle, player.playback.value.phase)
        assertEquals(1, player.released)
    }

    @Test
    fun aFailureSaysSoAndPlayTriesAgainFromTheStart() = runTest {
        val player = FakePlayer(this)
        player.toggle(remote)
        player.fail()
        assertEquals(PlaybackPhase.Failed, player.playback.value.phase)
        assertFalse(player.playback.value.active)

        player.toggle(remote)
        assertEquals(listOf("v1", "v1"), player.opened, "a failed fetch is fetched again")
        player.release()
    }

    @Test
    fun aCallPausesItAndTheControlShowsPlay() = runTest {
        val player = FakePlayer(this)
        player.toggle(remote)
        player.ready(42_000)
        player.position = 3_000
        player.interrupt()
        assertEquals(VoicePlayback(PlaybackPhase.Paused, 3_000, 42_000), player.playback.value)
        player.release()
    }

    @Test
    fun onlyOnePlaysAtATime() = runTest {
        val first = FakePlayer(this)
        val second = FakePlayer(this)
        first.toggle(remote)
        first.ready(42_000)

        second.toggle(VoiceSource.Local("mine", RecordedVoice(ByteArray(4), 3_000)))

        assertEquals(PlaybackPhase.Paused, first.playback.value.phase)
        assertEquals(PlaybackPhase.Loading, second.playback.value.phase)
        first.release()
        second.release()
    }

    @Test
    fun leavingTheScreenStopsItAndLetsGoOfThePlayer() = runTest {
        val player = FakePlayer(this)
        player.toggle(remote)
        player.ready(42_000)
        player.release()
        assertEquals(PlaybackPhase.Idle, player.playback.value.phase)
        assertEquals(1, player.released)
        runCurrent()
    }

    @Test
    fun seekingWhilePlayingOrPausedMovesThePlayerAtOnce() = runTest {
        val player = FakePlayer(this)
        player.toggle(remote)
        player.ready(42_000)
        player.seekTo(21_000)
        assertEquals(listOf(21_000L), player.seeks)
        assertEquals(21_000L, player.playback.value.positionMs)
        assertEquals(PlaybackPhase.Playing, player.playback.value.phase, "seeking never pauses")

        player.toggle(remote)
        player.seekTo(99_000)
        assertEquals(42_000L, player.seeks.last(), "never past the end")
        assertEquals(PlaybackPhase.Paused, player.playback.value.phase, "seeking never plays")
        player.release()
    }

    @Test
    fun seekingBeforeTheFirstPlayStartsThereWithoutFetchingFirst() = runTest {
        val player = FakePlayer(this)
        player.seekTo(5_000)
        assertTrue(player.opened.isEmpty(), "a seek fetches nothing")
        assertTrue(player.seeks.isEmpty())
        assertEquals(5_000L, player.playback.value.positionMs)

        player.toggle(remote)
        assertEquals(5_000L, player.playback.value.positionMs, "the clock keeps the place while it loads")
        player.ready(42_000)
        assertEquals(listOf(5_000L), player.seeks, "the player starts where the person pointed")
        player.release()
    }

    @Test
    fun seekingAfterTheEndPlaysFromThereWithoutASecondDownload() = runTest {
        val player = FakePlayer(this)
        player.toggle(remote)
        player.ready(42_000)
        player.end()
        player.seekTo(30_000)
        assertEquals(listOf(30_000L), player.seeks)
        player.toggle(remote)
        assertEquals(1, player.opened.size)
        assertEquals(30_000L, player.playback.value.positionMs)
        player.release()
    }

    @Test
    fun theFileSaysHowLongItIsWhenTheServerDidNot() {
        assertEquals(41_960L, VoicePlayback().loading().playing(41_960).durationMs)
        assertEquals(42_000L, VoicePlayback(durationMs = 42_000).playing(null).durationMs)
        assertEquals(42_000L, VoicePlayback(durationMs = 42_000).at(50_000).positionMs, "never past the end")
    }

    @Test
    fun theClockReadsMinutesAndSeconds() {
        assertEquals("0:00", formatClock(0))
        assertEquals("0:07", formatClock(7_400))
        assertEquals("0:59", formatClock(59_900), "never ahead of the recording")
        assertEquals("1:00", formatClock(59_980), "a minute's recording reads as a minute")
        assertEquals("1:00", formatClock(60_000))
        assertEquals("0:00", formatClock(-5))
    }

    /** A platform player that does what it is told, and reports when the test says. */
    private class FakePlayer(scope: TestScope) : BaseVoicePlayer(scope as CoroutineScope) {
        val opened = mutableListOf<String>()
        var resumed = 0
        var released = 0
        var position = 0L
        val seeks = mutableListOf<Long>()

        override fun seekNative(positionMs: Long) {
            seeks += positionMs
            position = positionMs
        }

        override fun open(source: VoiceSource) {
            opened += source.key
        }

        override fun resume() {
            resumed++
        }

        override fun pauseNative() = Unit

        override fun releaseNative() {
            released++
        }

        override fun positionMs(): Long = position

        fun ready(durationMs: Long) = onReady(durationMs)

        fun end() {
            position = 0
            onEnded()
        }

        fun fail() = onFailed()

        fun interrupt() = onPausedBySystem()
    }
}
