@file:OptIn(ExperimentalTestApi::class)

package com.moyi.app.designsystem

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
import com.moyi.app.designsystem.component.ReactionBadge
import com.moyi.app.designsystem.component.ReactionChoices
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Owner rulings 1 and 6 (2026-11-09): each reaction chip, and the partner's reaction under my
 * entry, draws an emoji with its words, but only the words are spoken. The emoji must never
 * reach a screen reader, in any node's text or content description, merged or not.
 */
class ReactionEmojiTest {
    private val words = listOf("Heart", "Thank you", "Smile", "Moved", "Me too")
    private val emoji = listOf("❤️", "🙏", "😊", "🥹", "🤝")

    private fun SemanticsNode.allSpoken(): List<String> {
        val own = config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
            config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
        return own + children.flatMap { it.allSpoken() }
    }

    @Test
    fun eachChipIsSpokenByItsWordAloneAndTheEmojiIsNeverSpoken() = runComposeUiTest {
        setContent {
            MoyiTheme(darkTheme = true) {
                ReactionChoices(labels = words, emoji = emoji, selected = 1, onChoose = {}, takeBackLabel = "Take back your reaction")
            }
        }
        for (word in words) onNodeWithText(word).assertExists()
        for (tree in listOf(false, true)) {
            val spoken = onRoot(useUnmergedTree = tree).fetchSemanticsNode().allSpoken()
            for (glyph in emoji) {
                assertTrue(spoken.none { glyph in it }, "spoken: $spoken (unmerged=$tree)")
            }
        }
    }

    @Test
    fun theChipsWrapInsideTheNarrowestColumnAndKeep48Targets() = runComposeUiTest {
        setContent {
            MoyiTheme(darkTheme = true) {
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.width(320.dp)) {
                    ReactionChoices(labels = words, emoji = emoji, selected = null, onChoose = {}, takeBackLabel = "Take back your reaction")
                }
            }
        }
        val root = onRoot().fetchSemanticsNode().boundsInRoot
        for (word in words) {
            val chip = onNodeWithText(word).fetchSemanticsNode()
            val bounds = chip.boundsInRoot
            assertTrue(bounds.right <= root.right + 0.5f, "$word overflows: $bounds in $root")
            with(density) { assertTrue(bounds.height >= 48.dp.toPx() - 0.5f, "$word is ${bounds.height}px high") }
        }
    }

    @Test
    fun thePartnersReactionIsSpokenAsItsSentenceAlone() = runComposeUiTest {
        setContent {
            MoyiTheme(darkTheme = true) { ReactionBadge(emoji = emoji[0], text = "Tunde sent a heart.") }
        }
        val spoken = onRoot(useUnmergedTree = true).fetchSemanticsNode().allSpoken()
        assertEquals(listOf("Tunde sent a heart."), spoken.filter { it.isNotBlank() })
        assertEquals(0, onAllNodesWithText(emoji[0], substring = true, useUnmergedTree = true).fetchSemanticsNodes().size)
        assertTrue(onRoot().fetchSemanticsNode().config.getOrNull(SemanticsProperties.LiveRegion) == null)
    }
}
