@file:OptIn(ExperimentalTestApi::class)

package com.moyi.app.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.moyi.app.designsystem.component.AttachedVoice
import com.moyi.app.designsystem.component.AttachedVoiceState
import com.moyi.app.designsystem.component.SPEED_PILL_VISIBLE_TAG
import com.moyi.app.designsystem.component.VOICE_CLOCK_TAG
import com.moyi.app.designsystem.component.VOICE_ROW_TAG
import com.moyi.app.designsystem.component.VoiceNote
import com.moyi.app.designsystem.component.VoiceNoteLook
import com.moyi.app.designsystem.component.VoiceNoteView
import com.moyi.app.designsystem.component.VoiceWaveform
import com.moyi.app.designsystem.component.WaveformMath
import com.moyi.app.designsystem.platform.RecordedVoice
import com.moyi.app.designsystem.theme.RosewoodDark
import com.moyi.app.designsystem.theme.MoyiDimens
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The voice note on one line (docs/missing-components.md §68, owner request 2026-11-10): play,
 * the bars, the clock and the speed pill side by side, the played bars `ink` and the rest
 * `outlineStrong`; at twice the text size the clock and the pill go under the bars rather than
 * clip or squeeze them.
 */
class VoiceRowTest {
    private val voice = VoiceNote(key = "v1", url = "https://storage.test/v1.ogg?sig", durationMs = 42_000, look = VoiceNoteLook.Playable)

    @Test
    fun atNormalTextTheVoiceNoteIsOneFortyEightLine() = runComposeUiTest {
        // An EntryCard's inner width on a 390 phone: 390 - 2 x 16 - 2 x 24 = 310.
        show(fontScale = 1f) { VoiceNoteView(voice) }
        val row = bounds(VOICE_ROW_TAG)
        // Before §68 the row was the 48 band of the bars with the 24 clock line under it: 72.
        // Now it is one 48 line, the touch target's own height (NFR-101): two thirds of it.
        val height = with(density) { row.height.toDp() }
        assertTrue(abs(height.value - 48f) < 1f, "one 48 line, not $height")
        val bars = onNodeWithContentDescription("Position in the voice note").fetchSemanticsNode().boundsInRoot
        val clock = bounds(VOICE_CLOCK_TAG)
        val pill = bounds(SPEED_PILL_VISIBLE_TAG)
        assertTrue(bars.right <= clock.left && clock.right <= pill.left, "bars, then the clock, then the pill")
        assertTrue(abs(bars.center.y - clock.center.y) < 1f && abs(clock.center.y - pill.center.y) < 1f, "on one centre line")
        assertTrue(with(density) { bars.width.toDp() } >= MoyiDimens.Waveform.MinWidth, "the bars keep a usable width")
        // The play button is still a 48 target that says what it does.
        val play = onNodeWithContentDescription("Play the voice note").fetchSemanticsNode().boundsInRoot
        assertTrue(with(density) { play.width.toDp() } >= 48.dp && with(density) { play.height.toDp() } >= 48.dp, "a 48 play target")
    }

    @Test
    fun playedAndUnplayedBarsDiffer() = runComposeUiTest {
        setContent {
            MoyiTheme(darkTheme = true) {
                Box(Modifier.width(200.dp)) {
                    VoiceWaveform(
                        levels = WaveformMath.placeholder("v1"),
                        progress = { 0.5f },
                        label = "Position in the voice note",
                        position = "0:21 of 0:42",
                        onSeek = {},
                    )
                }
            }
        }
        val pixels = onNodeWithContentDescription("Position in the voice note").captureToImage().toPixelMap()
        val y = pixels.height / 2
        fun count(from: Int, until: Int, colour: Color) = (from until until).count { x -> pixels[x, y].near(colour) }
        val half = pixels.width / 2
        val ink = RosewoodDark.ink
        val unplayed = RosewoodDark.outlineStrong
        assertTrue(count(0, half, ink) > 20 && count(0, half, unplayed) == 0, "the played half is ink")
        assertTrue(count(half, pixels.width, unplayed) > 20 && count(half, pixels.width, ink) == 0, "the unplayed half is outlineStrong")
    }

    @Test
    fun atTwiceTheTextSizeTheClockAndPillWrapUnderTheBarsAndNothingClips() = runComposeUiTest {
        var clockSize = IntSize.Zero
        show(fontScale = 2f) {
            val measurer = rememberTextMeasurer()
            clockSize = measurer.measure(
                "0:00 of 0:42",
                MoyiTheme.type.body.copy(fontFeatureSettings = "tnum"),
            ).size
            VoiceNoteView(voice)
        }
        val row = bounds(VOICE_ROW_TAG)
        val bars = onNodeWithContentDescription("Position in the voice note").fetchSemanticsNode().boundsInRoot
        val clock = bounds(VOICE_CLOCK_TAG)
        val pill = bounds(SPEED_PILL_VISIBLE_TAG)
        assertTrue(with(density) { bars.width.toDp() } >= MoyiDimens.Waveform.MinWidth, "the bars keep a usable width: ${bars.width}")
        assertTrue(clock.top >= bars.bottom - 1f, "the clock under the bars")
        // Fully visible: the clock at its natural size (one line, not squeezed), inside the row, apart from the pill.
        assertTrue(abs(clock.width - clockSize.width) <= 1f && abs(clock.height - clockSize.height) <= 1f, "the clock unsqueezed: $clock vs $clockSize")
        assertInside(clock, row, "clock")
        assertInside(pill, row, "pill")
        assertTrue(!clock.overlaps(pill) && !pill.overlaps(bars), "the pill clear of the clock and the bars")
        onNodeWithContentDescription("Playback speed, normal").assertExists()
    }

    @Test
    fun theListenBackKeepsRecordAgainAndRemoveOnItsRow() = runComposeUiTest {
        // The composer's content width: a 358 box less its 8 padding each side.
        show(fontScale = 1f, width = 342) {
            AttachedVoice(
                state = AttachedVoiceState.Recorded(RecordedVoice(ByteArray(0), 6_000)),
                onStop = {},
                onDiscard = {},
                onRecordAgain = {},
                onRemove = {},
            )
        }
        val row = bounds(VOICE_ROW_TAG)
        val bars = onNodeWithContentDescription("Your voice note, 0:06").fetchSemanticsNode().boundsInRoot
        val again = onNodeWithContentDescription("Record again").fetchSemanticsNode().boundsInRoot
        val remove = onNodeWithContentDescription("Remove the voice note").fetchSemanticsNode().boundsInRoot
        assertTrue(abs(bars.center.y - again.center.y) < 1f && again.right <= remove.left, "Record again and Remove on the bars' line")
        assertTrue(with(density) { bars.width.toDp() } >= MoyiDimens.Waveform.MinWidth, "the bars keep a usable width")
        assertInside(bounds(VOICE_CLOCK_TAG), row, "clock")
        assertInside(bounds(SPEED_PILL_VISIBLE_TAG), row, "pill")
    }

    private fun ComposeUiTest.show(fontScale: Float, width: Int = 310, content: @Composable () -> Unit) {
        setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                MoyiTheme(darkTheme = true) { Box(Modifier.width(width.dp)) { content() } }
            }
        }
    }

    private fun ComposeUiTest.bounds(tag: String): Rect = onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun assertInside(inner: Rect, outer: Rect, what: String) {
        assertTrue(
            inner.left >= outer.left - 1f && inner.right <= outer.right + 1f && inner.top >= outer.top - 1f && inner.bottom <= outer.bottom + 1f,
            "the $what is inside the row: $inner in $outer",
        )
    }

    private fun Color.near(other: Color): Boolean =
        abs(red - other.red) < 0.04f && abs(green - other.green) < 0.04f && abs(blue - other.blue) < 0.04f && alpha > 0.9f
}
