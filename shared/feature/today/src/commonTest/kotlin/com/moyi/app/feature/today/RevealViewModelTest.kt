package com.moyi.app.feature.today

import app.cash.turbine.test
import com.moyi.app.feature.today.common.FakeCopy
import com.moyi.app.feature.today.common.FakeDailyLoop
import com.moyi.app.feature.today.common.TodayStatus
import com.moyi.app.feature.today.reveal.RevealAction
import com.moyi.app.feature.today.reveal.RevealEvent
import com.moyi.app.feature.today.reveal.RevealPhase
import com.moyi.app.feature.today.reveal.RevealViewModel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

class RevealViewModelTest : MainDispatcherTest() {
    private val loop = FakeDailyLoop().apply { setStatus(TodayStatus.Waiting) }

    @Test
    fun opensOnTheBeforeFrameWithoutThePartnersWords() {
        val vm = RevealViewModel(loop, beforeHold = 1.hours, afterHold = 1.hours)
        assertEquals(RevealPhase.Before, vm.state.value.phase)
        assertNull(vm.state.value.partnerEntry)
    }

    @Test
    fun turnsAndSettlesTodayIntoRevealed() = runTest {
        val vm = RevealViewModel(loop, beforeHold = Duration.ZERO, afterHold = Duration.ZERO)
        vm.events.test {
            assertEquals(RevealEvent.Settle, awaitItem())
        }
        assertEquals(RevealPhase.After, vm.state.value.phase)
        assertEquals(FakeCopy.PARTNER_ENTRY, vm.state.value.partnerEntry)
        assertEquals(TodayStatus.Revealed, loop.day.value.status)
    }

    @Test
    fun backBeforeTheTurnSettlesIntoRevealedAtOnce() = runTest {
        // D12: Back ends the moment early, into Today · revealed, never out of the app.
        val vm = RevealViewModel(loop, beforeHold = 1.hours, afterHold = 1.hours)
        vm.events.test {
            vm.onAction(RevealAction.OnBack)
            assertEquals(RevealEvent.Settle, awaitItem())
            expectNoEvents()
        }
        assertEquals(TodayStatus.Revealed, loop.day.value.status)
        assertEquals(RevealPhase.Before, vm.state.value.phase, "settled early: the turn never played")
    }

    @Test
    fun aSecondBackSettlesOnlyOnce() = runTest {
        val vm = RevealViewModel(loop, beforeHold = 1.hours, afterHold = 1.hours)
        vm.events.test {
            vm.onAction(RevealAction.OnBack)
            vm.onAction(RevealAction.OnBack)
            vm.onAction(RevealAction.OnDebugReplay)
            assertEquals(RevealEvent.Settle, awaitItem())
            expectNoEvents()
        }
    }
}
