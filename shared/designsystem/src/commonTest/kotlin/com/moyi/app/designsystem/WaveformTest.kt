package com.moyi.app.designsystem

import com.moyi.app.designsystem.component.ActionPairMath
import com.moyi.app.designsystem.component.WaveformMath
import com.moyi.app.designsystem.platform.levelFromAmplitude
import com.moyi.app.designsystem.platform.levelFromDecibels
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** The arithmetic behind the voice-note waveforms and the Edit/Delete pair. */
class WaveformTest {
    @Test
    fun barsFitTheWidthWithNoGapAfterTheLast() {
        assertEquals(0, WaveformMath.barCount(2f, 3f, 2f))
        assertEquals(1, WaveformMath.barCount(3f, 3f, 2f))
        assertEquals(2, WaveformMath.barCount(8f, 3f, 2f))
        assertEquals(50, WaveformMath.barCount(248f, 3f, 2f))
    }

    @Test
    fun downsamplingTakesTheMeanOfEachShareSoPausesShow() {
        val levels = listOf(0.2f, 0.8f, 0.4f, 0.6f, 0f, 0f, 0.5f, 0.5f)
        assertEquals(listOf(0.5f, 0.5f, 0f, 0.5f), WaveformMath.resample(levels, 4))
        assertEquals(0.375f, WaveformMath.resample(levels, 1).single())
    }

    @Test
    fun upsamplingRepeatsEachLevelAcrossItsShare() {
        assertEquals(listOf(0.2f, 0.2f, 0.8f, 0.8f), WaveformMath.resample(listOf(0.2f, 0.8f), 4))
    }

    @Test
    fun noLevelsIsASilentRowAndNoBarsIsNothing() {
        assertEquals(List(3) { 0f }, WaveformMath.resample(emptyList(), 3))
        assertTrue(WaveformMath.resample(listOf(0.5f), 0).isEmpty())
    }

    @Test
    fun aTouchSeeksToItsFractionOfTheWidthMirroredRightToLeft() {
        assertEquals(0.25f, WaveformMath.seekFraction(50f, 200f))
        assertEquals(0f, WaveformMath.seekFraction(-10f, 200f), "before the start is the start")
        assertEquals(1f, WaveformMath.seekFraction(250f, 200f), "past the end is the end")
        assertEquals(0.75f, WaveformMath.seekFraction(50f, 200f, rtl = true))
        assertEquals(0f, WaveformMath.seekFraction(50f, 0f))
    }

    @Test
    fun aFractionIsAPositionInTheVoiceNoteAndBack() {
        assertEquals(10_500L, WaveformMath.positionAt(0.25f, 42_000))
        assertEquals(42_000L, WaveformMath.positionAt(1.4f, 42_000))
        assertEquals(0L, WaveformMath.positionAt(-1f, 42_000))
        assertEquals(0.25f, WaveformMath.fractionOf(10_500, 42_000))
        assertEquals(0f, WaveformMath.fractionOf(10_500, null), "unknown length: nothing played yet")
        assertEquals(1f, WaveformMath.fractionOf(50_000, 42_000))
    }

    @Test
    fun whileRecordingTheNewestBarsShowNewestLast() {
        assertEquals(listOf(null, null, 0.1f, 0.2f), WaveformMath.latest(listOf(0.1f, 0.2f), 4))
        assertEquals(listOf(0.3f, 0.4f), WaveformMath.latest(listOf(0.1f, 0.2f, 0.3f, 0.4f), 2))
    }

    @Test
    fun withReduceMotionTheRecordingFillsTheMinuteInPlace() {
        // A capacity of 8 samples in 4 bars: two samples recorded fill the first bar only.
        assertEquals(listOf(0.5f, null, null, null), WaveformMath.onTimeline(listOf(0.75f, 0.25f), 8, 4))
        assertEquals(listOf(0.5f, 0.5f, null, null), WaveformMath.onTimeline(listOf(0.75f, 0.25f, 0.5f), 8, 4))
    }

    @Test
    fun theStandInShapeIsTheSameForTheSameVoiceNoteAndModerate() {
        val one = WaveformMath.placeholder("0b6f3c1e-voice")
        assertEquals(one, WaveformMath.placeholder("0b6f3c1e-voice"), "deterministic")
        assertNotEquals(one, WaveformMath.placeholder("another-voice"))
        assertEquals(WaveformMath.PLACEHOLDER_COUNT, one.size)
        assertTrue(one.all { it in 0.2f..0.8f }, "never claims silence or a shout")
    }

    @Test
    fun theMeterReadsTheSameScaleOnBothPlatforms() {
        assertEquals(1f, levelFromDecibels(0f))
        assertEquals(0f, levelFromDecibels(-50f))
        assertEquals(0f, levelFromDecibels(-160f), "iOS's silence")
        assertEquals(0.5f, levelFromDecibels(-25f))
        assertEquals(0f, levelFromDecibels(Float.NaN))
        assertEquals(0f, levelFromAmplitude(0))
        assertEquals(1f, levelFromAmplitude(32_767))
        // Half of full scale is about -6 dB: loud speech.
        assertTrue(levelFromAmplitude(16_384) in 0.87f..0.89f)
    }

    @Test
    fun twoActionsSitSideBySideOnlyWhenBothFitInHalf() {
        // 288 wide, 8 gap: 140 each.
        assertTrue(ActionPairMath.fitsSideBySide(110, 120, 288, 8))
        assertTrue(ActionPairMath.fitsSideBySide(140, 140, 288, 8))
        assertFalse(ActionPairMath.fitsSideBySide(110, 141, 288, 8), "one too wide stacks both")
        assertFalse(ActionPairMath.fitsSideBySide(10, 10, 8, 8))
    }

    @Test
    fun theLiveWaveformHasABarFromTheFirstFrameBeforeAnySound() {
        val first = WaveformMath.liveLevels(emptyList(), elapsedMs = 0)
        assertEquals(1, first.size, "a bar at 0 ms, before the microphone reports")
        assertEquals(WaveformMath.idleLevel(0), first.single())
        // 250 ms is four ticks begun (0, 80, 160, 240), none reported yet: all idle, not blank.
        val quiet = WaveformMath.liveLevels(emptyList(), elapsedMs = 250)
        assertEquals(4, quiet.size)
        assertTrue(quiet.all { it > 0.1f })
    }

    @Test
    fun theLiveWaveformDrawsTheVoiceOverTheIdleBaseline() {
        val shown = WaveformMath.liveLevels(listOf(0.9f, 0f), elapsedMs = 100)
        assertEquals(0.9f, shown[0], "a loud level wins")
        assertEquals(WaveformMath.idleLevel(1), shown[1], "silence is the baseline, not a gap")
        assertEquals(5, WaveformMath.liveLevels(List(5) { 0.5f }, elapsedMs = 0).size, "never fewer than reported")
    }

    @Test
    fun theIdleBaselineIsGentleAndRipples() {
        val idle = (0 until 50).map(WaveformMath::idleLevel)
        assertTrue(idle.all { it in 0.1f..0.21f })
        assertTrue(idle.toSet().size > 10, "it varies, so its movement shows")
    }

    @Test
    fun theSlideRunsAcrossEachTick() {
        assertEquals(0f, WaveformMath.tickFraction(0))
        assertEquals(0.5f, WaveformMath.tickFraction(40))
        assertEquals(0f, WaveformMath.tickFraction(160))
    }
}
