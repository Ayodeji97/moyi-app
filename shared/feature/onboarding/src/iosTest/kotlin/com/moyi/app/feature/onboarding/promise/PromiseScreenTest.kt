@file:OptIn(ExperimentalTestApi::class)

package com.moyi.app.feature.onboarding.promise

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.moyi.app.designsystem.MoyiTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The promise (owner ruling 2026-10-30, variant C): the heading read first, the
 * two sample entries announced as examples, the two actions, and the block
 * centred above the actions when it fits. The test window has no system insets.
 */
class PromiseScreenTest {
    private var begins = 0
    private var haveAccounts = 0

    private fun ComposeUiTest.promise(width: Dp = 390.dp, height: Dp = 760.dp) = setContent {
        MoyiTheme(darkTheme = true) {
            Box(Modifier.size(width, height)) {
                PromiseScreen(onBegin = { begins++ }, onHaveAccount = { haveAccounts++ })
            }
        }
    }

    @Test
    fun theHeadingIsTheShortRuleAndIsReadFirst() = runComposeUiTest {
        promise()
        val heading = onNode(isHeading()).fetchSemanticsNode()
        assertEquals("Write before you read.", heading.text())
        // A screen reader walks the tree in order: the first node it can speak is the heading.
        val first = spoken(onRoot().fetchSemanticsNode()).first()
        assertEquals(heading.id, first.id)
    }

    @Test
    fun theSamplesAreOneStopAnnouncedAsExamples() = runComposeUiTest {
        promise()
        onNodeWithTag(PromiseTags.SAMPLES).assertContentDescriptionEquals(
            "Two example entries, not real ones. From Amara: You waited up. From Tunde: You made the tea before I asked.",
        )
        // The cards' own text is not a second stop: in the merged tree a screen reader walks,
        // neither entry, label nor author is reachable on its own.
        for (text in listOf("You waited up.", "You made the tea before I asked.", "From Amara", "From Tunde", "An example")) {
            assertEquals(0, onAllNodes(hasText(text)).fetchSemanticsNodes().size, text)
        }
    }

    @Test
    fun eachActionGoesOnce() = runComposeUiTest {
        promise()
        // The link's casing is not visible here: TextLink speaks the words as written in
        // either style, so sentence case is checked on the device screenshots instead.
        onNodeWithText("I already have an account").performClick()
        assertEquals(1, haveAccounts)
        onNodeWithText("Begin").performClick()
        assertEquals(1, begins)
    }

    @Test
    fun whenTheBlockFitsItIsCentredAboveTheActions() = runComposeUiTest {
        promise(height = 760.dp)
        val headingTop = dp(onNode(isHeading()).fetchSemanticsNode().boundsInRoot.top)
        val samplesBottom = dp(onNodeWithTag(PromiseTags.SAMPLES).fetchSemanticsNode().boundsInRoot.bottom)
        val beginTop = dp(onNodeWithText("Begin").fetchSemanticsNode().boundsInRoot.top)
        // The room runs from the top padding (24) to 16 above the action block.
        val above = headingTop - 24.dp
        val below = (beginTop - 16.dp) - samplesBottom
        assertTrue(above > 24.dp, "the block should not hang from the top: $above above it")
        assertEquals(above.value, below.value, 1f, "above $above, below $below")
    }

    @Test
    fun whenTheBlockIsTooTallItTopAlignsAndScrolls() = runComposeUiTest {
        promise(width = 320.dp, height = 480.dp)
        val headingTop = dp(onNode(isHeading()).fetchSemanticsNode().boundsInRoot.top)
        assertEquals(24f, headingTop.value, 0.5f)
        // The block is taller than the room, so the actions are not pushed off: the samples scroll into view.
        onNodeWithTag(PromiseTags.SAMPLES).performScrollTo()
        onNodeWithText("Begin").performScrollTo().assertIsDisplayed()
    }

    private fun spoken(node: SemanticsNode): List<SemanticsNode> {
        val self = if (node.text() != null || node.config.getOrNull(SemanticsProperties.ContentDescription) != null) listOf(node) else emptyList()
        return self + node.children.flatMap { spoken(it) }
    }

    private fun SemanticsNode.text(): String? = config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }

    private fun ComposeUiTest.dp(px: Float): Dp = with(density) { px.toDp() }
}
