package com.moyi.app.feature.today

import app.cash.turbine.test
import com.moyi.app.designsystem.platform.NotificationPermission
import com.moyi.app.domain.bond.BondStatus
import com.moyi.app.domain.bond.PendingNotice
import com.moyi.app.domain.bond.SharedSetting
import com.moyi.app.domain.bond.SharedSettingNotice
import com.moyi.app.domain.bond.SharedSettingsNotices
import com.moyi.app.domain.bond.SharedSettingsSnapshot
import com.moyi.app.feature.today.common.RevealLedger
import com.moyi.app.feature.today.common.TodayStatus
import com.moyi.app.feature.today.today.TodayAction
import com.moyi.app.feature.today.today.TodayEvent
import com.moyi.app.feature.today.today.TodayViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.minus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Notes waiting per bond, as the repositories would leave them; Today reads and dismisses them. */
class FakeSettingsNotices(vararg waiting: Pair<String, SharedSettingNotice>) : SharedSettingsNotices {
    private var nextId = 1L
    private val pending = MutableStateFlow(waiting.map { (bond, notice) -> bond to PendingNotice(nextId++, notice) })
    val dismissed = mutableListOf<Long>()

    fun add(bondId: String, notice: SharedSettingNotice) {
        pending.value += bondId to PendingNotice(nextId++, notice)
    }

    override suspend fun observe(current: SharedSettingsSnapshot) = Unit

    override suspend fun recordOwn(current: SharedSettingsSnapshot, own: Set<SharedSetting>) = Unit

    override suspend fun clearOwnRequest(bondId: String, setting: SharedSetting) = Unit

    override fun notices(bondId: String): Flow<List<PendingNotice>> =
        pending.map { all -> all.filter { it.first == bondId }.map { it.second } }

    override suspend fun dismiss(bondId: String, id: Long) {
        dismissed += id
        pending.value = pending.value.filterNot { it.first == bondId && it.second.id == id }
    }
}

/**
 * The note about a change to a shared setting on Today (owner ask 2026-11-16): one at a time,
 * oldest first, in the place of the ask for notifications, which waits while one is there.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsNoticeTodayTest : MainDispatcherTest() {
    private val loop = FakeDailyLoop()
    // The reminder screen was shown yesterday, so the ask for notifications is due today.
    private val memory = FakeNotificationAskMemory(screenShownOn = PHONE_TODAY.minus(DatePeriod(days = 1)).toString())
    private val notices = FakeSettingsNotices()
    private val viewModel by lazy {
        TodayViewModel(loop, RevealLedger { "me" }, InviteBonds(), FakeOutbox(), memory, notices, today = { PHONE_TODAY })
    }

    private val bondId = dayView(TodayStatus.Waiting).bondId

    private fun TestScope.open(status: TodayStatus = TodayStatus.Waiting) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
        loop.show(dayView(status).copy(bondStatus = BondStatus.ACTIVE))
        viewModel.onAction(TodayAction.OnNotificationPermissionChecked(NotificationPermission.NotAsked))
    }

    @Test
    fun withNothingWaitingTheAskShowsAsBefore() = runTest {
        open()

        assertNull(viewModel.state.value.settingsNotice)
        assertTrue(viewModel.state.value.askForNotifications)
    }

    @Test
    fun aWaitingNoteTakesTheAsksPlace() = runTest {
        notices.add(bondId, SharedSettingNotice.TimezoneKept("Europe/London"))
        open()

        assertEquals(SharedSettingNotice.TimezoneKept("Europe/London"), viewModel.state.value.settingsNotice)
        assertFalse(viewModel.state.value.askForNotifications, "one note at a time: the settings note wins")
    }

    @Test
    fun aNoteFoundByALaterLoadReplacesTheAskOnScreen() = runTest {
        open()
        assertTrue(viewModel.state.value.askForNotifications)

        notices.add(bondId, SharedSettingNotice.NameChanged("Tea"))

        assertEquals(SharedSettingNotice.NameChanged("Tea"), viewModel.state.value.settingsNotice)
        assertFalse(viewModel.state.value.askForNotifications)
    }

    @Test
    fun dismissingShowsTheNextOneThenTheAskAgain() = runTest {
        notices.add(bondId, SharedSettingNotice.NameChanged("Tea"))
        notices.add(bondId, SharedSettingNotice.RestDaysChanged(on = false))
        open()

        viewModel.onAction(TodayAction.OnSettingsNoticeDismiss)
        assertEquals(SharedSettingNotice.RestDaysChanged(on = false), viewModel.state.value.settingsNotice, "oldest first, one after another")

        viewModel.onAction(TodayAction.OnSettingsNoticeDismiss)
        assertNull(viewModel.state.value.settingsNotice)
        assertTrue(viewModel.state.value.askForNotifications, "the ask comes back once nothing is waiting")
        assertEquals(listOf(1L, 2L), notices.dismissed)
    }

    @Test
    fun seeBondSettingsMarksItReadAndOpensSettings() = runTest {
        notices.add(bondId, SharedSettingNotice.TimezoneAgreed("Africa/Lagos"))
        open()

        viewModel.events.test {
            viewModel.onAction(TodayAction.OnSettingsNoticeOpenSettings)
            assertEquals(TodayEvent.NavigateToSettings(bondId), awaitItem())
        }
        assertNull(viewModel.state.value.settingsNotice)
    }

    @Test
    fun seeBondSettingsOnASearchNoteOpensSearchByMeaning() = runTest {
        notices.add(bondId, SharedSettingNotice.SemanticSearchAsked)
        notices.add(bondId, SharedSettingNotice.SemanticSearchTurnedOff)
        open()

        viewModel.events.test {
            viewModel.onAction(TodayAction.OnSettingsNoticeOpenSettings)
            assertEquals(TodayEvent.NavigateToSearchSettings(bondId), awaitItem())
            viewModel.onAction(TodayAction.OnSettingsNoticeOpenSettings)
            assertEquals(TodayEvent.NavigateToSearchSettings(bondId), awaitItem(), "the now-off note opens it too (ruling 13)")
        }
        assertNull(viewModel.state.value.settingsNotice)
    }

    @Test
    fun anotherBondsNoteIsNotShownHere() = runTest {
        notices.add("some-other-bond", SharedSettingNotice.NameChanged("Elsewhere"))
        open()

        assertNull(viewModel.state.value.settingsNotice)
    }

    @Test
    fun aLayoutWithoutTheNotesPlaceKeepsItWaiting() = runTest {
        notices.add(bondId, SharedSettingNotice.NameChanged("Tea"))
        open(TodayStatus.Queued)
        assertNull(viewModel.state.value.settingsNotice)
        assertTrue(notices.dismissed.isEmpty(), "not shown, so not read")

        loop.show(dayView(TodayStatus.Waiting).copy(bondStatus = BondStatus.ACTIVE))
        assertEquals(SharedSettingNotice.NameChanged("Tea"), viewModel.state.value.settingsNotice)
    }
}
