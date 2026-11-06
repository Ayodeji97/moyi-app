@file:OptIn(ExperimentalTestApi::class)

package com.moyi.app.designsystem

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moyi.app.designsystem.component.SecondaryAction
import com.moyi.app.designsystem.component.secondaryActionColor
import com.moyi.app.designsystem.theme.RosewoodDark
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The outlined secondary action (owner ruling 2026-11-06): a button, set in `body` in the
 * words as written and not underlined (the border is the cue, not an underline), on a
 * target of at least 48 x 48.
 */
class SecondaryActionTest {
    private fun ComposeUiTest.action(
        text: String,
        enabled: Boolean = true,
        onClick: () -> Unit = {},
    ) = setContent {
        MoyiTheme(darkTheme = true) {
            SecondaryAction(text, onClick = onClick, enabled = enabled, modifier = Modifier)
        }
    }

    private fun ComposeUiTest.layoutOf(text: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        val node = onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode()
        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
        return results.single()
    }

    @Test
    fun itIsAButtonThatRuns() = runComposeUiTest {
        var clicks = 0
        action("Edit what you wrote", onClick = { clicks++ })
        val node = onNodeWithText("Edit what you wrote")
        assertEquals(Role.Button, node.fetchSemanticsNode().config.getOrNull(SemanticsProperties.Role))
        node.performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun itIsSetInBodyAsWrittenAndNotUnderlined() = runComposeUiTest {
        action("Edit what you wrote")
        val layout = layoutOf("Edit what you wrote")
        assertEquals("Edit what you wrote", layout.layoutInput.text.text)
        assertEquals(16.sp, layout.layoutInput.style.fontSize)
        assertNull(layout.layoutInput.style.textDecoration)
    }

    @Test
    fun aShortLabelStillGetsA48By48Target() = runComposeUiTest {
        action("Go")
        val bounds = onNodeWithText("Go").fetchSemanticsNode().boundsInRoot
        val width = with(density) { bounds.width.toDp() }
        val height = with(density) { bounds.height.toDp() }
        assertTrue(width >= 48.dp, "target is $width wide")
        assertTrue(height >= 48.dp, "target is $height tall")
    }

    @Test
    fun disabledIsNotTappable() = runComposeUiTest {
        action("Delete what you wrote", enabled = false)
        onNodeWithText("Delete what you wrote").assertIsNotEnabled()
    }

    @Test
    fun editIsInkDeleteIsDangerDisabledIsInkMuted() {
        assertEquals(RosewoodDark.ink, RosewoodDark.secondaryActionColor(enabled = true, destructive = false))
        assertEquals(RosewoodDark.danger, RosewoodDark.secondaryActionColor(enabled = true, destructive = true))
        assertEquals(RosewoodDark.inkMuted, RosewoodDark.secondaryActionColor(enabled = false, destructive = true))
    }
}
