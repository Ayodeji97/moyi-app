package com.moyi.app.domain.streak

import com.moyi.app.core.DataError
import com.moyi.app.core.Result

/**
 * The bond's streak, as the server counts it (`GET /bonds/{bondId}/streak`, ADR-0034).
 * **Server-authoritative**: nothing here is computed or incremented on the device
 * (states.md §7). [days] is one cell per date, oldest first, never a date after today.
 *
 * @property current the run ending today (or yesterday, while today is still open).
 * @property longest the longest run ever. Not the run that just ended (states.md §7a, gap).
 * @property freezesAvailable rest days banked; kept, unspent, while [strictMode] is on.
 * @property freezeProgress complete days towards the next rest day. Not drawn.
 * @property totalCompleteDays every complete day of the bond's life. Not drawn.
 * @property lastCompleteDate ISO date (`YYYY-MM-DD`) of the last complete day, if any.
 */
data class Streak(
    val current: Int,
    val longest: Int,
    val freezesAvailable: Int,
    val freezeProgress: Int,
    val strictMode: Boolean,
    val totalCompleteDays: Int,
    val lastCompleteDate: String?,
    val days: List<StreakDay>,
)

/** One calendar cell: [date] in ISO form, and what the day was **to the streak**. */
data class StreakDay(val date: String, val status: StreakDayStatus)

/** The contract's five cell values (states.md §7a). */
enum class StreakDayStatus {
    /** Both wrote. */
    COMPLETE,

    /** A rest day covered it, or a timezone change stepped over it: the payload does not say which. */
    FROZEN,

    /** One of the two wrote, and no rest day covered it. */
    SOLO,

    /** Neither wrote, and no rest day covered it. */
    MISSED,

    /** Today, until both have written. */
    OPEN,
}

interface StreakRepository {
    /** The streak of [bondId]. Online only; the response is read fresh each time the screen opens. */
    suspend fun streak(bondId: String): Result<Streak, DataError.Network>
}
