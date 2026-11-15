package com.moyi.app.feature.today

import app.cash.turbine.test
import com.moyi.app.designsystem.platform.NotificationPermission
import com.moyi.app.domain.bond.BondStatus
import com.moyi.app.feature.today.common.RevealLedger
import com.moyi.app.feature.today.common.TodayStatus
import com.moyi.app.feature.today.today.TodayAction
import com.moyi.app.feature.today.today.TodayEvent
import com.moyi.app.feature.today.today.TodayViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * When Today opens the reminder screen (owner rulings 2026-11-15): once per phone, the first
 * time a live day shows while the phone has not been asked, joined or not, before anything is
 * written; remembered on the phone before it opens.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReminderScreenTodayTest : MainDispatcherTest() {
    private val loop = FakeDailyLoop()
    private val memory = FakeNotificationAskMemory()

    private fun newViewModel() = TodayViewModel(loop, RevealLedger { "me" }, InviteBonds(), FakeOutbox(), memory, today = { PHONE_TODAY })

    private val viewModel by lazy { newViewModel() }

    private fun TodayViewModel.read(permission: NotificationPermission) = onAction(TodayAction.OnNotificationPermissionChecked(permission))

    @Test
    fun theFirstArrivalOpensItBeforeAnythingIsWrittenAndRemembersTheDay() = runTest {
        viewModel.events.test {
            loop.show(dayView(TodayStatus.NotWritten).copy(bondStatus = BondStatus.ACTIVE))
            viewModel.read(NotificationPermission.NotAsked)
            assertEquals(TodayEvent.NavigateToReminderAsk("bond-1"), awaitItem())
            expectNoEvents()
        }
        assertEquals(PHONE_TODAY.toString(), memory.screenShownOn)
    }

    @Test
    fun itOpensWhileNobodyHasJoined() = runTest {
        viewModel.events.test {
            viewModel.read(NotificationPermission.NotAsked)
            // The permission was read first this time: the day arriving opens it.
            loop.show(dayView(TodayStatus.NotWritten).copy(bondStatus = BondStatus.PENDING_MEMBER, partnerName = null))
            assertEquals(TodayEvent.NavigateToReminderAsk("bond-1"), awaitItem())
        }
    }

    @Test
    fun itOpensOnceEvenWhenTheDayAndThePermissionAreReadAgain() = runTest {
        viewModel.events.test {
            loop.show(dayView(TodayStatus.NotWritten))
            viewModel.read(NotificationPermission.NotAsked)
            assertEquals(TodayEvent.NavigateToReminderAsk("bond-1"), awaitItem())
            loop.show(dayView(TodayStatus.Waiting))
            viewModel.read(NotificationPermission.NotAsked)
            expectNoEvents()
        }
    }

    @Test
    fun aNewTodayOnTheSamePhoneNeverOpensItAgain() = runTest {
        loop.show(dayView(TodayStatus.NotWritten))
        viewModel.read(NotificationPermission.NotAsked)
        // The app relaunched, or someone signed out and in: the phone remembers.
        val next = newViewModel()
        next.events.test {
            next.read(NotificationPermission.NotAsked)
            expectNoEvents()
        }
    }

    @Test
    fun itDoesNotOpenBeforeThePermissionHasBeenRead() = runTest {
        viewModel.events.test {
            loop.show(dayView(TodayStatus.NotWritten))
            expectNoEvents()
        }
        assertNull(memory.screenShownOn)
    }

    @Test
    fun itDoesNotOpenOnceThePhoneHasAnsweredOrOnAndroidBelow13() = runTest {
        // Android below 13 reads as Granted: the permission does not exist there.
        listOf(NotificationPermission.Granted, NotificationPermission.Denied).forEach { answered ->
            val model = newViewModel()
            model.events.test {
                loop.show(dayView(TodayStatus.NotWritten))
                model.read(answered)
                expectNoEvents()
            }
        }
        assertNull(memory.screenShownOn)
    }

    @Test
    fun itDoesNotOpenOnABondThatHasEnded() = runTest {
        viewModel.events.test {
            viewModel.read(NotificationPermission.NotAsked)
            listOf(BondStatus.ARCHIVED, BondStatus.PENDING_DELETION, BondStatus.DELETED).forEach { ended ->
                loop.show(dayView(TodayStatus.NotWritten).copy(bondStatus = ended))
            }
            expectNoEvents()
        }
        assertNull(memory.screenShownOn)
    }

    @Test
    fun itDoesNotOpenOnAPhoneThatAlreadySaidNotNowToTheNote() = runTest {
        // An install from before the screen, which answered the note: it keeps the note's answer.
        memory.dismissed = true
        viewModel.events.test {
            loop.show(dayView(TodayStatus.NotWritten))
            viewModel.read(NotificationPermission.NotAsked)
            expectNoEvents()
        }
    }

    @Test
    fun itDoesNotOpenWhileTheEntryIsOnlyOnThePhone() = runTest {
        viewModel.events.test {
            loop.show(dayView(TodayStatus.Queued))
            viewModel.read(NotificationPermission.NotAsked)
            expectNoEvents()
        }
    }
}
