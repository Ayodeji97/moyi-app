package com.moyi.app.feature.today

import app.cash.turbine.test
import com.moyi.app.feature.today.common.FakeDailyLoop
import com.moyi.app.feature.today.common.TodayStatus
import com.moyi.app.feature.today.write.WriteAction
import com.moyi.app.feature.today.write.WriteEvent
import com.moyi.app.feature.today.write.WriteState
import com.moyi.app.feature.today.write.WriteSubmit
import com.moyi.app.feature.today.write.WriteViewModel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration

class WriteViewModelTest : MainDispatcherTest() {
    private val loop = FakeDailyLoop()
    private fun viewModel(editing: Boolean = false) = WriteViewModel(editing, loop, fakeLatency = Duration.ZERO)

    @Test
    fun typingUpdatesTheTextAndTheCounterAppearsOnlyPast400() {
        val vm = viewModel()
        vm.onAction(WriteAction.OnTextChange("a".repeat(400)))
        assertEquals(400, vm.state.value.text.length)
        assertFalse(vm.state.value.showCounter)

        vm.onAction(WriteAction.OnTextChange("a".repeat(401)))
        assertTrue(vm.state.value.showCounter)
        assertFalse(vm.state.value.overLimit)
        assertTrue(vm.state.value.canSubmit)
    }

    @Test
    fun over500IsInvalidAndBlankCannotBeSent() {
        assertFalse(WriteState(text = "   ").canSubmit)
        assertFalse(WriteState(text = "").canSubmit)
        val over = WriteState(text = "a".repeat(501))
        assertTrue(over.overLimit)
        assertFalse(over.canSubmit)
        assertTrue(WriteState(text = "a".repeat(500)).canSubmit)
    }

    @Test
    fun dismissingThePromptRemovesIt() {
        val vm = viewModel()
        vm.onAction(WriteAction.OnDismissPrompt)
        assertNull(vm.state.value.prompt)
    }

    @Test
    fun sendingMovesTodayToWaitingWithTheWordsAndGoesBack() = runTest {
        val vm = viewModel()
        vm.events.test {
            vm.onAction(WriteAction.OnTextChange("You made the tea."))
            vm.onAction(WriteAction.OnSubmit)
            assertEquals(WriteEvent.NavigateBack, awaitItem())
        }
        assertEquals(TodayStatus.Waiting, loop.day.value.status)
        assertEquals("You made the tea.", loop.day.value.myEntry)
    }

    @Test
    fun blankSendsNothing() = runTest {
        val vm = viewModel()
        vm.events.test {
            vm.onAction(WriteAction.OnSubmit)
            expectNoEvents()
        }
        assertEquals(WriteSubmit.Idle, vm.state.value.submit)
        assertEquals(TodayStatus.NotWritten, loop.day.value.status)
    }

    @Test
    fun editingStartsFromWhatIWroteWithNoPrompt() {
        loop.setMyEntry("Already written.", TodayStatus.Waiting)
        val vm = viewModel(editing = true)
        assertEquals("Already written.", vm.state.value.text)
        assertTrue(vm.state.value.editing)
        assertNull(vm.state.value.prompt)
    }

    @Test
    fun typingAfterAFailureClearsIt() {
        val vm = viewModel()
        // The debug switch reaches save failed at step 5.
        repeat(5) { vm.onAction(WriteAction.OnDebugCycle) }
        assertEquals(WriteSubmit.Failed, vm.state.value.submit)
        vm.onAction(WriteAction.OnTextChange("${vm.state.value.text} more"))
        assertEquals(WriteSubmit.Idle, vm.state.value.submit)
    }

    @Test
    fun editsWhileSendingAreIgnored() {
        val vm = viewModel()
        repeat(3) { vm.onAction(WriteAction.OnDebugCycle) } // 419:193 sending
        val sending = vm.state.value
        vm.onAction(WriteAction.OnTextChange("changed"))
        assertEquals(sending, vm.state.value)
    }

    @Test
    fun theDebugSwitchVisitsEachDrawnComposerStateAndWraps() {
        val vm = viewModel()
        val seen = (1..7).map {
            vm.onAction(WriteAction.OnDebugCycle)
            vm.state.value
        }
        assertTrue(seen[0].showCounter && !seen[0].overLimit) // 181:43
        assertTrue(seen[1].overLimit) // 181:83
        assertEquals(WriteSubmit.Sending, seen[2].submit) // 419:193
        assertTrue(seen[3].editing) // 419:291
        assertEquals(WriteSubmit.Failed, seen[4].submit) // 384:229
        assertEquals(WriteSubmit.Queued, seen[5].submit)
        assertEquals(WriteState(), seen[6]) // back to 181:3
    }
}
