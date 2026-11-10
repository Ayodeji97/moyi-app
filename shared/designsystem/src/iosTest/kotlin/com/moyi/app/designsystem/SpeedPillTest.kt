@file:OptIn(ExperimentalTestApi::class)

package com.moyi.app.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.dp
import com.moyi.app.designsystem.component.SPEED_PILL_VISIBLE_TAG
import com.moyi.app.designsystem.component.VoiceNote
import com.moyi.app.designsystem.component.VoiceNoteLook
import com.moyi.app.designsystem.component.VoiceNoteView
import com.moyi.app.designsystem.platform.PlaybackSpeed
import com.moyi.app.designsystem.platform.SessionPlaybackSpeed
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The speed pill (docs/missing-components.md §64): on every voice note before play, a 48
 * target around a small pill that keeps the clock line's height, spoken as a button.
 */
class SpeedPillTest {
    @AfterTest
    fun backToNormal() {
        // The session's speed is app-wide: leave it as a new session finds it.
        while (SessionPlaybackSpeed.shared.speed.value != PlaybackSpeed.Normal) SessionPlaybackSpeed.shared.cycle()
    }

    @Test
    fun theFortyEightTargetCyclesTheSpeedAndSaysIt() = runComposeUiTest {
        setContent {
            MoyiTheme(darkTheme = true) {
                Box(Modifier.width(310.dp)) {
                    VoiceNoteView(VoiceNote(key = "v1", url = "https://storage.test/v1.ogg?sig", durationMs = 42_000, look = VoiceNoteLook.Playable))
                }
            }
        }
        // Before play: the pill (drawn, its text not in the tree) sits beside "0:00 of 0:42".
        onNodeWithTag(SPEED_PILL_VISIBLE_TAG, useUnmergedTree = true).assertExists()
        val pill = onNodeWithContentDescription("Playback speed, normal")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        val target = pill.fetchSemanticsNode().boundsInRoot
        assertTrue(with(density) { target.width.toDp() } >= 48.dp && with(density) { target.height.toDp() } >= 48.dp, "a 48 target")
        val visible = onNodeWithTag(SPEED_PILL_VISIBLE_TAG, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(visible.height < target.height, "the visible pill stays small inside it")

        pill.performClick()
        onNodeWithContentDescription("Playback speed, 1.5 times").assertExists()
        onNodeWithContentDescription("Playback speed, 1.5 times").performClick()
        onNodeWithContentDescription("Playback speed, 2 times").performClick()
        onNodeWithContentDescription("Playback speed, normal").assertExists()
    }
}
