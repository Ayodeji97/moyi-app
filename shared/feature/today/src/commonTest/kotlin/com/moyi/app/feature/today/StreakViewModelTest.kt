package com.moyi.app.feature.today

import com.moyi.app.core.DataError
import com.moyi.app.core.Result
import com.moyi.app.designsystem.component.DayCellState
import com.moyi.app.domain.streak.FrozenReason
import com.moyi.app.domain.streak.Streak
import com.moyi.app.domain.streak.StreakDay
import com.moyi.app.domain.streak.StreakDayStatus
import com.moyi.app.domain.streak.StreakRepository
import com.moyi.app.feature.today.common.DailyLoopState
import com.moyi.app.feature.today.common.TodayStatus
import com.moyi.app.feature.today.streak.StreakAction
import com.moyi.app.feature.today.streak.StreakEvent
import com.moyi.app.feature.today.streak.StreakFooter
import com.moyi.app.feature.today.streak.StreakLoad
import com.moyi.app.feature.today.streak.StreakViewModel
import com.moyi.app.feature.today.streak.StreakUi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Streak screen (states.md §7) over a fake repository: each drawn state chosen from the
 * contract's payload, the numbers shown exactly as sent, the rolling window never past the
 * server's last cell, Strict mode's key, the failure and Try again.
 */
class StreakViewModelTest : MainDispatcherTest() {
    private class FakeStreakRepository : StreakRepository {
        val answers = ArrayDeque<Result<Streak, DataError.Network>>()
        val asked = mutableListOf<String>()
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun streak(bondId: String): Result<Streak, DataError.Network> {
            asked += bondId
            gate?.await()
            return answers.removeFirst()
        }
    }

    private val loop = FakeDailyLoop(DailyLoopState.Ready(dayView(TodayStatus.Revealed)))
    private val repository = FakeStreakRepository()

    private fun open(vararg answers: Result<Streak, DataError.Network>): StreakViewModel {
        repository.answers.addAll(answers)
        return StreakViewModel(repository, loop)
    }

    private fun StreakViewModel.ui(): StreakUi = state.value.streak ?: error("not ready: ${state.value.load}")

    /**
     * [count] cells ending on [DAY] (today, OPEN), oldest first; [at] sets a cell by its distance
     * back from today. A FROZEN cell is a rest day unless [reasons] gives it another reason.
     */
    private fun streak(
        current: Int = 22,
        longest: Int = 41,
        freezes: Int = 2,
        strict: Boolean = false,
        count: Int = 28,
        at: Map<Int, StreakDayStatus> = emptyMap(),
        reasons: Map<Int, FrozenReason> = emptyMap(),
        lastRunLength: Int? = null,
    ) = Streak(
        current = current,
        longest = longest,
        freezesAvailable = freezes,
        freezeProgress = 0,
        strictMode = strict,
        totalCompleteDays = 57,
        lastCompleteDate = null,
        days = (count - 1 downTo 0).map { back ->
            val status = at[back] ?: if (back == 0) StreakDayStatus.OPEN else StreakDayStatus.COMPLETE
            StreakDay(
                DAY.minus(DatePeriod(days = back)).toString(),
                status,
                if (status == StreakDayStatus.FROZEN) reasons[back] ?: FrozenReason.REST_DAY else null,
            )
        },
        lastRunLength = lastRunLength,
        lastRunBrokenOn = lastRunLength?.let { DAY.minus(DatePeriod(days = 1)).toString() },
    )

    // ---- The four drawn states ----

    @Test
    fun theCurrentStreakShowsTheServersNumbersFourWeeksEndingTodayAndTheRestDaysLeft() = runTest {
        val viewModel = open(Result.Success(streak(at = mapOf(10 to StreakDayStatus.FROZEN))))

        val ui = viewModel.ui()
        assertEquals(StreakLoad.Ready, viewModel.state.value.load)
        assertEquals(22, ui.current)
        assertEquals(41, ui.longest)
        assertEquals(28, ui.cells.size)
        assertEquals(DAY, ui.cells.last().date)
        assertEquals(DayCellState.Today, ui.cells.last().state)
        assertEquals(DayCellState.Frozen, ui.cells[27 - 10].state)
        assertTrue(ui.showRestDayKey)
        assertEquals(StreakFooter.RestDaysLeft(2), ui.footer)
        assertEquals(listOf("bond-1"), repository.asked)
    }

    @Test
    fun yesterdayFrozenIsTheRestDayUsedWithTheRestDaysLeft() = runTest {
        val viewModel = open(Result.Success(streak(freezes = 1, at = mapOf(1 to StreakDayStatus.FROZEN))))
        assertEquals(StreakFooter.RestDayUsed(1), viewModel.ui().footer)
    }

    @Test
    fun aZeroAfterARunWithNoEndedRunSentIsTheStreakEndedWithoutANumber() = runTest {
        val viewModel = open(Result.Success(streak(current = 0, freezes = 0, at = mapOf(1 to StreakDayStatus.MISSED))))

        val ui = viewModel.ui()
        assertEquals(StreakFooter.Ended(strict = false), ui.footer)
        assertEquals(0, ui.current)
        assertEquals(41, ui.longest)
        assertEquals(DayCellState.Missed, ui.cells[26].state)
    }

    @Test
    fun strictModeSaysRestDaysAreOffDropsTheirKeyAndKeepsAPastRestDay() = runTest {
        val viewModel = open(Result.Success(streak(current = 11, freezes = 1, strict = true, at = mapOf(1 to StreakDayStatus.FROZEN, 12 to StreakDayStatus.MISSED))))

        val ui = viewModel.ui()
        assertEquals(StreakFooter.StrictMode, ui.footer)
        assertFalse(ui.showRestDayKey)
        // FR-073: switching Strict mode on never changes a past day's cell.
        assertEquals(DayCellState.Frozen, ui.cells[26].state)
    }

    @Test
    fun anEndedStreakInStrictModeGivesStrictModeAsTheReason() = runTest {
        val viewModel = open(Result.Success(streak(current = 0, freezes = 1, strict = true, at = mapOf(1 to StreakDayStatus.MISSED))))
        assertEquals(StreakFooter.Ended(strict = true), viewModel.ui().footer)
    }

    // ---- The run that ended, and the time zone shift (ADR-0034: lastRunLength, reason) ----

    @Test
    fun anEndedStreakSaysHowLongTheRunThatEndedWas() = runTest {
        val viewModel = open(Result.Success(streak(current = 0, freezes = 0, at = mapOf(1 to StreakDayStatus.MISSED), lastRunLength = 21)))

        val ui = viewModel.ui()
        assertEquals(StreakFooter.EndedAt(length = 21, strict = false), ui.footer)
        // The ended run is not the longest: both numbers stay as sent.
        assertEquals(41, ui.longest)
        assertEquals(0, ui.current)
    }

    @Test
    fun anEndedStreakInStrictModeSaysTheLengthAndStrictMode() = runTest {
        val viewModel = open(Result.Success(streak(current = 0, freezes = 1, strict = true, at = mapOf(1 to StreakDayStatus.MISSED), lastRunLength = 1)))
        assertEquals(StreakFooter.EndedAt(length = 1, strict = true), viewModel.ui().footer)
    }

    @Test
    fun anEarlierEndedRunIsNotMentionedWhileTheNextRunGrows() = runTest {
        // lastRunLength stays set while the next run grows; the ended line is only for current 0.
        val viewModel = open(Result.Success(streak(current = 5, freezes = 1, lastRunLength = 21)))
        assertEquals(StreakFooter.RestDaysLeft(1), viewModel.ui().footer)
    }

    @Test
    fun yesterdaySkippedByATimezoneChangeIsSaidAsThatNotAsARestDayUsed() = runTest {
        val viewModel = open(
            Result.Success(
                streak(freezes = 2, at = mapOf(1 to StreakDayStatus.FROZEN), reasons = mapOf(1 to FrozenReason.TIMEZONE_SHIFT)),
            ),
        )

        val ui = viewModel.ui()
        assertEquals(StreakFooter.TimezoneShift, ui.footer)
        // The same shape as a rest day (no other is designed); a screen reader hears the difference.
        assertEquals(DayCellState.Frozen, ui.cells[26].state)
        assertTrue(ui.cells[26].skippedByTimezone)
    }

    @Test
    fun aTimezoneShiftInStrictModeIsSaidRatherThanRestDaysAreOff() = runTest {
        val viewModel = open(
            Result.Success(
                streak(current = 11, freezes = 1, strict = true, at = mapOf(1 to StreakDayStatus.FROZEN), reasons = mapOf(1 to FrozenReason.TIMEZONE_SHIFT)),
            ),
        )
        assertEquals(StreakFooter.TimezoneShift, viewModel.ui().footer)
    }

    @Test
    fun aTimezoneShiftWithNoRunToCarryOnIsNotSaidToCarryOn() = runTest {
        val viewModel = open(
            Result.Success(
                streak(current = 0, longest = 0, freezes = 0, count = 2, at = mapOf(1 to StreakDayStatus.FROZEN), reasons = mapOf(1 to FrozenReason.TIMEZONE_SHIFT)),
            ),
        )
        assertEquals(StreakFooter.RestDaysLeft(0), viewModel.ui().footer)
    }

    @Test
    fun anEarlierTimezoneShiftOnlyChangesWhatItsCellSays() = runTest {
        val viewModel = open(
            Result.Success(streak(at = mapOf(10 to StreakDayStatus.FROZEN), reasons = mapOf(10 to FrozenReason.TIMEZONE_SHIFT))),
        )
        val ui = viewModel.ui()
        assertEquals(StreakFooter.RestDaysLeft(2), ui.footer)
        assertTrue(ui.cells[27 - 10].skippedByTimezone)
        assertFalse(ui.cells.filterIndexed { i, _ -> i != 27 - 10 }.any { it.skippedByTimezone })
    }

    @Test
    fun yesterdayFrozenForAReasonThisBuildDoesNotKnowGetsTheOrdinaryLine() = runTest {
        val viewModel = open(
            Result.Success(streak(freezes = 1, at = mapOf(1 to StreakDayStatus.FROZEN), reasons = mapOf(1 to FrozenReason.UNKNOWN))),
        )
        val ui = viewModel.ui()
        assertEquals(StreakFooter.RestDaysLeft(1), ui.footer)
        assertEquals(DayCellState.Frozen, ui.cells[26].state)
        assertFalse(ui.cells[26].skippedByTimezone)
    }

    @Test
    fun aNewBondWithNothingYetIsNotAnEndedStreak() = runTest {
        val viewModel = open(Result.Success(streak(current = 0, longest = 0, freezes = 0, count = 1)))
        assertEquals(StreakFooter.RestDaysLeft(0), viewModel.ui().footer)
    }

    @Test
    fun theNumbersAreShownAsSentNeverCountedFromTheCells() = runTest {
        // Every cell complete, yet the server says 3: the screen says 3 (server-authoritative).
        val viewModel = open(Result.Success(streak(current = 3, longest = 3)))
        assertEquals(3, viewModel.ui().current)
    }

    // ---- The window ----

    @Test
    fun aLongHistoryShowsOnlyTheLastFourWeeksAndNoCellAfterTheLastOne() = runTest {
        val viewModel = open(Result.Success(streak(count = 371)))

        val cells = viewModel.ui().cells
        assertEquals(28, cells.size)
        assertEquals(DAY.minus(DatePeriod(days = 27)), cells.first().date)
        assertEquals(DAY, cells.last().date)
    }

    @Test
    fun aYoungBondsGridStartsAtItsFirstCell() = runTest {
        val viewModel = open(Result.Success(streak(count = 5)))
        assertEquals(DAY.minus(DatePeriod(days = 4)), viewModel.ui().cells.first().date)
        assertEquals(5, viewModel.ui().cells.size)
    }

    @Test
    fun aMissingDateInsideTheWindowAndASoloDayAreDrawnBlank() = runTest {
        val full = streak(at = mapOf(3 to StreakDayStatus.SOLO))
        val gap = DAY.minus(DatePeriod(days = 5)).toString()
        val viewModel = open(Result.Success(full.copy(days = full.days.filterNot { it.date == gap })))

        val cells = viewModel.ui().cells
        assertEquals(28, cells.size)
        assertEquals(DayCellState.Missed, cells.single { it.date == LocalDate.parse(gap) }.state)
        assertEquals(DayCellState.Missed, cells[27 - 3].state)
    }

    // ---- Loading, failure, retry, back ----

    @Test
    fun itIsLoadingUntilTheServerAnswers() = runTest {
        repository.gate = CompletableDeferred()
        val viewModel = open(Result.Success(streak()))
        assertEquals(StreakLoad.Loading, viewModel.state.value.load)
        assertNull(viewModel.state.value.streak)

        repository.gate?.complete(Unit)
        assertEquals(StreakLoad.Ready, viewModel.state.value.load)
    }

    @Test
    fun aFailureShowsTheErrorAndTryAgainAsksAgain() = runTest {
        val viewModel = open(Result.Failure(DataError.Network.NoInternet), Result.Success(streak()))
        assertEquals(StreakLoad.Failed, viewModel.state.value.load)

        viewModel.onAction(StreakAction.OnRetryClick)

        assertEquals(StreakLoad.Ready, viewModel.state.value.load)
        assertEquals(22, viewModel.ui().current)
        assertEquals(2, repository.asked.size)
    }

    @Test
    fun aDateThatDoesNotParseIsTheErrorNotACalendarWithAHole() = runTest {
        val viewModel = open(Result.Success(streak().copy(days = listOf(StreakDay("yesterday", StreakDayStatus.COMPLETE)))))
        assertEquals(StreakLoad.Failed, viewModel.state.value.load)
    }

    @Test
    fun withNoOpenBondThereIsNothingToAskAndTheErrorShows() = runTest {
        loop.state.value = DailyLoopState.NoOpenBond
        val viewModel = open()
        assertEquals(StreakLoad.Failed, viewModel.state.value.load)
        assertTrue(repository.asked.isEmpty())
    }

    @Test
    fun backNavigatesBack() = runTest {
        val viewModel = open(Result.Success(streak()))
        viewModel.onAction(StreakAction.OnBackClick)
        assertEquals(StreakEvent.NavigateBack, viewModel.events.first())
    }
}
