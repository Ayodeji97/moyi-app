package com.moyi.app.domain.report

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReportTest {
    @Test
    fun aBlankDetailIsNotSent() {
        assertNull(reportDetailOrNull(""))
        assertNull(reportDetailOrNull("   \n "))
    }

    @Test
    fun aDetailIsSentAsWritten() {
        assertEquals("  they said it twice ", reportDetailOrNull("  they said it twice "))
    }

    @Test
    fun codePointsCountAnEmojiOnce() {
        assertEquals(1, codePointCount("😀"))
        assertEquals(3, codePointCount("a😀b"))
        // A lone surrogate is still one code point, not zero.
        assertEquals(1, codePointCount("\uD83D"))
    }

    @Test
    fun fiveHundredCodePointsIsTheLimit() {
        assertFalse(isReportDetailTooLong("a".repeat(500)))
        assertTrue(isReportDetailTooLong("a".repeat(501)))
        // 500 emoji are 1000 chars but 500 code points: allowed.
        assertFalse(isReportDetailTooLong("😀".repeat(500)))
    }
}
