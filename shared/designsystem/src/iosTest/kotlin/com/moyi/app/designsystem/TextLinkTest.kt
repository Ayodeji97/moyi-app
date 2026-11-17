@file:OptIn(ExperimentalTestApi::class)

package com.moyi.app.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moyi.app.designsystem.component.TextLink
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * D14 (owner ruling 2026-10-30): a secondary link is drawn in sentence case, in `body`,
 * underlined, on a target of at least 48. The drawn text is read from the text layout,
 * not from semantics, so a link that spoke one casing and drew another would fail here.
 */
class TextLinkTest {
    private fun ComposeUiTest.link(text: String, modifier: Modifier = Modifier.fillMaxWidth()) =
        setContent {
            MoyiTheme(darkTheme = true) {
                TextLink(text, onClick = {}, modifier = modifier)
            }
        }

    private fun ComposeUiTest.layoutOf(text: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        val node = onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode()
        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
        return results.single()
    }

    @Test
    fun theWordsAreDrawnAsWrittenNotUppercased() = runComposeUiTest {
        link("I forgot my password")
        assertEquals("I forgot my password", layoutOf("I forgot my password").layoutInput.text.text)
    }

    @Test
    fun itIsSetInBodyAndUnderlined() = runComposeUiTest {
        link("Send it again")
        val style = layoutOf("Send it again").layoutInput.style
        assertEquals(16.sp, style.fontSize)
        assertEquals(TextDecoration.Underline, style.textDecoration)
    }

    @Test
    fun theTargetIsAtLeast48() = runComposeUiTest {
        link("Use a different address")
        val bounds = onNodeWithText("Use a different address").fetchSemanticsNode().boundsInRoot
        val height = with(density) { bounds.height.toDp() }
        assertTrue(height >= 48.dp, "target is $height tall")
    }

    /** A word narrower than 48 still gets a 48 x 48 target: width is checked, not only height. */
    @Test
    fun aShortLinkIsStill48Wide() = runComposeUiTest {
        link("Go", modifier = Modifier)
        val bounds = onNodeWithText("Go").fetchSemanticsNode().boundsInRoot
        val width = with(density) { bounds.width.toDp() }
        val height = with(density) { bounds.height.toDp() }
        assertTrue(width >= 48.dp, "target is $width wide")
        assertTrue(height >= 48.dp, "target is $height tall")
    }

    /**
     * Switching the phone between light and dark redraws the underline in the new `ink`, not
     * only the words. On iOS the paragraph keeps the colour its underline was built with, and a
     * colour change alone repaints only the glyphs (Skiko `ParagraphLayouter.setColor`), so a
     * link that lived through the switch is compared, pixel for pixel, with one drawn fresh in
     * the new theme.
     */
    @Test
    fun theUnderlineTakesTheNewThemesColourAfterASwitch() = runComposeUiTest {
        var dark by mutableStateOf(false)
        setContent {
            Column {
                MoyiTheme(darkTheme = dark) {
                    TextLink("Not now", onClick = {}, modifier = Modifier.testTag("switched"))
                }
                MoyiTheme(darkTheme = true) {
                    TextLink("Not now", onClick = {}, modifier = Modifier.testTag("fresh"))
                }
            }
        }
        waitForIdle()
        dark = true
        waitForIdle()

        val switched = onNodeWithTag("switched").captureToImage().toPixelMap()
        val fresh = onNodeWithTag("fresh").captureToImage().toPixelMap()
        assertEquals(fresh.width, switched.width)
        assertEquals(fresh.height, switched.height)
        val differing = (0 until fresh.height).sumOf { y ->
            (0 until fresh.width).count { x -> switched[x, y] != fresh[x, y] }
        }
        assertEquals(0, differing, "pixels that differ from a link drawn fresh in dark")
    }
}
