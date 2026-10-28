package com.moyi.app.feature.today

import app.cash.turbine.test
import com.moyi.app.feature.today.common.FakeDailyLoop
import com.moyi.app.feature.today.common.TodayStatus
import com.moyi.app.feature.today.delete.DeleteEntryAction
import com.moyi.app.feature.today.delete.DeleteEntryEvent
import com.moyi.app.feature.today.delete.DeleteEntryViewModel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

class DeleteEntryViewModelTest : MainDispatcherTest() {
    private val loop = FakeDailyLoop().apply { setStatus(TodayStatus.Revealed) }

    @Test
    fun confirmingLeavesTheTombstoneAndGoesBack() = runTest {
        val vm = DeleteEntryViewModel(loop, fakeLatency = Duration.ZERO)
        vm.events.test {
            vm.onAction(DeleteEntryAction.OnConfirmClick)
            assertEquals(DeleteEntryEvent.NavigateBack, awaitItem())
        }
        assertEquals(TodayStatus.Tombstone, loop.day.value.status)
    }

    @Test
    fun whileDeletingTheConfirmIsNotSentTwice() = runTest {
        // Irreversible, so not optimistic: Today stays revealed until the delete lands.
        val vm = DeleteEntryViewModel(loop, fakeLatency = 1.hours)
        vm.events.test {
            vm.onAction(DeleteEntryAction.OnConfirmClick)
            assertTrue(vm.state.value.deleting)
            vm.onAction(DeleteEntryAction.OnConfirmClick)
            expectNoEvents()
        }
        assertEquals(TodayStatus.Revealed, loop.day.value.status)
    }

    @Test
    fun cancellingGoesBackAndDeletesNothing() = runTest {
        val vm = DeleteEntryViewModel(loop, fakeLatency = Duration.ZERO)
        vm.events.test {
            vm.onAction(DeleteEntryAction.OnCancelClick)
            assertEquals(DeleteEntryEvent.NavigateBack, awaitItem())
        }
        assertFalse(vm.state.value.deleting)
        assertEquals(TodayStatus.Revealed, loop.day.value.status)
    }
}
