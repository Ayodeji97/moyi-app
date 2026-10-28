@file:OptIn(ExperimentalTestApi::class)

package com.moyi.app.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.moyi.app.designsystem.component.MoyiScaffold
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The scaffold measured in a fixed frame. "Visible" heights are the parts of
 * a node not clipped by its ancestors. The test window has no system insets.
 */
class MoyiScaffoldLayoutTest {
    private fun ComposeUiTest.scaffold(width: Dp, height: Dp, content: Dp, action: Dp) = setContent {
        MoyiTheme(darkTheme = true) {
            Box(Modifier.size(width, height)) {
                MoyiScaffold(
                    action = { Box(Modifier.fillMaxWidth().requiredHeight(action).testTag("action")) },
                ) {
                    Box(Modifier.fillMaxWidth().requiredHeight(content).testTag("content"))
                }
            }
        }
    }

    private fun ComposeUiTest.visibleHeight(tag: String): Dp {
        val bounds = onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        return with(density) { bounds.height.toDp() }
    }

    private fun ComposeUiTest.visibleBottom(tag: String): Dp {
        val bounds = onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        return with(density) { bounds.bottom.toDp() }
    }

    private fun assertDp(expected: Dp, actual: Dp) = assertEquals(expected.value, actual.value, 0.5f, "expected $expected, was $actual")

    @Test
    fun onATallScreenTheActionsArePinnedAndOnlyTheContentScrolls() = runComposeUiTest {
        // 700, not 800: the test window is 768 high and `size` stays inside it.
        scaffold(width = 360.dp, height = 700.dp, content = 1000.dp, action = 80.dp)

        // 700 - 24 top - 24 bottom = 652; the action block sits at its foot, visible without scrolling.
        assertDp(80.dp, visibleHeight("action"))
        assertDp(676.dp, visibleBottom("action"))
        // The content gets the rest, 652 - 16 - 80, and scrolls inside it.
        assertDp(556.dp, visibleHeight("content"))
    }

    @Test
    fun onAShortScreenATallActionBlockScrollsWithTheContentInsteadOfSqueezingIt() = runComposeUiTest {
        // A 360 x 400 viewport (keyboard up, landscape, 200 % font) with actions over half the height.
        scaffold(width = 360.dp, height = 400.dp, content = 150.dp, action = 300.dp)

        // Pinned, the content would get 352 - 16 - 300 = 36. Stacked it keeps all 150.
        assertDp(150.dp, visibleHeight("content"))

        onNodeWithTag("action").performScrollTo()
        assertDp(300.dp, visibleHeight("action"))
    }
}
