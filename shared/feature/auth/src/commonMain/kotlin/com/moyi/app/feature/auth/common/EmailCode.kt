package com.moyi.app.feature.auth.common

/** The typed code's length: six digits (backend `VerificationCode`, contract `VerifyEmailRequest.code`). */
const val EMAIL_CODE_LENGTH = 6

/**
 * What the one code field received: a keystroke, a pasted code, or a pasted link.
 *
 * The verify and reset emails carry both a link and a six-digit code, and people paste
 * whichever they copied. One field takes both, so nobody has to know which box a thing
 * belongs in (docs/missing-components.md §58).
 */
sealed interface CodeInput {
    /** Zero to six ASCII digits, all the field will hold. */
    data class Digits(val value: String) : CodeInput

    /** A pasted link: its token, sent as the link would be when opened. */
    data class Link(val token: String) : CodeInput
}

/**
 * Reads what landed in the code field. Pure, so it is tested directly (EmailCodeTest).
 *
 * - **A link** is anything carrying `token=`: the whole link from the email, as people copy
 *   it. Its token is taken as [extractVerificationToken] takes it.
 * - **Everything else is reduced to its digits**, the first six kept: a paste of "123 456",
 *   "123-456" or "Your code: 123456" becomes "123456"; a letter typed by mistake is dropped.
 *   Any script's decimal digits count and become ASCII (doc 14: an Arabic or Devanagari
 *   keyboard types its own numerals; the server takes only `0`–`9`).
 */
fun parseCodeInput(raw: String): CodeInput {
    if ("token=" in raw) {
        val token = extractVerificationToken(raw)
        // A link cut short is not a code: its host or port digits must not fill the field.
        return if (token.isEmpty()) CodeInput.Digits("") else CodeInput.Link(token)
    }
    val digits = buildString {
        for (c in raw) {
            if (length == EMAIL_CODE_LENGTH) break
            if (c.isDigit()) c.digitToIntOrNull()?.let { append('0' + it) }
        }
    }
    return CodeInput.Digits(digits)
}
