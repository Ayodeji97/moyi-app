package com.moyi.app.domain.auth

/**
 * What proves a person read the verification or reset email. The email carries both, and
 * the server takes exactly one of them (doc 06 §3.1, `VerifyEmailRequest` and
 * `ResetPasswordRequest`):
 *
 * - [Link]: the token after `?token=` in the emailed link, opened or pasted.
 * - [Code]: the six digits typed from the email, with the address it was sent to. The
 *   address is half of the proof: a code alone names no account.
 *
 * A sealed type rather than three nullable strings, so "a token and a code" or "a code with
 * no address", which the server refuses with `422 VALIDATION_FAILED`, cannot be built.
 */
sealed interface EmailProof {
    data class Link(val token: String) : EmailProof

    data class Code(val email: String, val code: String) : EmailProof {
        /** The digits are a secret until spent; a data class would print them in a log line. */
        override fun toString(): String = "Code(email=***, code=***)"
    }
}
