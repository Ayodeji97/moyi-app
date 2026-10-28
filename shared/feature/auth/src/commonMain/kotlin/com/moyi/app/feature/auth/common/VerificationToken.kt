package com.moyi.app.feature.auth.common

/**
 * The verification token from whatever was pasted: the bare token, or the
 * whole link from the email (`https://…/verify?token=abc&x=y`), which is what
 * people tend to copy.
 *
 * - Surrounding whitespace is dropped (a copied line often carries a newline).
 * - With `token=` present, the value runs to the next `&`, `#` or whitespace.
 * - Percent-escapes are left alone: the backend's tokens are URL-safe, so a
 *   `%` would mean the paste is not a token, and the server says so (422).
 *
 * Pure, so it is tested directly (VerificationTokenTest).
 */
fun extractVerificationToken(pasted: String): String {
    val trimmed = pasted.trim()
    val marker = "token="
    val start = trimmed.indexOf(marker)
    if (start < 0) return trimmed
    return trimmed.substring(start + marker.length).takeWhile { it != '&' && it != '#' && !it.isWhitespace() }
}
