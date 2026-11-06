package com.moyi.app.feature.bond

import app.cash.turbine.test
import com.moyi.app.core.ApiErrorCode
import com.moyi.app.core.DataError
import com.moyi.app.core.Result
import com.moyi.app.designsystem.presentation.UiText
import com.moyi.app.domain.bond.BondStatus
import com.moyi.app.domain.bond.PendingDeletionRequest
import com.moyi.app.feature.bond.resources.Res
import com.moyi.app.feature.bond.resources.settings_error_generic
import com.moyi.app.feature.bond.settings.BondSettingsAction
import com.moyi.app.feature.bond.settings.BondSettingsEvent
import com.moyi.app.feature.bond.settings.BondSettingsViewModel
import com.moyi.app.feature.bond.settings.CloseBoxStep
import com.moyi.app.feature.bond.settings.SettingsError
import com.moyi.app.feature.bond.settings.SettingsField
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Block and leave (FR-029, FR-029a) and close the box (FR-028) on [BondSettingsViewModel].
 * Leave's own tests are with the rest of settings, in [BondSettingsViewModelTest].
 */
class EndingViewModelTest : MainDispatcherTest() {
    private val repo = FakeBondSettingsRepository(settingsV1.copy(partnerName = "Tunde"))
    private val viewModel by lazy { BondSettingsViewModel("b1", repo, FakeAuthRepository(), FakeBondRepository(repo), zoneIds = { emptySet() }) }

    private val generic = UiText.Resource(Res.string.settings_error_generic)

    // ----- block and leave -----

    @Test
    fun blockOpensItsConfirmWithWithdrawOnAndDoesNothingUntilConfirmed() = runTest {
        viewModel.events.test {
            viewModel.onAction(BondSettingsAction.OnBlockClick)
            assertEquals(BondSettingsEvent.OpenBlock, awaitItem())
        }
        assertTrue(viewModel.state.value.withdrawOnBlock, "withdrawal is pre-selected on block (`26` §3.3)")
        assertTrue(repo.blockWithdraws.isEmpty())
    }

    @Test
    fun aConfirmedBlockWithdrawsByDefaultThenEnds() = runTest {
        viewModel.events.test {
            viewModel.onAction(BondSettingsAction.OnBlockClick)
            assertEquals(BondSettingsEvent.OpenBlock, awaitItem())
            viewModel.onAction(BondSettingsAction.OnBlockConfirm)
            assertEquals(BondSettingsEvent.Ended, awaitItem())
        }
        assertEquals(listOf(true), repo.blockWithdraws)
        assertEquals(0, repo.leaves, "a block is its own route, never a leave")
    }

    @Test
    fun aBlockWithTheRowUntickedKeepsTheEntries() = runTest {
        viewModel.events.test {
            viewModel.onAction(BondSettingsAction.OnBlockClick)
            assertEquals(BondSettingsEvent.OpenBlock, awaitItem())
            viewModel.onAction(BondSettingsAction.OnBlockWithdrawToggle)
            viewModel.onAction(BondSettingsAction.OnBlockConfirm)
            assertEquals(BondSettingsEvent.Ended, awaitItem())
        }
        assertEquals(listOf(false), repo.blockWithdraws)
    }

    @Test
    fun whileTheBlockIsInFlightItSaysBlockingAndTheRowCannotChange() = runTest {
        val gate = CompletableDeferred<Unit>()
        repo.gate = gate
        viewModel.onAction(BondSettingsAction.OnBlockClick)
        viewModel.onAction(BondSettingsAction.OnBlockConfirm)

        assertTrue(viewModel.state.value.blocking)
        viewModel.onAction(BondSettingsAction.OnBlockWithdrawToggle)
        viewModel.onAction(BondSettingsAction.OnBlockConfirm)
        assertTrue(viewModel.state.value.withdrawOnBlock, "the choice is fixed once it is sent")
        gate.complete(Unit)
        assertEquals(1, repo.blockWithdraws.size, "one block, however often it is tapped")
    }

    @Test
    fun aFailedBlockKeepsThePersonHereWithTheReasonAndTheirChoice() = runTest {
        repo.blockAnswers += Result.Failure(DataError.Network.NoInternet)
        viewModel.events.test {
            viewModel.onAction(BondSettingsAction.OnBlockClick)
            assertEquals(BondSettingsEvent.OpenBlock, awaitItem())
            viewModel.onAction(BondSettingsAction.OnBlockWithdrawToggle)
            viewModel.onAction(BondSettingsAction.OnBlockConfirm)
            expectNoEvents()
        }
        assertEquals(SettingsError(SettingsField.Block, generic), viewModel.state.value.error)
        assertFalse(viewModel.state.value.blocking)
        assertFalse(viewModel.state.value.withdrawOnBlock)
    }

    @Test
    fun aBlockOnABondThePersonIsNoLongerInEnds() = runTest {
        repo.blockAnswers += apiError(404, ApiErrorCode.NOT_FOUND)
        viewModel.events.test {
            viewModel.onAction(BondSettingsAction.OnBlockClick)
            assertEquals(BondSettingsEvent.OpenBlock, awaitItem())
            viewModel.onAction(BondSettingsAction.OnBlockConfirm)
            assertEquals(BondSettingsEvent.Ended, awaitItem())
        }
    }

    @Test
    fun blockIsNotOfferedWhenTheOtherMemberIsNotKnown() = runTest {
        repo.server = settingsV1.copy(partnerName = null)
        viewModel.events.test {
            viewModel.onAction(BondSettingsAction.OnBlockClick)
            expectNoEvents()
        }
    }

    // ----- close the box -----

    @Test
    fun theFirstAskWaitsForTheOtherMember() = runTest {
        viewModel.events.test {
            viewModel.onAction(BondSettingsAction.OnCloseBoxClick)
            assertEquals(BondSettingsEvent.OpenCloseBox, awaitItem())
            assertEquals(CloseBoxStep.Ask, viewModel.state.value.closeBoxStep)

            viewModel.onAction(BondSettingsAction.OnCloseBoxConfirm)
            expectNoEvents()
        }
        assertEquals(1, repo.deletionRequests)
        assertEquals(CloseBoxStep.Asked, viewModel.state.value.closeBoxStep)
        assertEquals(askLapses, viewModel.state.value.settings?.pendingDeletion?.expiresAt)
    }

    @Test
    fun theAskerIsNeverOfferedToAgreeToTheirOwnAsk() {
        repo.server = repo.server.copy(pendingDeletion = PendingDeletionRequest(requestedByMe = true))
        assertEquals(CloseBoxStep.Asked, viewModel.state.value.closeBoxStep)

        viewModel.onAction(BondSettingsAction.OnCloseBoxConfirm)

        assertEquals(0, repo.deletionRequests, "one person asking twice is not two people agreeing")
    }

    @Test
    fun agreeingToTheOtherMembersAskStartsTheCoolingOffWithItsDate() {
        repo.server = repo.server.copy(pendingDeletion = PendingDeletionRequest(requestedByMe = false))
        assertEquals(CloseBoxStep.Agree, viewModel.state.value.closeBoxStep)

        viewModel.onAction(BondSettingsAction.OnCloseBoxConfirm)

        val state = viewModel.state.value
        assertEquals(CloseBoxStep.CoolingOff, state.closeBoxStep)
        assertEquals(BondStatus.PENDING_DELETION, state.settings?.status)
        assertEquals(coolingOffEnds, state.settings?.deletionScheduledFor)
    }

    @Test
    fun keepingTheBoxCallsOffTheCoolingOffAndReturnsToTheRoot() = runTest {
        repo.server = repo.server.copy(status = BondStatus.PENDING_DELETION, deletionScheduledFor = coolingOffEnds)
        assertEquals(CloseBoxStep.CoolingOff, viewModel.state.value.closeBoxStep)
        viewModel.events.test {
            viewModel.onAction(BondSettingsAction.OnCloseBoxWithdraw)
            assertEquals(BondSettingsEvent.Saved, awaitItem())
        }
        assertEquals(1, repo.deletionCancels)
        assertEquals(BondStatus.ACTIVE, viewModel.state.value.settings?.status)
        assertEquals(CloseBoxStep.Ask, viewModel.state.value.closeBoxStep)
    }

    @Test
    fun cancellingAnAskThatIsAlreadyGoneIsStillDone() = runTest {
        repo.server = repo.server.copy(pendingDeletion = PendingDeletionRequest(requestedByMe = true))
        repo.deletionCancelAnswers += apiError(404, ApiErrorCode.NOT_FOUND)
        viewModel.state.value
        repo.server = repo.server.copy(pendingDeletion = null)
        viewModel.events.test {
            viewModel.onAction(BondSettingsAction.OnCloseBoxWithdraw)
            assertEquals(BondSettingsEvent.Saved, awaitItem())
        }
        assertEquals(CloseBoxStep.Ask, viewModel.state.value.closeBoxStep)
    }

    @Test
    fun aFailedAskSaysSoAndStaysOnAsk() {
        repo.deletionAnswers += Result.Failure(DataError.Network.Timeout)
        viewModel.onAction(BondSettingsAction.OnCloseBoxConfirm)

        assertEquals(SettingsError(SettingsField.CloseBox, generic), viewModel.state.value.error)
        assertEquals(CloseBoxStep.Ask, viewModel.state.value.closeBoxStep)
    }

    @Test
    fun askingOnABondThatHasEndedEnds() = runTest {
        repo.deletionAnswers += apiError(409, ApiErrorCode.BOND_ARCHIVED)
        viewModel.events.test {
            viewModel.onAction(BondSettingsAction.OnCloseBoxConfirm)
            assertEquals(BondSettingsEvent.Ended, awaitItem())
        }
    }

    @Test
    fun duringTheCoolingOffNothingElseChangesButLeavingStillWorks() = runTest {
        repo.server = repo.server.copy(status = BondStatus.PENDING_DELETION, deletionScheduledFor = coolingOffEnds)
        viewModel.onAction(BondSettingsAction.OnRestDaysToggle)
        viewModel.onAction(BondSettingsAction.OnRevealTimeClick)
        assertTrue(repo.updates.isEmpty(), "the cooling-off takes no other write (states.md §9)")
        assertFalse(viewModel.state.value.pickingRevealTime)

        viewModel.events.test {
            viewModel.onAction(BondSettingsAction.OnLeaveClick)
            assertEquals(BondSettingsEvent.OpenLeave, awaitItem())
        }
    }
}
