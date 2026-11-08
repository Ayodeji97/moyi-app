package com.moyi.app.domain.today

import com.moyi.app.domain.bond.Bond

/**
 * The last day Today showed, kept on this device so the next launch can draw it at
 * once and refresh behind it (doc 13 §5: cache-then-network; NFR-010).
 *
 * Scoped to the person signed in: [read] never returns another person's day, and
 * the whole store is wiped whenever a session ends or starts. The server always
 * wins: every successful fetch replaces what is here.
 *
 * What is written is the caller's to make safe: the daily loop strips the partner's
 * words from every layout not allowed to show them before it writes.
 */
interface TodayCache {
    /** The signed-in person's cached day, or null when there is none (or nobody is signed in). */
    suspend fun read(): CachedDay?

    /** Replaces the cached day. Does nothing when nobody is signed in. */
    suspend fun write(day: CachedDay)

    /** Forgets the cached day (the person is in no open bond any more). */
    suspend fun clear()
}

/** A day and the bond it belongs to: everything Today needs to draw it. The bond's invite is never kept. */
data class CachedDay(
    val bond: Bond,
    val today: Today,
)
