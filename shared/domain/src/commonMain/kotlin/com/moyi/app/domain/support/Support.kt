package com.moyi.app.domain.support

import com.moyi.app.core.DataError
import com.moyi.app.core.Result
import kotlin.time.Instant

/**
 * A message to Moyi's support, filed: the ticket's [id] and when it arrived. Nothing of what
 * was written comes back (the server never shows it again).
 */
data class SupportTicket(val id: String, val createdAt: Instant)

/** Why a subject or a body cannot be sent, as [SupportText] checks it. */
enum class SupportTextProblem { Blank, TooLong, Unsendable }

/**
 * The contract's rule for a support message's text (`SupportMessageRequest`, FR-102): not
 * blank, no U+0000, no unpaired surrogate, and at most [SUBJECT_MAX] or [BODY_MAX]
 * **code points** (the server's unit, not graphemes and not UTF-16 chars, so an emoji
 * counts once here and once there). Blank is Kotlin's [isBlank], the server's own check.
 *
 * Checked before sending so the person is told at once; the server checks again and a
 * `422` naming the field is still handled.
 */
object SupportText {
    const val SUBJECT_MAX = 120
    const val BODY_MAX = 5000

    fun checkSubject(subject: String): SupportTextProblem? = check(subject, SUBJECT_MAX)

    fun checkBody(body: String): SupportTextProblem? = check(body, BODY_MAX)

    private fun check(text: String, max: Int): SupportTextProblem? = when {
        text.isBlank() -> SupportTextProblem.Blank
        '\u0000' in text || hasUnpairedSurrogate(text) -> SupportTextProblem.Unsendable
        codePointCount(text) > max -> SupportTextProblem.TooLong
        else -> null
    }

    /** A surrogate pair is one code point; every other char is one. */
    fun codePointCount(text: String): Int {
        var count = 0
        var i = 0
        while (i < text.length) {
            i += if (text[i].isHighSurrogate() && i + 1 < text.length && text[i + 1].isLowSurrogate()) 2 else 1
            count++
        }
        return count
    }

    private fun hasUnpairedSurrogate(text: String): Boolean {
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c.isHighSurrogate() && i + 1 < text.length && text[i + 1].isLowSurrogate() -> i += 2
                c.isSurrogate() -> return true
                else -> i++
            }
        }
        return false
    }
}

/**
 * `POST /support/messages`: signed-in only, five a day per account (refusals and replays
 * included), `429 RATE_LIMITED` past that.
 *
 * [idempotencyKey] is minted by the caller for one message and reused on every retry of
 * it, so a retry after a lost answer is the same ticket, not a second one.
 *
 * Subject and body are what a person wrote to a person: never logged, never cached.
 */
interface SupportRepository {
    suspend fun send(subject: String, body: String, idempotencyKey: String): Result<SupportTicket, DataError.Network>
}
