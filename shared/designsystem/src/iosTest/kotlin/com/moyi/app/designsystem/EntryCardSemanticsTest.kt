@file:OptIn(ExperimentalTestApi::class)

package com.moyi.app.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.moyi.app.designsystem.component.EntryCard
import com.moyi.app.designsystem.component.EntryCardReaction
import com.moyi.app.designsystem.component.EntryCardVariant
import com.moyi.app.designsystem.component.SPEED_PILL_VISIBLE_TAG
import com.moyi.app.designsystem.component.VoiceNote
import com.moyi.app.designsystem.component.VoiceNoteLook
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What a screen reader gets from an [EntryCard]: its one merged stop. The drawn "1x" of a voice
 * note's speed pill and the drawn "You: Moved" of the reaction pill are for the eye; the merged
 * tree must carry neither (docs/missing-components.md §64, §67).
 */
class EntryCardSemanticsTest {
    /** Every string a node announces: its text and its content description. */
    private fun SemanticsNode.spoken(): List<String> =
        (config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }) +
            config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()

    private val voice = VoiceNote(key = "v1", url = "https://storage.test/v1.ogg?sig", durationMs = 42_000, look = VoiceNoteLook.Playable)

    @Test
    fun theMergedCardDoesNotReadTheDrawnSpeed() = runComposeUiTest {
        setContent {
            MoyiTheme(darkTheme = true) {
                Box(Modifier.width(310.dp)) {
                    EntryCard("From Pax", "You made me tea.", variant = EntryCardVariant.Partner, voice = voice)
                }
            }
        }
        // The pill is drawn (found by its tag), but its text is in no tree, merged or not...
        onNodeWithTag(SPEED_PILL_VISIBLE_TAG, useUnmergedTree = true).assertExists()
        onNodeWithText("1\u00D7", useUnmergedTree = true).assertDoesNotExist()
        // ...but the card, as one stop, never says it. The action stays reachable on its own.
        val card = onNodeWithText("From Pax").fetchSemanticsNode().spoken()
        assertTrue(card.any { it.contains("You made me tea.") }, "the words are read: $card")
        assertFalse(card.any { it.contains("1\u00D7") }, "the drawn speed is not read: $card")
        onNodeWithContentDescription("Playback speed, normal", useUnmergedTree = true).assertExists()
    }

    @Test
    fun theReactionPillIsDrawnAsYouAndSpokenAsYouReacted() = runComposeUiTest {
        setContent {
            MoyiTheme(darkTheme = false) {
                Box(Modifier.width(310.dp)) {
                    EntryCard(
                        "From Pax",
                        "You made me tea.",
                        variant = EntryCardVariant.Partner,
                        reaction = EntryCardReaction(text = "You: \uD83E\uDD79 Moved", spoken = "You reacted: Moved"),
                    )
                }
            }
        }
        // The pill has no semantics text of its own (drawn, spoken as the sentence) and no click action.
        onNodeWithText("You: \uD83E\uDD79 Moved", useUnmergedTree = true).assertDoesNotExist()
        val pill = onNodeWithContentDescription("You reacted: Moved", useUnmergedTree = true).fetchSemanticsNode()
        assertFalse(pill.config.contains(SemanticsActions.OnClick), "display-only")
        // Merged: the card reads the sentence, once, and never the drawn text.
        val card = onNodeWithText("From Pax").fetchSemanticsNode().spoken()
        assertEquals(1, card.count { it == "You reacted: Moved" }, "spoken once: $card")
        assertFalse(card.any { it.contains("You: ") }, "the drawn text is not read: $card")
    }

    @Test
    fun noReactionNoPill() = runComposeUiTest {
        setContent { MoyiTheme(darkTheme = false) { EntryCard("From Pax", "You made me tea.", variant = EntryCardVariant.Partner) } }
        onNodeWithContentDescription("You reacted: Moved", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun onlyAPartnerCardDrawsIt() = runComposeUiTest {
        setContent {
            MoyiTheme(darkTheme = false) {
                EntryCard("From you", "Tea.", reaction = EntryCardReaction("You: Moved", "You reacted: Moved"))
            }
        }
        onNodeWithContentDescription("You reacted: Moved", useUnmergedTree = true).assertDoesNotExist()
    }
}
