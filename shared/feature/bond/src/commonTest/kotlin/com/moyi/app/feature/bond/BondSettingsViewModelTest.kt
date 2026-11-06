package com.moyi.app.feature.bond

import app.cash.turbine.test
import com.moyi.app.core.ApiErrorCode
import com.moyi.app.core.DataError
import com.moyi.app.core.Result
import com.moyi.app.designsystem.presentation.UiText
import com.moyi.app.domain.bond.BondSettingsChange
import com.moyi.app.domain.bond.BondType
import com.moyi.app.domain.bond.TimeOfDay
import com.moyi.app.feature.bond.resources.Res
import com.moyi.app.feature.bond.resources.settings_error_conflict
import com.moyi.app.feature.bond.resources.settings_error_generic
import com.moyi.app.feature.bond.resources.settings_leave_error_ended
import com.moyi.app.feature.bond.settings.BondSettingsAction
import com.moyi.app.feature.bond.settings.BondSettingsEvent
import com.moyi.app.feature.bond.settings.BondSettingsViewModel
import com.moyi.app.feature.bond.settings.SettingsError
import com.moyi.app.feature.bond.settings.SettingsField
import com.moyi.app.feature.bond.settings.SettingsLoad
import com.moyi.app.feature.onboarding.resources.bond_name_error_blank
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import com.moyi.app.feature.onboarding.resources.Res as OnboardingRes

class BondSettingsViewModelTest : MainDispatcherTest() {
    private val repo = FakeBondSettingsRepository()
    private val auth = FakeAuthRepository()
    private val bonds = FakeBondRepository(repo)
    private var zones: Set<String> = setOf("Europe/London", "Africa/Lagos", "UTC", "Etc/GMT+1", "America/Argentina/Buenos_Aires")
    private val viewModel by lazy { BondSettingsViewModel("b1", repo, auth, bonds, zoneIds = { zones }) }

    private val generic = UiText.Resource(Res.string.settings_error_generic)
    private val conflictLine = UiText.Resource(Res.string.settings_error_conflict)

    // ----- load -----

    @Test
    fun loadsTheSettingsWithTheirVersion() {
        val state = viewModel.state.value
        assertEquals(SettingsLoad.Ready, state.load)
        assertEquals(settingsV1, state.settings)
        assertEquals(1, repo.readCount)
    }

    @Test
    fun aFailedLoadIsTheGenericErrorAndTryAgainLoadsAgain() {
        repo.reads += Result.Failure(DataError.Network.NoInternet)
        assertEquals(SettingsLoad.Failed, viewModel.state.value.load)

        viewModel.onAction(BondSettingsAction.OnRetryClick)

        assertEquals(SettingsLoad.Ready, viewModel.state.value.load)
        assertEquals(settingsV1, viewModel.state.value.settings)
        assertEquals(2, repo.readCount)
    }

    @Test
    fun aBondThePersonIsNoLongerInEndsSettings() = runTest {
        repo.reads += apiError(404, ApiErrorCode.NOT_FOUND)
        viewModel.events.test { assertEquals(BondSettingsEvent.Ended, awaitItem()) }
    }

    // ----- bond name -----

    @Test
    fun theNameEditorOpensWithTheCurrentNameAndSavesWithTheVersion() = runTest {
        viewModel.events.test {
            viewModel.onAction(BondSettingsAction.OnNameClick)
            assertEquals(BondSettingsEvent.OpenName, awaitItem())
            assertEquals("Amara and Tunde", viewModel.state.value.nameDraft)

            viewModel.onAction(BondSettingsAction.OnNameChange("Us two"))
            viewModel.onAction(BondSettingsAction.OnNameSave)

            assertEquals(BondSettingsEvent.Saved, awaitItem())
        }
        assertEquals(Triple("b1", "\"1\"", BondSettingsChange(name = "Us two")), repo.updates.single())
        assertEquals("Us two", viewModel.state.value.settings?.name)
        assertEquals("\"2\"", viewModel.state.value.settings?.version, "the next change sends the new version")
    }

    @Test
    fun aBlankNameIsRefusedOnTheFieldWithoutARequest() {
        viewModel.onAction(BondSettingsAction.OnNameClick)
        viewModel.onAction(BondSettingsAction.OnNameChange("   "))
        viewModel.onAction(BondSettingsAction.OnNameSave)

        assertEquals(UiText.Resource(OnboardingRes.string.bond_name_error_blank), viewModel.state.value.nameError)
        assertTrue(repo.updates.isEmpty())
    }

    @Test
    fun anUnchangedNameClosesWithoutARequest() = runTest {
        viewModel.events.test {
            viewModel.onAction(BondSettingsAction.OnNameClick)
            assertEquals(BondSettingsEvent.OpenName, awaitItem())
            viewModel.onAction(BondSettingsAction.OnNameSave)
            assertEquals(BondSettingsEvent.Saved, awaitItem())
        }
        assertTrue(repo.updates.isEmpty())
    }

    @Test
    fun aFailedNameSaveKeepsTheTypedNameAndSaysSo() = runTest {
        repo.updateAnswers += Result.Failure(DataError.Network.NoInternet)
        viewModel.events.test {
            viewModel.onAction(BondSettingsAction.OnNameClick)
            assertEquals(BondSettingsEvent.OpenName, awaitItem())
            viewModel.onAction(BondSettingsAction.OnNameChange("Us two"))
            viewModel.onAction(BondSettingsAction.OnNameSave)
            expectNoEvents()
        }

        val state = viewModel.state.value
        assertEquals("Us two", state.nameDraft)
        assertEquals(SettingsError(SettingsField.Name, generic), state.error)
        assertNull(state.saving)
        assertEquals("Amara and Tunde", state.settings?.name)
    }

    // ----- relationship type, reveal time, rest days -----

    @Test
    fun choosingATypeSavesItAtOnceAndReturns() = runTest {
        viewModel.events.test {
            viewModel.onAction(BondSettingsAction.OnTypeSelect(BondType.FRIENDS))
            assertEquals(BondSettingsEvent.Saved, awaitItem())
        }
        assertEquals(BondSettingsChange(type = BondType.FRIENDS), repo.updates.single().third)
        assertEquals(BondType.FRIENDS, viewModel.state.value.settings?.type)
    }

    @Test
    fun aFailedTypeSaveKeepsThePersonOnTheEditor() = runTest {
        repo.updateAnswers += Result.Failure(DataError.Network.Server(500))
        viewModel.events.test {
            viewModel.onAction(BondSettingsAction.OnTypeSelect(BondType.FAMILY))
            expectNoEvents()
        }
        assertEquals(SettingsError(SettingsField.Type, generic), viewModel.state.value.error)
        assertEquals(BondType.COUPLE, viewModel.state.value.settings?.type)
    }

    @Test
    fun aPickedRevealTimeSavesAsHoursAndMinutes() {
        viewModel.onAction(BondSettingsAction.OnRevealTimeClick)
        assertTrue(viewModel.state.value.pickingRevealTime)

        viewModel.onAction(BondSettingsAction.OnRevealTimePicked(20, 30))

        assertFalse(viewModel.state.value.pickingRevealTime)
        assertEquals(BondSettingsChange(revealTime = TimeOfDay(20, 30)), repo.updates.single().third)
        assertEquals(TimeOfDay(20, 30), viewModel.state.value.settings?.revealTime)
    }

    @Test
    fun theRowSaysSavingWhileTheChangeIsInFlight() {
        repo.gate = CompletableDeferred()
        viewModel.onAction(BondSettingsAction.OnRevealTimePicked(22, 0))
        assertEquals(SettingsField.RevealTime, viewModel.state.value.saving)

        // One change at a time: a second, while the first is in flight, sends nothing.
        viewModel.onAction(BondSettingsAction.OnRestDaysToggle)
        assertEquals(1, repo.updates.size)

        repo.gate!!.complete(Unit)
        assertNull(viewModel.state.value.saving)
    }

    @Test
    fun restDaysToggleInPlaceByFlippingStrictMode() {
        assertTrue(viewModel.state.value.settings!!.restDays)

        viewModel.onAction(BondSettingsAction.OnRestDaysToggle)

        assertEquals(BondSettingsChange(strictMode = true), repo.updates.single().third)
        assertFalse(viewModel.state.value.settings!!.restDays)
    }

    @Test
    fun aFailedToggleKeepsWhatTheServerHasAndSaysSoUnderTheRow() {
        repo.updateAnswers += Result.Failure(DataError.Network.Timeout)

        viewModel.onAction(BondSettingsAction.OnRestDaysToggle)

        assertTrue(viewModel.state.value.settings!!.restDays)
        assertEquals(SettingsError(SettingsField.RestDays, generic), viewModel.state.value.error)
    }

    // ----- the ETag conflict -----

    @Test
    fun aStaleVersionReloadsShowsWhatIsThereNowAndSaysSo() {
        assertEquals(settingsV1, viewModel.state.value.settings, "read before the other member's change")
        val theirs = settingsV1.copy(name = "Tunde and Amara", version = "\"2\"")
        repo.updateAnswers += apiError(412, ApiErrorCode.PRECONDITION_FAILED)
        repo.server = theirs
        viewModel.onAction(BondSettingsAction.OnNameClick)
        viewModel.onAction(BondSettingsAction.OnNameChange("Us two"))

        viewModel.onAction(BondSettingsAction.OnNameSave)

        val state = viewModel.state.value
        assertEquals(theirs, state.settings, "reloaded: the other member's change, with its version")
        assertEquals(SettingsError(SettingsField.Name, conflictLine), state.error)
        assertEquals("Us two", state.nameDraft, "what the person typed is kept, to save again")
        assertEquals(1, repo.updates.size, "never retried blindly")

        viewModel.onAction(BondSettingsAction.OnNameSave)
        assertEquals("\"2\"", repo.updates.last().second, "a second save carries the version just read")
    }

    @Test
    fun aWriteOnAnEndedBondLeavesSettings() = runTest {
        repo.updateAnswers += apiError(409, ApiErrorCode.BOND_ARCHIVED)
        viewModel.events.test {
            viewModel.onAction(BondSettingsAction.OnRestDaysToggle)
            assertEquals(BondSettingsEvent.Ended, awaitItem())
        }
    }

    // ----- leave -----

    @Test
    fun leavingOpensItsConfirmWithEntriesKeptAndDoesNothingUntilConfirmed() = runTest {
        viewModel.events.test {
            viewModel.onAction(BondSettingsAction.OnLeaveClick)
            assertEquals(BondSettingsEvent.OpenLeave, awaitItem())
        }
        assertFalse(viewModel.state.value.withdrawOnLeave, "FR-029a is off by default on leave (ADR-0035)")
        assertEquals(0, repo.leaves)
    }

    @Test
    fun aConfirmedLeaveKeepsTheEntriesThenEnds() = runTest {
        viewModel.events.test {
            viewModel.onAction(BondSettingsAction.OnLeaveClick)
            assertEquals(BondSettingsEvent.OpenLeave, awaitItem())
            viewModel.onAction(BondSettingsAction.OnLeaveConfirm)
            assertEquals(BondSettingsEvent.Ended, awaitItem())
        }
        assertEquals(listOf(false), repo.leaveWithdraws)
    }

    @Test
    fun aLeaveWithTheRowTickedWithdrawsTheEntries() = runTest {
        viewModel.events.test {
            viewModel.onAction(BondSettingsAction.OnLeaveClick)
            assertEquals(BondSettingsEvent.OpenLeave, awaitItem())
            viewModel.onAction(BondSettingsAction.OnLeaveWithdrawToggle)
            viewModel.onAction(BondSettingsAction.OnLeaveConfirm)
            assertEquals(BondSettingsEvent.Ended, awaitItem())
        }
        assertEquals(listOf(true), repo.leaveWithdraws)
    }

    @Test
    fun reopeningTheLeaveConfirmStartsFromEntriesKeptAgain() = runTest {
        viewModel.events.test {
            viewModel.onAction(BondSettingsAction.OnLeaveClick)
            viewModel.onAction(BondSettingsAction.OnLeaveWithdrawToggle)
            viewModel.onAction(BondSettingsAction.OnEditorClosed)
            viewModel.onAction(BondSettingsAction.OnLeaveClick)
            cancelAndIgnoreRemainingEvents()
        }
        assertFalse(viewModel.state.value.withdrawOnLeave)
    }

    @Test
    fun aFailedLeaveKeepsThePersonHereWithTheReasonAndTheirChoice() = runTest {
        repo.leaveAnswers += Result.Failure(DataError.Network.NoInternet)
        viewModel.events.test {
            viewModel.onAction(BondSettingsAction.OnLeaveClick)
            assertEquals(BondSettingsEvent.OpenLeave, awaitItem())
            viewModel.onAction(BondSettingsAction.OnLeaveWithdrawToggle)
            viewModel.onAction(BondSettingsAction.OnLeaveConfirm)
            expectNoEvents()
        }
        assertEquals(SettingsError(SettingsField.Leave, generic), viewModel.state.value.error)
        assertFalse(viewModel.state.value.leaving)
        assertTrue(viewModel.state.value.withdrawOnLeave)
    }

    @Test
    fun leavingABondThatAlreadyEndedStillEnds() = runTest {
        repo.leaveAnswers += apiError(409, ApiErrorCode.BOND_ARCHIVED)
        viewModel.events.test {
            viewModel.onAction(BondSettingsAction.OnLeaveClick)
            assertEquals(BondSettingsEvent.OpenLeave, awaitItem())
            viewModel.onAction(BondSettingsAction.OnLeaveConfirm)
            assertEquals(BondSettingsEvent.Ended, awaitItem())
        }
    }

    @Test
    fun aWithdrawingLeaveOnABondThatAlreadyEndedSaysNothingWasWithdrawn() = runTest {
        repo.leaveAnswers += apiError(409, ApiErrorCode.BOND_ARCHIVED)
        viewModel.events.test {
            viewModel.onAction(BondSettingsAction.OnLeaveClick)
            assertEquals(BondSettingsEvent.OpenLeave, awaitItem())
            viewModel.onAction(BondSettingsAction.OnLeaveWithdrawToggle)
            viewModel.onAction(BondSettingsAction.OnLeaveConfirm)
            expectNoEvents()
        }
        assertEquals(SettingsError(SettingsField.Leave, UiText.Resource(Res.string.settings_leave_error_ended)), viewModel.state.value.error)
    }

    // ----- sign out -----

    @Test
    fun signOutEndsTheSessionOnce() {
        viewModel.onAction(BondSettingsAction.OnSignOutClick)
        viewModel.onAction(BondSettingsAction.OnSignOutClick)

        assertTrue(viewModel.state.value.signingOut)
        assertEquals(1, auth.logouts)
    }
}
