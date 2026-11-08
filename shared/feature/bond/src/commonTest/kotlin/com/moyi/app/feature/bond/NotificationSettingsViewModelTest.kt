package com.moyi.app.feature.bond

import app.cash.turbine.test
import com.moyi.app.core.ApiErrorCode
import com.moyi.app.core.DataError
import com.moyi.app.core.Result
import com.moyi.app.designsystem.platform.NotificationPermission
import com.moyi.app.designsystem.presentation.UiText
import com.moyi.app.domain.bond.TimeOfDay
import com.moyi.app.domain.notification.NotificationCategory
import com.moyi.app.domain.notification.NotificationPreferences
import com.moyi.app.domain.notification.NotificationSettingsRepository
import com.moyi.app.feature.bond.notifications.NotificationPermissionPolicy
import com.moyi.app.feature.bond.notifications.NotificationRow
import com.moyi.app.feature.bond.notifications.NotificationRowError
import com.moyi.app.feature.bond.notifications.NotificationSettingsAction
import com.moyi.app.feature.bond.notifications.NotificationSettingsEvent
import com.moyi.app.feature.bond.notifications.NotificationSettingsViewModel
import com.moyi.app.feature.bond.notifications.TimeTarget
import com.moyi.app.feature.bond.resources.Res
import com.moyi.app.feature.bond.resources.settings_error_generic
import com.moyi.app.feature.bond.settings.SettingsLoad
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Notifications: load, a change per row with Saving, a failure that keeps the screen, and when the permission is asked. */
class NotificationSettingsViewModelTest : MainDispatcherTest() {
    private class FakeNotifications : NotificationSettingsRepository {
        var server = NotificationPreferences(NotificationCategory.entries.toSet() - NotificationCategory.OnThisDay)
        val reads = ArrayDeque<Result<NotificationPreferences, DataError.Network>>()
        val writes = ArrayDeque<Result<NotificationPreferences, DataError.Network>>()
        val changes = mutableListOf<Pair<NotificationCategory, Boolean>>()
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun preferences(bondId: String) = reads.removeFirstOrNull() ?: Result.Success(server)

        override suspend fun change(bondId: String, category: NotificationCategory, on: Boolean): Result<NotificationPreferences, DataError.Network> {
            changes += category to on
            gate?.await()
            writes.removeFirstOrNull()?.let { return it }
            server = NotificationPreferences(if (on) server.on + category else server.on - category)
            return Result.Success(server)
        }
    }

    private val notifications = FakeNotifications()
    private val settings = FakeBondSettingsRepository()
    private val viewModel by lazy { NotificationSettingsViewModel("b1", notifications, settings) }
    private val generic = UiText.Resource(Res.string.settings_error_generic)

    // ----- load -----

    @Test
    fun loadsTheTogglesAndTheMembersOwnSettings() {
        val state = viewModel.state.value
        assertEquals(SettingsLoad.Ready, state.load)
        assertEquals(notifications.server, state.preferences)
        assertEquals(settings.member, state.member)
        assertFalse(state.quietHoursOn)
    }

    @Test
    fun eitherHalfFailingIsTheGenericErrorAndTryAgainLoads() {
        settings.memberReads += Result.Failure(DataError.Network.NoInternet)
        assertEquals(SettingsLoad.Failed, viewModel.state.value.load)

        viewModel.onAction(NotificationSettingsAction.OnRetryClick)

        assertEquals(SettingsLoad.Ready, viewModel.state.value.load)
    }

    @Test
    fun aBondThatEndedIsEnded() = runTest {
        notifications.reads += apiError(409, ApiErrorCode.BOND_ARCHIVED)
        viewModel.events.test { assertEquals(NotificationSettingsEvent.Ended, awaitItem()) }
    }

    // ----- toggles -----

    @Test
    fun aToggleSaysSavingThenShowsWhatTheServerKept() {
        notifications.gate = CompletableDeferred()
        viewModel.onAction(NotificationSettingsAction.OnCategoryToggle(NotificationCategory.Milestone))
        assertEquals(NotificationRow.Category(NotificationCategory.Milestone), viewModel.state.value.saving)

        notifications.gate!!.complete(Unit)

        assertNull(viewModel.state.value.saving)
        assertFalse(viewModel.state.value.preferences!!.isOn(NotificationCategory.Milestone))
        assertEquals(listOf(NotificationCategory.Milestone to false), notifications.changes)
    }

    @Test
    fun aFailedToggleKeepsTheScreenAndWhatWasThereAndSaysSoUnderTheRow() {
        val before = viewModel.state.value.preferences
        notifications.writes += Result.Failure(DataError.Network.NoInternet)

        viewModel.onAction(NotificationSettingsAction.OnCategoryToggle(NotificationCategory.Reveal))

        val state = viewModel.state.value
        assertEquals(SettingsLoad.Ready, state.load)
        assertEquals(before, state.preferences)
        assertNull(state.saving)
        assertEquals(NotificationRowError(NotificationRow.Category(NotificationCategory.Reveal), generic), state.error)
    }

    @Test
    fun oneChangeAtATime() {
        notifications.gate = CompletableDeferred()
        viewModel.onAction(NotificationSettingsAction.OnCategoryToggle(NotificationCategory.Milestone))
        viewModel.onAction(NotificationSettingsAction.OnCategoryToggle(NotificationCategory.Reveal))
        viewModel.onAction(NotificationSettingsAction.OnQuietHoursToggle)

        assertEquals(1, notifications.changes.size)
        assertTrue(settings.memberReplaced.isEmpty())
    }

    // ----- reminder time and quiet hours -----

    @Test
    fun aReminderTimeReplacesTheMemberSettingsKeepingEverythingElse() {
        viewModel.onAction(NotificationSettingsAction.OnReminderTimeClick)
        assertEquals(TimeTarget.Reminder, viewModel.state.value.picking)

        viewModel.onAction(NotificationSettingsAction.OnTimePicked(21, 30))

        assertEquals(listOf(settings.member), settings.memberReplaced)
        assertEquals(TimeOfDay(21, 30), settings.member.reminderTime)
        assertEquals("Tee", settings.member.nicknameForOther)
        assertEquals("Africa/Lagos", settings.member.reminderTimezone)
        assertNull(viewModel.state.value.picking)
    }

    @Test
    fun theSameTimeSendsNothing() {
        viewModel.onAction(NotificationSettingsAction.OnReminderTimeClick)
        viewModel.onAction(NotificationSettingsAction.OnTimePicked(20, 0))
        assertTrue(settings.memberReplaced.isEmpty())
    }

    @Test
    fun quietHoursTurnOnWithBothEndsAndOffWithNeither() {
        viewModel.onAction(NotificationSettingsAction.OnQuietHoursToggle)
        assertTrue(viewModel.state.value.quietHoursOn)
        assertEquals(TimeOfDay(22, 0), settings.member.quietHoursStart)
        assertEquals(TimeOfDay(7, 0), settings.member.quietHoursEnd)

        viewModel.onAction(NotificationSettingsAction.OnQuietEndClick)
        viewModel.onAction(NotificationSettingsAction.OnTimePicked(6, 30))
        assertEquals(TimeOfDay(6, 30), settings.member.quietHoursEnd)

        viewModel.onAction(NotificationSettingsAction.OnQuietHoursToggle)
        assertFalse(viewModel.state.value.quietHoursOn)
        assertNull(settings.member.quietHoursStart)
        assertNull(settings.member.quietHoursEnd)
    }

    @Test
    fun theQuietTimesCannotBePickedWhileQuietHoursAreOff() {
        viewModel.onAction(NotificationSettingsAction.OnQuietStartClick)
        assertNull(viewModel.state.value.picking)
    }

    @Test
    fun aFailedQuietHoursChangeKeepsWhatWasThere() {
        settings.memberWrites += apiError(422, ApiErrorCode.VALIDATION_FAILED)

        viewModel.onAction(NotificationSettingsAction.OnQuietHoursToggle)

        assertFalse(viewModel.state.value.quietHoursOn)
        assertEquals(NotificationRowError(NotificationRow.QuietHours, generic), viewModel.state.value.error)
    }

    // ----- the permission -----

    @Test
    fun turningAKindOnWhileThePhoneWasNeverAskedAsksOnceAndStillSaves() = runTest {
        viewModel.onAction(NotificationSettingsAction.OnPermissionChecked(NotificationPermission.NotAsked))
        viewModel.events.test {
            viewModel.onAction(NotificationSettingsAction.OnCategoryToggle(NotificationCategory.OnThisDay))
            assertEquals(NotificationSettingsEvent.AskPermission, awaitItem())
            assertTrue(viewModel.state.value.askingPermission)
            assertTrue(viewModel.state.value.preferences!!.isOn(NotificationCategory.OnThisDay))

            // A second kind turned on while the prompt is up asks nothing more.
            viewModel.onAction(NotificationSettingsAction.OnCategoryToggle(NotificationCategory.Milestone))
            viewModel.onAction(NotificationSettingsAction.OnCategoryToggle(NotificationCategory.Milestone))
            expectNoEvents()

            viewModel.onAction(NotificationSettingsAction.OnPermissionChecked(NotificationPermission.Granted))
            assertFalse(viewModel.state.value.askingPermission)
        }
    }

    @Test
    fun turningAKindOffNeverAsks() = runTest {
        viewModel.onAction(NotificationSettingsAction.OnPermissionChecked(NotificationPermission.NotAsked))
        viewModel.events.test {
            viewModel.onAction(NotificationSettingsAction.OnCategoryToggle(NotificationCategory.Reveal))
            expectNoEvents()
        }
    }

    @Test
    fun aPhoneThatSaidNoIsNeverAskedAgainAndCanOpenSettings() = runTest {
        viewModel.onAction(NotificationSettingsAction.OnPermissionChecked(NotificationPermission.Denied))
        viewModel.events.test {
            viewModel.onAction(NotificationSettingsAction.OnCategoryToggle(NotificationCategory.OnThisDay))
            viewModel.onAction(NotificationSettingsAction.OnAllowClick)
            expectNoEvents()

            viewModel.onAction(NotificationSettingsAction.OnOpenPhoneSettingsClick)
            assertEquals(NotificationSettingsEvent.OpenPhoneSettings, awaitItem())
        }
    }

    @Test
    fun allowAsksOnlyWhileThePhoneWasNeverAsked() = runTest {
        viewModel.events.test {
            viewModel.onAction(NotificationSettingsAction.OnAllowClick) // not read yet: nothing
            expectNoEvents()
            viewModel.onAction(NotificationSettingsAction.OnPermissionChecked(NotificationPermission.NotAsked))
            viewModel.onAction(NotificationSettingsAction.OnAllowClick)
            assertEquals(NotificationSettingsEvent.AskPermission, awaitItem())
            viewModel.onAction(NotificationSettingsAction.OnAllowClick) // already asking
            expectNoEvents()
        }
    }

    @Test
    fun thePolicyAsksOnlyOnTurningOnANeverAskedPhone() {
        val all = NotificationPermission.entries + listOf(null)
        for (permission in all) {
            for (turningOn in listOf(true, false)) {
                for (asking in listOf(true, false)) {
                    val expected = turningOn && !asking && permission == NotificationPermission.NotAsked
                    assertEquals(expected, NotificationPermissionPolicy.shouldAskOnToggle(permission, turningOn, asking), "$permission $turningOn $asking")
                }
            }
        }
        assertTrue(NotificationPermissionPolicy.offersAllow(NotificationPermission.NotAsked))
        assertFalse(NotificationPermissionPolicy.offersAllow(NotificationPermission.Granted))
        assertTrue(NotificationPermissionPolicy.saysItIsOff(NotificationPermission.Denied))
        assertFalse(NotificationPermissionPolicy.saysItIsOff(null))
    }
}
