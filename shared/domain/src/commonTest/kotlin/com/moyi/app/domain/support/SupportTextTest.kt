package com.moyi.app.domain.support

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** [SupportText]: the server's rule, counted in its unit (code points). */
class SupportTextTest {
    @Test
    fun blankIsRefused() {
        assertEquals(SupportTextProblem.Blank, SupportText.checkSubject(""))
        assertEquals(SupportTextProblem.Blank, SupportText.checkBody(" \n\t"))
    }

    @Test
    fun theLimitIsInclusive() {
        assertNull(SupportText.checkSubject("a".repeat(SupportText.SUBJECT_MAX)))
        assertEquals(SupportTextProblem.TooLong, SupportText.checkSubject("a".repeat(SupportText.SUBJECT_MAX + 1)))
        assertNull(SupportText.checkBody("a".repeat(SupportText.BODY_MAX)))
        assertEquals(SupportTextProblem.TooLong, SupportText.checkBody("a".repeat(SupportText.BODY_MAX + 1)))
    }

    @Test
    fun anEmojiIsOneCodePointNotTwoChars() {
        val heart = "💛" // U+1F49B, two UTF-16 chars
        assertEquals(1, SupportText.codePointCount(heart))
        // 120 emoji: 240 chars, but 120 code points, which the server accepts.
        assertNull(SupportText.checkSubject(heart.repeat(SupportText.SUBJECT_MAX)))
    }

    @Test
    fun nulAndALoneSurrogateCannotBeStored() {
        assertEquals(SupportTextProblem.Unsendable, SupportText.checkBody("a\u0000b"))
        assertEquals(SupportTextProblem.Unsendable, SupportText.checkBody("a\uD83Db"))
    }
}
