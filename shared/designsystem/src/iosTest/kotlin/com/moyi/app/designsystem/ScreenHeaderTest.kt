@file:OptIn(ExperimentalTestApi::class)

package com.moyi.app.designsystem

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import com.moyi.app.designsystem.component.MoyiScaffold
import com.moyi.app.designsystem.component.ScreenHeader
import kotlin.test.Test
import kotlin.test.assertEquals

/** D1: the eyebrow shows on a root screen and goes under a back target. */
class ScreenHeaderTest {
    @Test
    fun aRootScreenKeepsTheEyebrow() = runComposeUiTest {
        setContent {
            MoyiTheme(darkTheme = true) {
                MoyiScaffold { ScreenHeader(eyebrow = "New bond", heading = "Set up your bond") }
            }
        }
        onNodeWithText("New bond").assertIsDisplayed()
        onNodeWithText("Set up your bond").assertIsDisplayed()
    }

    @Test
    fun underABackTargetTheEyebrowGoes() = runComposeUiTest {
        setContent {
            MoyiTheme(darkTheme = true) {
                MoyiScaffold(onBack = {}) { ScreenHeader(eyebrow = "New bond", heading = "Set up your bond") }
            }
        }
        assertEquals(0, onAllNodesWithTextCount("New bond"))
        onNodeWithText("Set up your bond").assertIsDisplayed()
    }

    private fun ComposeUiTest.onAllNodesWithTextCount(text: String): Int =
        onAllNodes(hasText(text)).fetchSemanticsNodes().size
}
