package com.moyi.app.designsystem

import com.moyi.app.designsystem.component.PhotoFrame
import com.moyi.app.designsystem.component.photoFrame
import kotlin.test.Test
import kotlin.test.assertEquals

/** The EntryCard photo's height cap (docs/missing-components.md §70): crop only what is taller than 3:4. */
class PhotoFrameTest {
    @Test
    fun aTallPhotoIsCappedAt3x4AndCropped() {
        assertEquals(PhotoFrame(0.75f, crops = true, known = true), photoFrame(1080, 1920)) // 9:16 screenshot
        assertEquals(PhotoFrame(0.75f, crops = true, known = true), photoFrame(1000, 3000))
    }

    @Test
    fun aCameraPortraitIsShownWholeAtTheCap() {
        assertEquals(PhotoFrame(0.75f, crops = false, known = true), photoFrame(3024, 4032))
    }

    @Test
    fun squareLandscapeAndPanoramaAreNeverCropped() {
        assertEquals(PhotoFrame(1f, crops = false, known = true), photoFrame(1200, 1200))
        assertEquals(PhotoFrame(4f / 3f, crops = false, known = true), photoFrame(4032, 3024))
        // The old 2:1 floor cropped panoramas; the cap is on height only.
        assertEquals(PhotoFrame(4f, crops = false, known = true), photoFrame(8000, 2000))
    }

    @Test
    fun noSizeHoldsThePlaceAtTheCap() {
        val placeholder = PhotoFrame(0.75f, crops = false, known = false)
        assertEquals(placeholder, photoFrame(null, null))
        assertEquals(placeholder, photoFrame(1080, null))
        assertEquals(placeholder, photoFrame(0, 1080))
    }
}
