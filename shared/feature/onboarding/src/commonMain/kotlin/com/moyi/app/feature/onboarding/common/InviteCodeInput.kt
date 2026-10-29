package com.moyi.app.feature.onboarding.common

/**
 * The shape rules for a typed invite code (states.md §2; ADR-0026). Pure, so
 * the ViewModel test checks them directly.
 *
 * - Case does not matter: input is upper-cased and trimmed of spaces.
 * - The alphabet is `23456789ABCDEFGHJKMNPQRSTVWXYZ`. `0 O 1 I L U` are
 *   **rejected with a message**, never silently mapped.
 * - Six characters. Anything past six is not taken.
 * - A pasted invite link gives the code at its end.
 */
object InviteCodeInput {
    const val LENGTH = 6
    const val ALPHABET = "23456789ABCDEFGHJKMNPQRSTVWXYZ"

    enum class Problem { UnusedCharacter, TooShort }

    /**
     * What the field keeps from a keystroke or a paste. A pasted invite link
     * (`https://getmoyi.app/i/K7Q2PD`, or without the scheme) gives its code:
     * the backend builds every link as the lander's address plus `/CODE`
     * (`InviteLinks`), so the code is the last path segment.
     */
    fun normalise(raw: String): String =
        codeFromLink(raw).uppercase().filter { it.isLetterOrDigit() }.take(LENGTH)

    /**
     * The last path segment of something with a `/` in it, without a query,
     * fragment or trailing slash; anything else unchanged. A typed code never
     * holds a `/`, so this cannot change what someone types.
     */
    internal fun codeFromLink(raw: String): String {
        val trimmed = raw.trim()
        if ('/' !in trimmed) return trimmed
        return trimmed.substringBefore('#').substringBefore('?').trimEnd('/').substringAfterLast('/')
    }

    /** Checked as the person types: only a character outside the alphabet is worth saying early. */
    fun liveProblem(code: String): Problem? =
        if (code.any { it !in ALPHABET }) Problem.UnusedCharacter else null

    /** Checked on Continue. */
    fun submitProblem(code: String): Problem? =
        liveProblem(code) ?: if (code.length < LENGTH) Problem.TooShort else null
}
