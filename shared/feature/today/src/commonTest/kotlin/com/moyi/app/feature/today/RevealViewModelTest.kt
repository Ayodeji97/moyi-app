package com.moyi.app.feature.today

import app.cash.turbine.test
import com.moyi.app.feature.today.common.FakeDailyLoop
import com.moyi.app.feature.today.common.TodayStatus
import com.moyi.app.feature.today.reveal.RevealEvent
import com.moyi.app.feature.today.reveal.RevealPhase
import com.moyi.app.feature.today.reveal.RevealViewModel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

class RevealViewModelTest : MainDispatcherTest() {
    private val loop = FakeDailyLoop().apply { setStatus(TodayStatus.Waiting) }

    @Test
    fun opensOnTheBeforeFrame() {
        val vm = RevealViewModel(loop, beforeHold = 1.hours, afterHold = 1.hours)
        assertEquals(RevealPhase.Before, vm.state.value.phase)
    }

    @Test
    fun turnsAndSettlesTodayIntoRevealed() = runTest {
        val vm = RevealViewModel(loop, beforeHold = Duration.ZERO, afterHold = Duration.ZERO)
        vm.events.test {
            assertEquals(RevealEvent.Settle, awaitItem())
        }
        assertEquals(RevealPhase.After, vm.state.value.phase)
        assertEquals(TodayStatus.Revealed, loop.day.value.status)
    }
}
