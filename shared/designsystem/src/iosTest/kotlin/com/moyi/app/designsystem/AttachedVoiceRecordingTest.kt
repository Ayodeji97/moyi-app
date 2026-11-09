@file:OptIn(ExperimentalTestApi::class)

package com.moyi.app.designsystem

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.moyi.app.designsystem.component.AttachedVoice
import com.moyi.app.designsystem.component.AttachedVoiceState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The recording row (owner ruling 2026-11-09): trash at the start, the live waveform, the
 * dot and the clock, the tick at the end. Both icons are 48 targets that say what they do.
 */
class AttachedVoiceRecordingTest {
    @Test
    fun trashStartsTheRowTickEndsItAndTheDotIsSpokenWithTheClock() = runComposeUiTest {
        val taps = mutableListOf<String>()
        setContent {
            MoyiTheme(darkTheme = true) {
                AttachedVoice(
                    state = AttachedVoiceState.Recording(12_000),
                    onStop = { taps += "keep" },
                    onDiscard = { taps += "discard" },
                    onRecordAgain = null,
                    onRemove = null,
                    modifier = Modifier.width(272.dp),
                )
            }
        }
        val trash = onNodeWithContentDescription("Delete this recording").fetchSemanticsNode().boundsInRoot
        val tick = onNodeWithContentDescription("Stop and keep the recording").fetchSemanticsNode().boundsInRoot
        assertTrue(trash.right < tick.left, "trash first, tick last")
        assertTrue(with(density) { trash.width.toDp() } >= 48.dp && with(density) { tick.height.toDp() } >= 48.dp, "48 targets")
        onNodeWithContentDescription("Recording 0:12 of 1:00").assertExists()

        onNodeWithContentDescription("Delete this recording").performClick()
        onNodeWithContentDescription("Stop and keep the recording").performClick()
        assertEquals(listOf("discard", "keep"), taps)
    }
}
