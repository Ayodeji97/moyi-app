package com.moyi.app.feature.auth.common

import kotlin.test.Test
import kotlin.test.assertEquals

class EmailCodeTest {
    @Test
    fun keystrokesAndPastesAreReducedToSixDigits() {
        assertEquals(CodeInput.Digits(""), parseCodeInput(""))
        assertEquals(CodeInput.Digits("12"), parseCodeInput("1a2"))
        assertEquals(CodeInput.Digits("123456"), parseCodeInput(" 123 456\n"))
        assertEquals(CodeInput.Digits("123456"), parseCodeInput("123-456"))
        assertEquals(CodeInput.Digits("944457"), parseCodeInput("Your code: 944457"))
        assertEquals(CodeInput.Digits("123456"), parseCodeInput("1234567890"))
    }

    @Test
    fun aLeadingZeroIsKept() {
        assertEquals(CodeInput.Digits("004217"), parseCodeInput("004217"))
    }

    @Test
    fun otherScriptsDigitsBecomeAscii() {
        // Arabic-Indic and Devanagari keyboards type their own numerals.
        assertEquals(CodeInput.Digits("123456"), parseCodeInput("١٢٣٤٥٦"))
        assertEquals(CodeInput.Digits("789"), parseCodeInput("७८९"))
    }

    @Test
    fun aPastedLinkIsItsToken() {
        assertEquals(CodeInput.Link("FIRP2_Gh"), parseCodeInput("http://localhost:3000/verify?token=FIRP2_Gh"))
        assertEquals(CodeInput.Link("abc"), parseCodeInput("https://getmoyi.app/reset-password?token=abc&x=1\n"))
    }

    @Test
    fun aLinkWithAnEmptyTokenIsReadAsDigits() {
        assertEquals(CodeInput.Digits(""), parseCodeInput("https://getmoyi.app/verify?token="))
    }
}
