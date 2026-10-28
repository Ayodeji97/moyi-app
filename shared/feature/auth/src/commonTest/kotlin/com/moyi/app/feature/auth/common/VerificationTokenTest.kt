package com.moyi.app.feature.auth.common

import kotlin.test.Test
import kotlin.test.assertEquals

class VerificationTokenTest {
    @Test
    fun aBareTokenIsTrimmedAndKept() {
        assertEquals("Xy-9_abc", extractVerificationToken("  Xy-9_abc\n"))
    }

    @Test
    fun theWholeLinkGivesItsTokenValue() {
        assertEquals("abc123", extractVerificationToken("http://localhost:3000/verify?token=abc123"))
    }

    @Test
    fun theValueStopsAtTheNextParameterFragmentOrSpace() {
        assertEquals("abc", extractVerificationToken("https://getmoyi.app/verify?lang=en&token=abc&utm=mail"))
        assertEquals("abc", extractVerificationToken("https://getmoyi.app/verify?token=abc#top"))
        assertEquals("abc", extractVerificationToken("Open this: https://getmoyi.app/verify?token=abc to finish."))
    }

    @Test
    fun anEmptyPasteOrEmptyValueIsEmpty() {
        assertEquals("", extractVerificationToken("   "))
        assertEquals("", extractVerificationToken("https://getmoyi.app/verify?token="))
    }
}
