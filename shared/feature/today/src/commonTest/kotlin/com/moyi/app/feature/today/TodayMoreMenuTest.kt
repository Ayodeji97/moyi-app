package com.moyi.app.feature.today

import com.moyi.app.feature.today.common.RevealLedger
import com.moyi.app.feature.today.common.TodayStatus
import com.moyi.app.feature.today.resources.Res
import com.moyi.app.feature.today.resources.today_both_wrote_title
import com.moyi.app.feature.today.today.OwnEntryMenuItem
import com.moyi.app.feature.today.today.TodayAction
import com.moyi.app.feature.today.today.TodayState
import com.moyi.app.feature.today.today.TodayViewModel
import com.moyi.app.feature.today.today.actionFor
import com.moyi.app.feature.today.today.ownEntryMenu
import com.moyi.app.feature.today.today.titleResource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The "More" menu on your own entry card (owner ruling 2026-11-11): what it offers, and that it bypasses nothing. */
@OptIn(ExperimentalCoroutinesApi::class)
class TodayMoreMenuTest : MainDispatcherTest() {
    private val loop = FakeDailyLoop()
    private val viewModel by lazy { TodayViewModel(loop, RevealLedger { "me" }, InviteBonds(), FakeOutbox()) }

    private fun TestScope.collectState() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
    }

    private fun menu(status: TodayStatus) = TodayState(status = status).ownEntryMenu()

    @Test
    fun beforeTheRevealTheMenuHoldsEditThenDelete() {
        assertEquals(listOf(OwnEntryMenuItem.Edit, OwnEntryMenuItem.Delete), menu(TodayStatus.Waiting))
    }

    @Test
    fun afterTheRevealTheMenuHoldsDeleteOnly() {
        assertEquals(listOf(OwnEntryMenuItem.Delete), menu(TodayStatus.Revealed))
    }

    @Test
    fun whereThereIsNoOwnEntryToChangeThereIsNoMenu() {
        val none = TodayStatus.entries - setOf(TodayStatus.Waiting, TodayStatus.Revealed)
        none.forEach { assertTrue(menu(it).isEmpty(), "$it") }
    }

    @Test
    fun menuItemsDispatchTheActionsTheButtonsDid() {
        assertEquals(TodayAction.OnEditClick, TodayState(status = TodayStatus.Waiting).actionFor(OwnEntryMenuItem.Edit))
        assertEquals(TodayAction.OnDeleteBeforeRevealClick, TodayState(status = TodayStatus.Waiting).actionFor(OwnEntryMenuItem.Delete))
        assertEquals(TodayAction.OnDeleteAfterRevealClick, TodayState(status = TodayStatus.Revealed).actionFor(OwnEntryMenuItem.Delete))
    }

    @Test
    fun deleteFromTheMenuBeforeTheRevealStillAsksFirst() = runTest {
        collectState()
        loop.show(dayView(TodayStatus.Waiting))
        val state = viewModel.state.value
        viewModel.onAction(state.actionFor(OwnEntryMenuItem.Delete))
        assertTrue(viewModel.state.value.confirmingDeleteBeforeReveal)
        assertEquals(0, loop.deletesBefore)
        viewModel.onAction(TodayAction.OnDeleteDismiss)
        assertFalse(viewModel.state.value.confirmingDeleteBeforeReveal)
        assertEquals(0, loop.deletesBefore)
        assertEquals(TodayStatus.Waiting, viewModel.state.value.status)
    }

    @Test
    fun deleteFromTheMenuAfterTheRevealStillAsksFirst() = runTest {
        collectState()
        loop.show(dayView(TodayStatus.Revealed))
        viewModel.onAction(viewModel.state.value.actionFor(OwnEntryMenuItem.Delete))
        assertTrue(viewModel.state.value.confirmingDelete)
        assertEquals(0, loop.deletesAfter)
    }

    @Test
    fun theMenuFollowsTheLoadedDay() = runTest {
        collectState()
        loop.show(dayView(TodayStatus.Waiting))
        assertEquals(2, viewModel.state.value.ownEntryMenu().size)
        loop.show(dayView(TodayStatus.Revealed))
        assertEquals(listOf(OwnEntryMenuItem.Delete), viewModel.state.value.ownEntryMenu())
    }

    @Test
    fun theSettledTitleSaysBothWroteOnARevealedDayOnly() {
        assertEquals(Res.string.today_both_wrote_title, TodayStatus.Revealed.titleResource())
        assertEquals(Res.string.today_both_wrote_title, TodayStatus.Tombstone.titleResource())
        assertNull(TodayStatus.Waiting.titleResource())
        assertNull(TodayStatus.NotWritten.titleResource())
    }
}
