@file:OptIn(ExperimentalTestApi::class)

package com.moyi.app.designsystem

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.moyi.app.designsystem.component.FactsCard
import com.moyi.app.designsystem.component.FactsCardLink
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The facts card (docs/missing-components.md §89): a heading, each fact its own line, and the one link. */
class FactsCardTest {
    private val facts = listOf("First fact.", "Second fact.", "Third fact.")

    @Test
    fun theLabelIsAHeadingAndEveryFactIsShownInOrder() = runComposeUiTest {
        setContent {
            MoyiTheme(darkTheme = true) {
                FactsCard(label = "Good to know", facts = facts, modifier = Modifier.width(358.dp))
            }
        }
        onNodeWithText("Good to know").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        val tops = facts.map { fact ->
            onNodeWithText(fact).assertIsDisplayed()
            onNodeWithText(fact).fetchSemanticsNode().boundsInRoot.top
        }
        assertTrue(tops.zipWithNext().all { (a, b) -> a < b }, "the facts are stacked in the order given: $tops")
    }

    @Test
    fun theLinkIsTheOneThingThatActs() = runComposeUiTest {
        var taps = 0
        setContent {
            MoyiTheme(darkTheme = false) {
                FactsCard(
                    label = "Good to know",
                    facts = facts,
                    link = FactsCardLink("Read the Privacy Policy", onClick = { taps++ }),
                    modifier = Modifier.width(358.dp),
                )
            }
        }
        onNodeWithText("Read the Privacy Policy").performClick()
        assertEquals(1, taps)
        val lastFact = onNodeWithText("Third fact.").fetchSemanticsNode().boundsInRoot
        val link = onNodeWithText("Read the Privacy Policy").fetchSemanticsNode().boundsInRoot
        assertTrue(link.top >= lastFact.bottom, "the link ends the card")
        assertTrue(link.height >= 48f, "a 48 target")
    }

    @Test
    fun withoutALinkNothingActs() = runComposeUiTest {
        setContent {
            MoyiTheme(darkTheme = true) { FactsCard(label = "Good to know", facts = facts) }
        }
        onNodeWithText("Read the Privacy Policy").assertDoesNotExist()
    }
}
