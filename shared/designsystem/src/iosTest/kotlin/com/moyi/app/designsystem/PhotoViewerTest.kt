@file:OptIn(ExperimentalTestApi::class)

package com.moyi.app.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import com.moyi.app.designsystem.component.EntryCard
import com.moyi.app.designsystem.component.EntryCardVariant
import com.moyi.app.designsystem.component.EntryPhoto
import com.moyi.app.designsystem.component.EntryPhotoLook
import com.moyi.app.designsystem.component.EntryPhotoView
import com.moyi.app.designsystem.component.PHOTO_VIEWER_TAG
import com.moyi.app.designsystem.component.RESERVED_VOICE_ROW_TAG
import com.moyi.app.designsystem.component.VoiceNote
import com.moyi.app.designsystem.component.VoiceNoteLook
import kotlin.test.Test
import kotlin.test.assertEquals

/** The photo on an EntryCard opens full screen and closes again (docs/missing-components.md §70). */
class PhotoViewerTest {
    private val photo = EntryPhoto(key = "m1", url = "https://storage.test/m1?sig", width = 1080, height = 1920, look = EntryPhotoLook.Shown)

    @Test
    fun aTapOpensTheViewerAndCloseReturns() = runComposeUiTest {
        setContent {
            MoyiTheme(darkTheme = false) {
                Box(Modifier.width(310.dp)) { EntryCard("From you", "You made me tea.", photo = photo) }
            }
        }
        onNodeWithTag(PHOTO_VIEWER_TAG).assertDoesNotExist()

        onNodeWithContentDescription("Photo").performClick()
        onNodeWithTag(PHOTO_VIEWER_TAG).assertExists()
        // The photo keeps its description in the viewer, and the close target is named.
        onNodeWithContentDescription("Close").assertExists()

        onNodeWithContentDescription("Close").performClick()
        onNodeWithTag(PHOTO_VIEWER_TAG).assertDoesNotExist()
    }

    @Test
    fun aPartnersPhotoOpensProtectedToo() = runComposeUiTest {
        // No window to flag in a test host; this pins that the protected path opens and closes.
        setContent {
            MoyiTheme(darkTheme = true) {
                Box(Modifier.width(310.dp)) { EntryCard("From Pax", "You listened.", variant = EntryCardVariant.Partner, photo = photo) }
            }
        }
        onNodeWithContentDescription("Photo").performClick()
        onNodeWithTag(PHOTO_VIEWER_TAG).assertExists()
        onNodeWithContentDescription("Close").performClick()
        onNodeWithTag(PHOTO_VIEWER_TAG).assertDoesNotExist()
    }

    @Test
    fun aCachedPhotoWithNoUrlHoldsItsCappedPlaceAndOpensNothing() = runComposeUiTest {
        setContent {
            MoyiTheme(darkTheme = false) {
                Box(Modifier.width(300.dp)) { EntryPhotoView(photo.copy(url = null)) }
            }
        }
        // 300 wide, a 9:16 photo capped at 3:4: 400 tall, before any URL arrives.
        onNodeWithContentDescription("Photo").assertHeightIsEqualTo(400.dp)
        onNodeWithContentDescription("Photo").performClick()
        onNodeWithTag(PHOTO_VIEWER_TAG).assertDoesNotExist()
    }

    @Test
    fun aWidePhotoIsShownWholeAtItsOwnShape() = runComposeUiTest {
        setContent {
            MoyiTheme(darkTheme = false) {
                Box(Modifier.width(300.dp)) { EntryPhotoView(photo.copy(width = 3000, height = 1000)) }
            }
        }
        onNodeWithContentDescription("Photo").assertHeightIsEqualTo(100.dp)
    }

    @Test
    fun aCachedVoiceNoteReservesTheHeightItsPlayerWillTakeWithoutDrawingIt() = runComposeUiTest {
        val live = VoiceNote(key = "v1", url = "https://storage.test/v1.ogg?sig", durationMs = 12_000, look = VoiceNoteLook.Playable)
        setContent {
            MoyiTheme(darkTheme = false) {
                Column {
                    Box(Modifier.width(310.dp).testTag("cached")) { EntryCard("From you", "You made me tea.", voice = live.copy(url = null)) }
                    Box(Modifier.width(310.dp).testTag("live")) { EntryCard("From you", "You made me tea.", voice = live) }
                }
            }
        }
        // The card drawn from the cache is already as tall as it will be once the URL arrives.
        val cached = onNodeWithTag("cached").getUnclippedBoundsInRoot().height
        assertEquals(onNodeWithTag("live").getUnclippedBoundsInRoot().height, cached)
        onNodeWithTag(RESERVED_VOICE_ROW_TAG, useUnmergedTree = true).assertExists()
        // Only the live card offers play to a screen reader: the reserved row is cleared from what it reads.
        val play = hasContentDescription("Play the voice note")
        onNode(play and hasAnyAncestor(hasTestTag("cached"))).assertDoesNotExist()
        onAllNodes(play and hasAnyAncestor(hasTestTag("live"))).onFirst().assertExists()
    }
}
