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
 * @property lastRunLength the length of the run the latest break ended, or null while no run
 *   has ended (ADR-0034 amendment, decision 1). It stays set while the next run grows, so it
 *   says "your streak ended at N days" only when [current] is 0. At least 1, never above [longest].
 * @property lastRunBrokenOn ISO date of the missed day that ended that run (not part of it);
 *   null exactly when [lastRunLength] is.
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
    val lastRunLength: Int? = null,
    val lastRunBrokenOn: String? = null,
)

/**
 * One calendar cell: [date] in ISO form, and what the day was **to the streak**.
 *
 * @property reason why a [StreakDayStatus.FROZEN] cell is frozen, and null on every other cell.
 */
data class StreakDay(val date: String, val status: StreakDayStatus, val reason: FrozenReason? = null)

/** Why a day is [StreakDayStatus.FROZEN] (the contract's `StreakDayResponse.reason`). */
enum class FrozenReason {
    /** Neither or only one wrote, and a banked rest day covered it: "we used a rest day". */
    REST_DAY,

    /** An approved time zone change stepped over the date, so nobody could write on it. No rest day was spent (BR-6). */
    TIMEZONE_SHIFT,

    /**
     * A frozen day whose reason this app does not know: a value added to the contract after this
     * build, or none sent. Drawn as a frozen day, and nothing is said about why.
     */
    UNKNOWN,
}

/** The contract's five cell values (states.md §7a). */
enum class StreakDayStatus {
    /** Both wrote. */
    COMPLETE,

    /** A rest day covered it, or a time zone change stepped over it: [StreakDay.reason] says which. */
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
