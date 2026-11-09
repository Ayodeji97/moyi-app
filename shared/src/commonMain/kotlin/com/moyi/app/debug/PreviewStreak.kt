package com.moyi.app.debug

import com.moyi.app.core.DataError
import com.moyi.app.core.Result
import com.moyi.app.domain.streak.FrozenReason
import com.moyi.app.domain.streak.Streak
import com.moyi.app.domain.streak.StreakDay
import com.moyi.app.domain.streak.StreakDayStatus
import com.moyi.app.domain.streak.StreakRepository
import com.moyi.app.feature.today.common.FakeCopy
import kotlinx.coroutines.delay
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.minus
import kotlin.time.Duration.Companion.milliseconds

/**
 * DEBUG ONLY. The streak the Today preview runs on (`-PmoyiUiPreview=today`): the four drawn
 * states, then a failure, in turn. **Each call answers with the next one**, after 800 ms so
 * the bare surface can be seen: opening the screen shows the current streak, and a long
 * press on its title (or Try again on the failure) asks again.
 *
 * 1. **Current** (227:3): 22 now, 41 longest, two rest days, one of them used ten days ago.
 * 2. **Rest day used** (401:70): yesterday FROZEN, one left.
 * 3. **Ended** (401:160): yesterday missed, none left; a solo day four weeks back, drawn blank.
 *    The run it ended was 21 days (`lastRunLength`), counted from the cells.
 * 4. **Strict mode** (401:250): 11 now; the rest day taken before it was switched on stays a
 *    rest day (FR-073), and the banked one is kept.
 * 5. **Time zone shift** (no frame, docs/missing-components.md §59): yesterday FROZEN because
 *    the shared time zone changed (`TIMEZONE_SHIFT`); 22 now, both rest days still banked.
 * 6. The Generic error.
 *
 * Every number agrees with its cells, walking back from yesterday and counting complete plus
 * rest days, as states.md §7 checks the frames: 22, 22, 0 (after a run of 21), 11, 22. Bound over the real repository
 * only in that build (KoinInit).
 */
internal class PreviewStreakRepository : StreakRepository {
    private var next = 0

    override suspend fun streak(bondId: String): Result<Streak, DataError.Network> {
        delay(800.milliseconds)
        val step = next
        next = (next + 1) % STEPS
        return when (step) {
            0 -> Result.Success(streak(current = 22, freezes = 2, strict = false) { i -> if (i == 17) StreakDayStatus.FROZEN else if (i == 4) StreakDayStatus.MISSED else null })
            1 -> Result.Success(streak(current = 22, freezes = 1, strict = false) { i -> if (i == 26) StreakDayStatus.FROZEN else if (i == 4) StreakDayStatus.MISSED else null })
            2 -> Result.Success(
                streak(current = 0, freezes = 0, strict = false) { i -> if (i == 26) StreakDayStatus.MISSED else if (i == 4) StreakDayStatus.SOLO else null }
                    .let { it.copy(lastRunLength = 21, lastRunBrokenOn = it.days[26].date) },
            )
            3 -> Result.Success(streak(current = 11, freezes = 1, strict = true) { i -> if (i == 15) StreakDayStatus.MISSED else if (i == 8) StreakDayStatus.FROZEN else null })
            4 -> Result.Success(
                streak(current = 22, freezes = 2, strict = false, timezoneShiftAt = 26) { i -> if (i == 26) StreakDayStatus.FROZEN else if (i == 4) StreakDayStatus.MISSED else null },
            )
            else -> Result.Failure(DataError.Network.Serialization)
        }
    }

    /**
     * Twenty-eight cells, today (index 27) open; [override] changes a cell, the rest are complete.
     * A FROZEN cell is a rest day, except the one at [timezoneShiftAt], which a time zone change stepped over.
     */
    private fun streak(
        current: Int,
        freezes: Int,
        strict: Boolean,
        timezoneShiftAt: Int? = null,
        override: (Int) -> StreakDayStatus?,
    ): Streak {
        val days = (0 until 28).map { i ->
            val date = FakeCopy.DATE.minus(DatePeriod(days = 27 - i)).toString()
            val status = if (i == 27) StreakDayStatus.OPEN else override(i) ?: StreakDayStatus.COMPLETE
            val reason = when {
                status != StreakDayStatus.FROZEN -> null
                i == timezoneShiftAt -> FrozenReason.TIMEZONE_SHIFT
                else -> FrozenReason.REST_DAY
            }
            StreakDay(date, status, reason)
        }
        return Streak(
            current = current,
            longest = 41,
            freezesAvailable = freezes,
            freezeProgress = 3,
            strictMode = strict,
            totalCompleteDays = 112,
            lastCompleteDate = days.lastOrNull { it.status == StreakDayStatus.COMPLETE }?.date,
            days = days,
        )
    }

    private companion object {
        const val STEPS = 6
    }
}
