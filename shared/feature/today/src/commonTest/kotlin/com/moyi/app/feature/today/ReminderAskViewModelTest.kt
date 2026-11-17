package com.moyi.app.feature.today

import app.cash.turbine.test
import com.moyi.app.core.DataError
import com.moyi.app.core.EmptyResult
import com.moyi.app.core.Result
import com.moyi.app.designsystem.platform.NotificationPermission
import com.moyi.app.domain.bond.BondSettings
import com.moyi.app.domain.bond.BondSettingsChange
import com.moyi.app.domain.bond.BondSettingsRepository
import com.moyi.app.domain.bond.MemberSettings
import com.moyi.app.domain.bond.TimeOfDay
import com.moyi.app.feature.today.reminder.ReminderAskAction
import com.moyi.app.feature.today.reminder.ReminderAskEvent
import com.moyi.app.feature.today.reminder.ReminderAskPhase
import com.moyi.app.feature.today.reminder.ReminderAskViewModel
import com.moyi.app.feature.today.reminder.ReminderTimeLoad
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The reminder screen (owner rulings 2026-11-15): the server's reminder time, a save only when
 * it changed, then the phone's prompt, and what each answer leads to.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReminderAskViewModelTest : MainDispatcherTest() {
    private val held = MemberSettings(
        reminderTime = TimeOfDay(20, 0),
        reminderTimezone = "Europe/London",
        quietHoursStart = TimeOfDay(22, 0),
        quietHoursEnd = TimeOfDay(7, 0),
        nicknameForOther = "T",
    )
    private val settings = FakeMemberSettings(held)

    private fun viewModel() = ReminderAskViewModel("bond-1", settings)

    // ---- The time ----

    @Test
    fun theTimeIsTheOneTheServerHoldsForThisMember() = runTest {
        val model = viewModel()
        assertEquals(ReminderTimeLoad.Ready, model.state.value.load)
        assertEquals(TimeOfDay(20, 0), model.state.value.time)
        assertEquals(listOf("bond-1"), settings.reads)
    }

    @Test
    fun whileItLoadsNoTimeIsShownAndThePickerDoesNotOpen() = runTest {
        settings.gate = CompletableDeferred()
        val model = viewModel()
        assertEquals(ReminderTimeLoad.Loading, model.state.value.load)
        assertNull(model.state.value.time)
        model.onAction(ReminderAskAction.OnTimeClick)
        assertFalse(model.state.value.picking)
        settings.gate!!.complete(Unit)
        assertEquals(TimeOfDay(20, 0), model.state.value.time)
    }

    @Test
    fun whenItCannotBeReadNoTimeIsGuessedAndNothingIsSaved() = runTest {
        settings.readFails = true
        val model = viewModel()
        assertEquals(ReminderTimeLoad.Failed, model.state.value.load)
        assertNull(model.state.value.time)
        model.events.test {
            model.onAction(ReminderAskAction.OnTurnOnClick)
            assertEquals(ReminderAskEvent.AskPermission, awaitItem())
        }
        assertTrue(settings.saved.isEmpty())
    }

    @Test
    fun pickingATimeShowsItWithoutSavingIt() = runTest {
        val model = viewModel()
        model.onAction(ReminderAskAction.OnTimeClick)
        assertTrue(model.state.value.picking)
        model.onAction(ReminderAskAction.OnTimePicked(7, 30))
        assertFalse(model.state.value.picking)
        assertEquals(TimeOfDay(7, 30), model.state.value.time)
        assertTrue(settings.saved.isEmpty())
    }

    // ---- Turn on reminders ----

    @Test
    fun anUnchangedTimeIsNotSavedAndThePromptComesStraightAway() = runTest {
        val model = viewModel()
        // Picked, but the same as the server's: nothing to save.
        model.onAction(ReminderAskAction.OnTimeClick)
        model.onAction(ReminderAskAction.OnTimePicked(20, 0))
        model.events.test {
            model.onAction(ReminderAskAction.OnTurnOnClick)
            assertEquals(ReminderAskEvent.AskPermission, awaitItem())
        }
        assertTrue(settings.saved.isEmpty())
        assertEquals(ReminderAskPhase.Prompting, model.state.value.phase)
    }

    @Test
    fun aChangedTimeIsSavedAsSettingsSavesItThenThePromptComes() = runTest {
        val model = viewModel()
        model.onAction(ReminderAskAction.OnTimeClick)
        model.onAction(ReminderAskAction.OnTimePicked(7, 30))
        settings.saveGate = CompletableDeferred()
        model.events.test {
            model.onAction(ReminderAskAction.OnTurnOnClick)
            // Saving: the PrimaryAction says so, and a second tap does nothing.
            assertEquals(ReminderAskPhase.Saving, model.state.value.phase)
            model.onAction(ReminderAskAction.OnTurnOnClick)
            model.onAction(ReminderAskAction.OnNotNowClick)
            expectNoEvents()
            settings.saveGate!!.complete(Unit)
            assertEquals(ReminderAskEvent.AskPermission, awaitItem())
        }
        // Every other field as read, the zone included: only the time changes.
        assertEquals(listOf(held.copy(reminderTime = TimeOfDay(7, 30))), settings.saved)
        assertEquals(TimeOfDay(7, 30), model.state.value.time)
    }

    @Test
    fun grantedGoesOnToToday() = runTest {
        val model = viewModel()
        model.events.test {
            model.onAction(ReminderAskAction.OnTurnOnClick)
            assertEquals(ReminderAskEvent.AskPermission, awaitItem())
            model.onAction(ReminderAskAction.OnPermissionAnswered(NotificationPermission.Granted))
            assertEquals(ReminderAskEvent.Done, awaitItem())
        }
    }

    @Test
    fun deniedStaysAndSaysHowToTurnThemOnWithOpenSettingsAndContinue() = runTest {
        val model = viewModel()
        model.events.test {
            model.onAction(ReminderAskAction.OnTurnOnClick)
            assertEquals(ReminderAskEvent.AskPermission, awaitItem())
            model.onAction(ReminderAskAction.OnPermissionAnswered(NotificationPermission.Denied))
            expectNoEvents()
            assertEquals(ReminderAskPhase.Answered, model.state.value.phase)
            assertTrue(model.state.value.denied)

            model.onAction(ReminderAskAction.OnOpenPhoneSettingsClick)
            assertEquals(ReminderAskEvent.OpenPhoneSettings, awaitItem())
            // Back from the phone's settings with them still off: still here.
            model.onAction(ReminderAskAction.OnPermissionChecked(NotificationPermission.Denied))
            expectNoEvents()

            model.onAction(ReminderAskAction.OnContinueClick)
            assertEquals(ReminderAskEvent.Done, awaitItem())
        }
    }

    @Test
    fun backFromThePhonesSettingsWithThemOnGoesOnToToday() = runTest {
        val model = viewModel()
        model.events.test {
            model.onAction(ReminderAskAction.OnTurnOnClick)
            awaitItem()
            model.onAction(ReminderAskAction.OnPermissionAnswered(NotificationPermission.Denied))
            model.onAction(ReminderAskAction.OnPermissionChecked(NotificationPermission.Granted))
            assertEquals(ReminderAskEvent.Done, awaitItem())
        }
    }

    @Test
    fun aFailedSaveSaysSoStillAsksAndNeverTrapsThePerson() = runTest {
        settings.saveFails = true
        val model = viewModel()
        model.onAction(ReminderAskAction.OnTimeClick)
        model.onAction(ReminderAskAction.OnTimePicked(7, 30))
        model.events.test {
            model.onAction(ReminderAskAction.OnTurnOnClick)
            // Reminders still come, at the server's time: the prompt is still worth asking.
            assertEquals(ReminderAskEvent.AskPermission, awaitItem())
            model.onAction(ReminderAskAction.OnPermissionAnswered(NotificationPermission.Granted))
            // Granted, but the time did not save: the screen says so before going on.
            expectNoEvents()
            assertTrue(model.state.value.saveFailed)
            assertEquals(ReminderAskPhase.Answered, model.state.value.phase)
            // The line names the time the server still holds, not the one that failed.
            assertEquals(TimeOfDay(20, 0), model.state.value.time)
            model.onAction(ReminderAskAction.OnContinueClick)
            assertEquals(ReminderAskEvent.Done, awaitItem())
        }
    }

    @Test
    fun aPromptClosedWithoutAnAnswerGoesOnToToday() = runTest {
        val model = viewModel()
        model.events.test {
            model.onAction(ReminderAskAction.OnTurnOnClick)
            awaitItem()
            model.onAction(ReminderAskAction.OnPermissionAnswered(NotificationPermission.NotAsked))
            assertEquals(ReminderAskEvent.Done, awaitItem())
        }
    }

    // ---- Not now ----

    @Test
    fun notNowGoesOnToTodayAndSavesNothing() = runTest {
        val model = viewModel()
        model.onAction(ReminderAskAction.OnTimeClick)
        model.onAction(ReminderAskAction.OnTimePicked(7, 30))
        model.events.test {
            model.onAction(ReminderAskAction.OnNotNowClick)
            assertEquals(ReminderAskEvent.Done, awaitItem())
        }
        assertTrue(settings.saved.isEmpty())
    }
}

/** `/members/me/settings` for one member: the rest of Bond settings is not this screen's. */
private class FakeMemberSettings(private var held: MemberSettings) : BondSettingsRepository {
    var gate: CompletableDeferred<Unit>? = null
    var saveGate: CompletableDeferred<Unit>? = null
    var readFails = false
    var saveFails = false
    val reads = mutableListOf<String>()
    val saved = mutableListOf<MemberSettings>()

    override suspend fun memberSettings(bondId: String): Result<MemberSettings, DataError.Network> {
        reads += bondId
        gate?.await()
        return if (readFails) Result.Failure(DataError.Network.NoInternet) else Result.Success(held)
    }

    override suspend fun replaceMemberSettings(bondId: String, settings: MemberSettings): Result<MemberSettings, DataError.Network> {
        saveGate?.await()
        if (saveFails) return Result.Failure(DataError.Network.Timeout)
        saved += settings
        held = settings
        return Result.Success(settings)
    }

    override suspend fun settings(bondId: String): Result<BondSettings, DataError.Network> = error("not used")

    override suspend fun update(bondId: String, version: String, change: BondSettingsChange): Result<BondSettings, DataError.Network> =
        error("not used")

    override suspend fun leave(bondId: String, withdrawEntries: Boolean): EmptyResult<DataError.Network> = error("not used")

    override suspend fun block(bondId: String, withdrawEntries: Boolean): EmptyResult<DataError.Network> = error("not used")

    override suspend fun requestDeletion(bondId: String): Result<BondSettings, DataError.Network> = error("not used")

    override suspend fun cancelDeletion(bondId: String): EmptyResult<DataError.Network> = error("not used")

    override suspend fun proposeTimezone(bondId: String, timezone: String): Result<BondSettings, DataError.Network> = error("not used")

    override suspend fun confirmTimezone(bondId: String, proposalId: String): Result<BondSettings, DataError.Network> = error("not used")

    override suspend fun cancelTimezoneChange(bondId: String): EmptyResult<DataError.Network> = error("not used")
    override suspend fun turnOffSemanticSearch(bondId: String, version: String): Result<BondSettings, DataError.Network> = error("not used")
    override suspend fun requestSemanticSearch(bondId: String): Result<BondSettings, DataError.Network> = error("not used")
    override suspend fun cancelSemanticSearchRequest(bondId: String): EmptyResult<DataError.Network> = error("not used")
}
