package com.moyi.app.domain.archive

import com.moyi.app.core.DataError
import com.moyi.app.core.Error
import com.moyi.app.core.Result

/**
 * "Remember when" (FR-204, ADR-0054 decision 8; `GET /bonds/{bondId}/remember`): the days of
 * a bond closest to a question, most relevant first, in the archive's day shape.
 *
 * **One answer, not a feed**: there is no cursor, and it may hold fewer days than asked for,
 * or none. The server ranks by the question's words and, when the bond's search by meaning is
 * on and a provider answers in time, by its meaning too; the client cannot tell which, and
 * renders one state. My own entry can be found before it is revealed; the partner's only once
 * it has been (the server decides; nothing is matched on the device).
 *
 * **Never stored** (the contract): no cache, no database, no saved state. The question is the
 * person's own words, so it never reaches a log either (the HTTP log cuts every query string).
 */
interface RememberRepository {
    /**
     * The days closest to [question], at most [REMEMBER_LIMIT]. [question] is the trimmed text,
     * 1 to [REMEMBER_MAX_LENGTH] characters (the caller caps the field).
     *
     * A `429` is [RememberError.RateLimited] with the wait the server gave. Everything else that
     * is not a `200` (`403`, `404`, a `422` the field should have made impossible, `5xx`, no
     * connection, a body that does not parse) is [RememberError.Failed]: one generic error, and
     * no cache to fall back to.
     */
    suspend fun remember(bondId: String, question: String): Result<List<ArchiveDay>, RememberError>
}

/** Why a question got no answer. Only these two are told apart (owner ruling 2026-11-18). */
sealed interface RememberError : Error {
    /**
     * `429 RATE_LIMITED`: thirty questions an hour per account. Ask is held for
     * [retryAfterSeconds], the server's `Retry-After` (whole seconds, at least 1).
     */
    data class RateLimited(val retryAfterSeconds: Int) : RememberError

    /** Any other failure, shown as the generic error. [cause] is for tests and debugging only, never shown. */
    data class Failed(val cause: DataError.Network) : RememberError
}

/** How many days a question asks for (owner ruling 2026-11-18: ten, not the contract's 20 maximum). */
const val REMEMBER_LIMIT = 10

/** The question's cap: the same 100 characters as Search's `q`, and for the same reasons ([SEARCH_MAX_LENGTH]). */
const val REMEMBER_MAX_LENGTH = SEARCH_MAX_LENGTH
