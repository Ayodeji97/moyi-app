@file:OptIn(ExperimentalTestApi::class)

package com.moyi.app.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.moyi.app.designsystem.component.AttachedPhoto
import com.moyi.app.designsystem.component.AttachedVoice
import com.moyi.app.designsystem.component.AttachedVoiceState
import com.moyi.app.designsystem.component.ComposeField
import com.moyi.app.designsystem.platform.PickedPhoto
import com.moyi.app.designsystem.platform.RecordedVoice
import com.moyi.app.designsystem.theme.MoyiDimens
import com.moyi.app.designsystem.theme.moyiTypography
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The composer never squeezes the words (docs/missing-components.md §66, the owner's iPhone,
 * 2026-11-10: with a photo and a voice note attached and the keyboard up, "God is" was cut to
 * half a line). The box here is as tall as that screenshot's: 358 x 316.
 */
class ComposeFieldRoomTest {
    @Test
    fun withTheKeyboardUpTheWordsKeepThreeLinesAndTheAttachmentsShareOneRow() = runComposeUiTest {
        showComposer(compact = true)
        assertTextKeepsThreeLines()
        val photo = onNodeWithContentDescription("Remove the photo").fetchSemanticsNode().boundsInRoot
        val voice = onNodeWithContentDescription("Remove the voice note").fetchSemanticsNode().boundsInRoot
        assertTrue(abs(photo.center.y - voice.center.y) < 1f, "the photo and the voice note on one row")
        assertTrue(photo.right <= voice.left, "the photo first")
        assertTrue(with(density) { photo.height.toDp() } >= 48.dp && with(density) { voice.width.toDp() } >= 48.dp, "48 targets")
        // What a screen reader hears of the voice note does not change; only the bars go.
        onNodeWithContentDescription("Your voice note, 0:06").assertExists()
        onNodeWithContentDescription("Play the voice note").assertExists()
        onNodeWithContentDescription("The photo you are adding").assertExists()
        onNodeWithContentDescription("Record again").assertDoesNotExist()
    }

    @Test
    fun withTheKeyboardDownTheBoxScrollsRatherThanCutTheWords() = runComposeUiTest {
        showComposer(compact = false)
        // Full size, the attachments take more than the box has left: the words still keep three lines.
        assertTextKeepsThreeLines()
        onNodeWithContentDescription("Record again").assertExists()
        // Unclipped positions: the voice note may sit below the fold, reached by scrolling.
        val photo = onNodeWithContentDescription("Remove the photo").fetchSemanticsNode()
        val voice = onNodeWithContentDescription("Remove the voice note").fetchSemanticsNode()
        assertTrue(voice.positionInRoot.y >= photo.positionInRoot.y + photo.size.height, "stacked, the voice note under the photo")
    }

    private fun ComposeUiTest.showComposer(compact: Boolean) {
        setContent {
            MoyiTheme(darkTheme = true) {
                Box(Modifier.width(358.dp).height(316.dp)) {
                    ComposeField(
                        value = "God is",
                        onValueChange = {},
                        prompt = "What would today have been like without them?",
                        onDismissPrompt = {},
                        onAddPhoto = {},
                        compactAttachments = compact,
                        attachment = { isCompact ->
                            AttachedPhoto(photo = PickedPhoto(ByteArray(0), 3, 4), onRemove = {}, compact = isCompact)
                            AttachedVoice(
                                state = AttachedVoiceState.Recorded(RecordedVoice(ByteArray(0), 6_000)),
                                onStop = {},
                                onDiscard = {},
                                onRecordAgain = {},
                                onRemove = {},
                                compact = isCompact,
                            )
                        },
                    )
                }
            }
        }
    }

    /** The text field (its wrap included) is at least three lines of `entry` tall, not clipped by the layout. */
    private fun ComposeUiTest.assertTextKeepsThreeLines() {
        val field = onNode(hasSetTextAction()).fetchSemanticsNode()
        // `entry` is 34 / 1.40 (tokens.md): its line height in px.
        val entry = moyiTypography(FontFamily.Default, FontFamily.Default).entry
        val lineHeight = with(density) { entry.fontSize.toPx() } * entry.lineHeight.value
        val minimum = MoyiDimens.ComposeField.MinTextLines * lineHeight
        assertTrue(field.size.height >= minimum, "the words keep three lines: ${field.size.height} px of at least $minimum")
    }
}
