package com.moyi.app.feature.onboarding.common

/**
 * The shape rules for a typed invite code (states.md §2; ADR-0026). Pure, so
 * the ViewModel test checks them directly.
 *
 * - Case does not matter: input is upper-cased and trimmed of spaces.
 * - The alphabet is `23456789ABCDEFGHJKMNPQRSTVWXYZ`. `0 O 1 I L U` are
 *   **rejected with a message**, never silently mapped.
 * - Six characters. Anything past six is not taken.
 */
object InviteCodeInput {
    const val LENGTH = 6
    const val ALPHABET = "23456789ABCDEFGHJKMNPQRSTVWXYZ"

    enum class Problem { UnusedCharacter, TooShort }

    /** What the field keeps from a keystroke or a paste. */
    fun normalise(raw: String): String =
        raw.uppercase().filter { it.isLetterOrDigit() }.take(LENGTH)

    /** Checked as the person types: only a character outside the alphabet is worth saying early. */
    fun liveProblem(code: String): Problem? =
        if (code.any { it !in ALPHABET }) Problem.UnusedCharacter else null

    /** Checked on Continue. */
    fun submitProblem(code: String): Problem? =
        liveProblem(code) ?: if (code.length < LENGTH) Problem.TooShort else null
}
