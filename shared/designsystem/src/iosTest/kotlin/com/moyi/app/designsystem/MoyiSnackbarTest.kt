@file:OptIn(ExperimentalTestApi::class)

package com.moyi.app.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.AccessibilityManager
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.moyi.app.designsystem.component.MoyiSnackbar
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The note at the foot of the screen (owner ruling 2026-11-17, docs/missing-components.md §87):
 * it stays 6 to 10 s by length, the platform's accessibility timeout can make that longer,
 * and a tap closes it early.
 */
class MoyiSnackbarTest {
    private var message by mutableStateOf<String?>(null)
    private var dismissals = 0

    /** Asks for twice the time, as Android does for a person who set a longer timeout. */
    private class Doubling : AccessibilityManager {
        override fun calculateRecommendedTimeoutMillis(
            originalTimeoutMillis: Long,
            containsIcons: Boolean,
            containsText: Boolean,
            containsControls: Boolean,
        ): Long = originalTimeoutMillis * 2
    }

    private fun ComposeUiTest.snackbar(accessibility: AccessibilityManager? = null) {
        mainClock.autoAdvance = false
        setContent {
            CompositionLocalProvider(LocalAccessibilityManager provides accessibility) {
                MoyiTheme(darkTheme = true) {
                    Box(Modifier.size(390.dp, 844.dp)) {
                        MoyiSnackbar(message = message, onDismiss = { dismissals++; message = null })
                    }
                }
            }
        }
    }

    private fun ComposeUiTest.show(text: String) {
        message = text
        mainClock.advanceTimeByFrame()
        waitForIdle()
    }

    @Test
    fun aShortNoteStaysSixSecondsThenGoes() = runComposeUiTest {
        snackbar()
        show("Your new password is saved.")

        mainClock.advanceTimeBy(5_800)
        assertEquals(0, dismissals, "still there before 6 s")
        mainClock.advanceTimeBy(400)
        assertEquals(1, dismissals, "gone at 6 s")
    }

    @Test
    fun theAccessibilityTimeoutCanMakeItLonger() = runComposeUiTest {
        snackbar(Doubling())
        show("Your new password is saved.")

        mainClock.advanceTimeBy(11_800)
        assertEquals(0, dismissals, "twice 6 s was asked for")
        mainClock.advanceTimeBy(400)
        assertEquals(1, dismissals)
    }

    @Test
    fun aTapClosesItEarly() = runComposeUiTest {
        snackbar()
        show("Your new password is saved.")
        mainClock.advanceTimeBy(1_000)

        onNodeWithText("Your new password is saved.").performClick()

        assertEquals(1, dismissals)
        mainClock.advanceTimeBy(10_000)
        assertEquals(1, dismissals, "the timer that was running does not dismiss it again")
    }

    @Test
    fun itIsOneButtonNamedCloseAtLeast48Tall() = runComposeUiTest {
        snackbar()
        show("Your new password is saved.")
        mainClock.advanceTimeBy(1_000)

        val node = onNodeWithText("Your new password is saved.").fetchSemanticsNode()
        assertEquals(Role.Button, node.config.getOrNull(SemanticsProperties.Role))
        assertEquals("Close", node.config.getOrNull(SemanticsActions.OnClick)?.label)
        assertTrue(with(density) { node.boundsInRoot.height.toDp() } >= 48.dp)
    }
}
