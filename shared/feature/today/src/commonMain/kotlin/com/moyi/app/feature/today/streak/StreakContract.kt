package com.moyi.app.feature.today.streak

import androidx.compose.runtime.Immutable
import com.moyi.app.designsystem.component.DayCellState
import com.moyi.app.domain.streak.FrozenReason
import com.moyi.app.domain.streak.Streak
import com.moyi.app.domain.streak.StreakDayStatus
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

enum class StreakLoad {
    /** The bare `surface`, no spinner (CLAUDE.md). */
    Loading,
    Ready,

    /** The Generic error StateBlock, with Try again. */
    Failed,
}

@Immutable
data class StreakState(
    val load: StreakLoad = StreakLoad.Loading,
    val streak: StreakUi? = null,
)

/**
 * The Streak screen as drawn (Figma 227:3, 401:70, 401:160, 401:250). Every number is the
 * server's ([Streak.current], [Streak.longest]); nothing is counted on the device.
 *
 * @property cells the rolling window ending on the server's last cell (today), oldest first:
 *   **never a cell after today** (states.md §7).
 * @property showRestDayKey false in Strict mode: a key for a state that cannot occur explains
 *   something that is not there (states.md §7).
 */
@Immutable
data class StreakUi(
    val current: Int,
    val longest: Int,
    val cells: List<StreakCell>,
    val showRestDayKey: Boolean,
    val footer: StreakFooter,
)

/**
 * @property skippedByTimezone a [DayCellState.Frozen] cell that a time zone change stepped over
 *   rather than a rest day covered. Drawn with the same shape (no fifth shape is designed,
 *   docs/missing-components.md §59); only what a screen reader hears differs.
 */
@Immutable
data class StreakCell(val date: LocalDate, val state: DayCellState, val skippedByTimezone: Boolean = false)

/** The one line under the key. Which one is decided from the payload, in this order. */
sealed interface StreakFooter {
    /**
     * `current` is 0 and the server sent the run the break ended (401:160): "Your streak ended at
     * [length] days. That is [length] days of showing up." [strict] says why: Strict mode, or no
     * rest day was left.
     */
    data class EndedAt(val length: Int, val strict: Boolean) : StreakFooter

    /** `current` is 0 after a run, but no ended run was sent: the same line without the number. */
    data class Ended(val strict: Boolean) : StreakFooter

    /** Yesterday is FROZEN because the shared time zone changed and stepped over it, and the run goes on. */
    data object TimezoneShift : StreakFooter

    /** Strict mode is on (401:250): "Rest days are off." */
    data object StrictMode : StreakFooter

    /** Yesterday is FROZEN by a rest day (401:70): a rest day was used, [left] remain. */
    data class RestDayUsed(val left: Int) : StreakFooter

    /** The ordinary day (227:3): [left] rest days banked. */
    data class RestDaysLeft(val left: Int) : StreakFooter
}

sealed interface StreakAction {
    data object OnBackClick : StreakAction
    data object OnRetryClick : StreakAction

    /** `-PmoyiUiPreview=today` only: a long press on the title asks again, and the preview steps to its next state. */
    data object OnDebugCycle : StreakAction
}

sealed interface StreakEvent {
    data object NavigateBack : StreakEvent
}

/** "The last four weeks" (Figma 267:38): four rows of seven. */
internal const val STREAK_WINDOW_DAYS = 28

/**
 * The payload as the screen draws it, or null if a date does not parse (the caller shows
 * the error rather than a calendar with holes it did not mean).
 *
 * - The window ends on the **last cell** the server sent, which is today while the bond
 *   takes writes (states.md §7a), and reaches back [STREAK_WINDOW_DAYS] days, or to the
 *   first cell if the bond is younger: the grid starts at the first cell.
 * - A date inside the window with no cell (a deletion called off, a day not yet evaluated)
 *   is drawn blank, as a day with no entry.
 * - `SOLO` is drawn blank too: its shape is not drawn yet (states.md §7, DayCell).
 * - `OPEN` is the Today ring. A complete today is drawn complete.
 * - The footer: an ended run is said with its length when the server sent it
 *   (`lastRunLength`, ADR-0034); a yesterday a time zone change stepped over is said as that,
 *   never as a rest day used; a frozen day whose reason is unknown gets the ordinary line.
 */
internal fun Streak.toStreakUi(): StreakUi? {
    val parsed = days.map { day -> (runCatching { LocalDate.parse(day.date) }.getOrNull() ?: return null) to day.status }
    val byDate = parsed.toMap()
    // A reason means something only on a FROZEN cell (the contract sends null on every other).
    val reasons = days.zip(parsed) { day, (date, status) -> date to day.reason.takeIf { status == StreakDayStatus.FROZEN } }.toMap()
    val cells = parsed.lastOrNull()?.let { (end, _) ->
        val first = parsed.first().first
        val windowStart = end.minus(DatePeriod(days = STREAK_WINDOW_DAYS - 1))
        val start = if (first > windowStart) first else windowStart
        generateSequence(start) { it.plusOneDay() }
            .takeWhile { it <= end }
            .map { date ->
                StreakCell(date, byDate[date].toDayCellState(), skippedByTimezone = reasons[date] == FrozenReason.TIMEZONE_SHIFT)
            }
            .toList()
    }.orEmpty()
    val yesterday = parsed.lastOrNull()?.first?.minus(DatePeriod(days = 1))
    val yesterdayReason = yesterday?.let { reasons[it] }
    val endedRun = lastRunLength
    val footer = when {
        current == 0 && endedRun != null -> StreakFooter.EndedAt(endedRun, strict = strictMode)
        current == 0 && longest > 0 -> StreakFooter.Ended(strict = strictMode)
        // Before Strict mode: a frozen yesterday under "Rest days are off" would read as a contradiction.
        current > 0 && yesterdayReason == FrozenReason.TIMEZONE_SHIFT -> StreakFooter.TimezoneShift
        strictMode -> StreakFooter.StrictMode
        yesterdayReason == FrozenReason.REST_DAY -> StreakFooter.RestDayUsed(freezesAvailable)
        // FrozenReason.UNKNOWN says nothing about why: the ordinary line, never a guess.
        else -> StreakFooter.RestDaysLeft(freezesAvailable)
    }
    return StreakUi(
        current = current,
        longest = longest,
        cells = cells,
        showRestDayKey = !strictMode,
        footer = footer,
    )
}

private fun LocalDate.plusOneDay(): LocalDate = LocalDate.fromEpochDays(toEpochDays() + 1)

private fun StreakDayStatus?.toDayCellState(): DayCellState = when (this) {
    StreakDayStatus.COMPLETE -> DayCellState.Complete
    // FR-073: a day keeps the cell it was given, in Strict mode too.
    StreakDayStatus.FROZEN -> DayCellState.Frozen
    StreakDayStatus.OPEN -> DayCellState.Today
    StreakDayStatus.SOLO, StreakDayStatus.MISSED, null -> DayCellState.Missed
}
