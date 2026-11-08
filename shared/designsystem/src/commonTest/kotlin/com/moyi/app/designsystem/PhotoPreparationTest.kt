package com.moyi.app.designsystem

import com.moyi.app.designsystem.platform.PhotoPreparation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PhotoPreparationTest {
    @Test
    fun aLargePhotoIsScaledSoItsLongerEdgeIsTheBackendsLargestVariant() {
        assertEquals(2048 to 1536, PhotoPreparation.fittedSize(4032, 3024))
        assertEquals(1536 to 2048, PhotoPreparation.fittedSize(3024, 4032))
        // 48 MP.
        assertEquals(2048 to 1536, PhotoPreparation.fittedSize(8064, 6048))
    }

    @Test
    fun aPhotoAlreadyWithinTheLimitIsNeverScaledUp() {
        assertEquals(1280 to 960, PhotoPreparation.fittedSize(1280, 960))
        assertEquals(2048 to 2048, PhotoPreparation.fittedSize(2048, 2048))
    }

    @Test
    fun aPanoramaKeepsItsShapeAndNoEdgeFallsBelowOnePixel() {
        val (w, h) = PhotoPreparation.fittedSize(16384, 4096)
        assertEquals(2048, w)
        assertEquals(512, h)
        assertEquals(2048 to 1, PhotoPreparation.fittedSize(100_000, 10))
    }

    @Test
    fun everyFittedSizeIsWithinTheBound() {
        for ((w, h) in listOf(4000 to 3000, 3000 to 4000, 2049 to 1, 1 to 9999, 5000 to 5000, 7 to 3)) {
            val (fw, fh) = PhotoPreparation.fittedSize(w, h)
            assertTrue(maxOf(fw, fh) <= PhotoPreparation.MAX_EDGE, "$w x $h -> $fw x $fh")
            assertTrue(fw >= 1 && fh >= 1)
        }
    }

    @Test
    fun theDecodeSampleHalvesOnlyWhileTheLongerEdgeStaysAtLeastTheTarget() {
        assertEquals(1, PhotoPreparation.sampleSize(2048, 1536))
        assertEquals(1, PhotoPreparation.sampleSize(4000, 3000))
        assertEquals(2, PhotoPreparation.sampleSize(4096, 3072))
        assertEquals(2, PhotoPreparation.sampleSize(8064, 6048))
        assertEquals(8, PhotoPreparation.sampleSize(16384, 4096))
        assertEquals(1, PhotoPreparation.sampleSize(640, 480))
    }

    @Test
    fun aPhotoWithNoSizeIsRefused() {
        assertFailsWith<IllegalArgumentException> { PhotoPreparation.fittedSize(0, 100) }
        assertFailsWith<IllegalArgumentException> { PhotoPreparation.sampleSize(100, 0) }
    }
}
