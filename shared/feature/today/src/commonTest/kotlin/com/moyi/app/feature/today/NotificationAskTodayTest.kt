package com.moyi.app.feature.today

import app.cash.turbine.test
import com.moyi.app.designsystem.platform.NotificationPermission
import com.moyi.app.designsystem.platform.NotificationPermissionController
import com.moyi.app.domain.bond.BondStatus
import com.moyi.app.domain.notification.NotificationAskMemory
import com.moyi.app.feature.today.common.RevealLedger
import com.moyi.app.feature.today.common.TodayStatus
import com.moyi.app.feature.today.today.TodayAction
import com.moyi.app.feature.today.today.TodayEvent
import com.moyi.app.feature.today.today.TodayViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** "Not now", remembered per phone: a fake of the platform's plain preferences. */
class FakeNotificationAskMemory(var dismissed: Boolean = false) : NotificationAskMemory {
    var dismissals = 0
        private set

    override fun isDismissed(): Boolean = dismissed

    override fun dismiss() {
        dismissals++
        dismissed = true
    }
}

/**
 * Asking for notifications on Today, once there is a reason (owner ruling 2026-11-13): after my
 * entry is saved in a bond someone has joined, while the phone has not been asked and "Not now"
 * has not been chosen on it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NotificationAskTodayTest : MainDispatcherTest() {
    private val loop = FakeDailyLoop()
    private val memory = FakeNotificationAskMemory()

    private fun newViewModel() = TodayViewModel(loop, RevealLedger { "me" }, InviteBonds(), FakeOutbox(), memory)

    private val viewModel by lazy { newViewModel() }

    /** A day in a bond with both members in it, as the server reports it. */
    private fun joinedDay(status: TodayStatus) = dayView(status).copy(bondStatus = BondStatus.ACTIVE)

    private fun TestScope.open(
        status: TodayStatus,
        permission: NotificationPermission = NotificationPermission.NotAsked,
        model: TodayViewModel = viewModel,
    ) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.state.collect {} }
        loop.show(joinedDay(status))
        model.onAction(TodayAction.OnNotificationPermissionChecked(permission))
    }

    // ---- When it shows ----

    @Test
    fun theFirstToWriteSeesItUnderWrittenOnceTheEntryIsSaved() = runTest {
        open(TodayStatus.Waiting)
        assertTrue(viewModel.state.value.askForNotifications)
    }

    @Test
    fun theSecondToWriteSeesItBelowBothEntriesOnceTheirSaveOpensTheDay() = runTest {
        open(TodayStatus.Revealed)
        assertTrue(viewModel.state.value.askForNotifications)
    }

    @Test
    fun itDoesNotShowBeforeAnyEntryIsSaved() = runTest {
        open(TodayStatus.NotWritten)
        assertFalse(viewModel.state.value.askForNotifications)
        // Kept on the phone, not yet saved on the server: not yet.
        loop.show(joinedDay(TodayStatus.Queued))
        assertFalse(viewModel.state.value.askForNotifications)
        // Then saved: now there is a reason.
        loop.show(joinedDay(TodayStatus.Waiting))
        assertTrue(viewModel.state.value.askForNotifications)
    }

    @Test
    fun itDoesNotShowWhileNobodyHasJoined() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
        viewModel.onAction(TodayAction.OnNotificationPermissionChecked(NotificationPermission.NotAsked))
        // The note for a bond nobody has joined has its own two actions.
        loop.show(dayView(TodayStatus.PartnerNotJoined).copy(bondStatus = BondStatus.PENDING_MEMBER, partnerName = null))
        assertFalse(viewModel.state.value.askForNotifications)
        // And never on a waiting day of such a bond, however it is drawn.
        loop.show(dayView(TodayStatus.Waiting).copy(bondStatus = BondStatus.PENDING_MEMBER))
        assertFalse(viewModel.state.value.askForNotifications)
    }

    @Test
    fun itDoesNotShowOnceThePhoneHasAnswered() = runTest {
        open(TodayStatus.Waiting, permission = NotificationPermission.Granted)
        assertFalse(viewModel.state.value.askForNotifications)
        viewModel.onAction(TodayAction.OnNotificationPermissionChecked(NotificationPermission.Denied))
        assertFalse(viewModel.state.value.askForNotifications)
    }

    @Test
    fun itDoesNotShowBeforeThePermissionHasBeenRead() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
        loop.show(joinedDay(TodayStatus.Waiting))
        assertFalse(viewModel.state.value.askForNotifications)
    }

    @Test
    fun itDoesNotShowOnAPhoneThatSaidNotNow() = runTest {
        memory.dismissed = true
        open(TodayStatus.Waiting)
        assertFalse(viewModel.state.value.askForNotifications)
    }

    // ---- Allow notifications ----

    @Test
    fun allowShowsThePhonesPromptAndEitherAnswerRemovesTheNote() = runTest {
        open(TodayStatus.Waiting)
        val phone = FakePermission(answer = NotificationPermission.Denied)
        viewModel.events.test {
            viewModel.onAction(TodayAction.OnAllowNotificationsClick)
            assertEquals(TodayEvent.AskNotificationPermission, awaitItem())
            // What TodayRoot does with the event: the controller's request, and its answer back.
            viewModel.onAction(TodayAction.OnNotificationPermissionChecked(phone.request()))
        }
        assertEquals(1, phone.requests)
        assertFalse(viewModel.state.value.askForNotifications)
        // The phone's answer is not "Not now": nothing is remembered for it.
        assertEquals(0, memory.dismissals)
    }

    @Test
    fun aGrantedAnswerRemovesTheNoteToo() = runTest {
        open(TodayStatus.Revealed)
        viewModel.onAction(TodayAction.OnAllowNotificationsClick)
        viewModel.onAction(TodayAction.OnNotificationPermissionChecked(NotificationPermission.Granted))
        assertFalse(viewModel.state.value.askForNotifications)
    }

    @Test
    fun aSecondTapWhileThePromptIsUpDoesNotAskTwice() = runTest {
        open(TodayStatus.Waiting)
        viewModel.events.test {
            viewModel.onAction(TodayAction.OnAllowNotificationsClick)
            viewModel.onAction(TodayAction.OnAllowNotificationsClick)
            assertEquals(TodayEvent.AskNotificationPermission, awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun allowDoesNothingWhereTheNoteIsNotDrawn() = runTest {
        open(TodayStatus.NotWritten)
        viewModel.events.test {
            viewModel.onAction(TodayAction.OnAllowNotificationsClick)
            expectNoEvents()
        }
    }

    // ---- Not now ----

    @Test
    fun notNowIsRememberedOnThePhoneAndTheNoteIsGone() = runTest {
        open(TodayStatus.Waiting)
        viewModel.onAction(TodayAction.OnNotNowClick)
        assertFalse(viewModel.state.value.askForNotifications)
        assertTrue(memory.dismissed)
    }

    @Test
    fun notNowStaysAfterARelaunchOrASignOut() = runTest {
        open(TodayStatus.Waiting)
        viewModel.onAction(TodayAction.OnNotNowClick)
        // A new Today over the same phone: the app relaunched, or another account signed in.
        val next = newViewModel()
        open(TodayStatus.Waiting, model = next)
        assertFalse(next.state.value.askForNotifications)
    }

    @Test
    fun theNoteOutlivesADeleteOfTheRevealedEntryNotNowIncluded() = runTest {
        // "Not now" is apart from Today's own dialog state, which a delete resets.
        open(TodayStatus.Revealed)
        viewModel.onAction(TodayAction.OnNotNowClick)
        viewModel.onAction(TodayAction.OnDeleteAfterRevealClick)
        viewModel.onAction(TodayAction.OnDeleteConfirm)
        loop.show(joinedDay(TodayStatus.Revealed))
        assertFalse(viewModel.state.value.askForNotifications)
    }

    /** The platform's controller, as TodayRoot holds it: answers [answer] and counts each prompt. */
    private class FakePermission(private val answer: NotificationPermission) : NotificationPermissionController {
        var requests = 0
            private set

        override suspend fun status(): NotificationPermission = if (requests == 0) NotificationPermission.NotAsked else answer

        override suspend fun request(): NotificationPermission {
            requests++
            return answer
        }

        override fun openSettings() = Unit
    }
}
