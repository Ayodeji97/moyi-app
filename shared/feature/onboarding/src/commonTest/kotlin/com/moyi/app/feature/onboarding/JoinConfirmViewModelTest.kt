package com.moyi.app.feature.onboarding

import app.cash.turbine.test
import com.moyi.app.core.ApiErrorCode
import com.moyi.app.core.DataError
import com.moyi.app.core.Result
import com.moyi.app.designsystem.presentation.UiText
import com.moyi.app.domain.bond.BondStatus
import com.moyi.app.domain.bond.BondType
import com.moyi.app.feature.onboarding.common.RelationshipType
import com.moyi.app.feature.onboarding.join.JoinConfirmAction
import com.moyi.app.feature.onboarding.join.JoinConfirmEvent
import com.moyi.app.feature.onboarding.join.JoinConfirmViewModel
import com.moyi.app.feature.onboarding.join.LeavingBond
import com.moyi.app.feature.onboarding.resources.Res
import com.moyi.app.feature.onboarding.resources.bond_create_error
import com.moyi.app.feature.onboarding.resources.join_error_limit
import com.moyi.app.feature.onboarding.resources.join_error_own_code
import com.moyi.app.feature.onboarding.resources.onboarding_error_generic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JoinConfirmViewModelTest : MainDispatcherTest() {
    private val bonds = FakeBondRepository()
    private val exit = FakeEmptyBondExit()
    private val viewModel = JoinConfirmViewModel("K7Q2PD", PREVIEW, leaving = null, bonds, exit)

    /** Opened from Esther's own bond, which nobody has joined: Join also ends it. */
    private val fromEmpty = LeavingBond(id = "empty-1", name = "Esther's space")
    private fun fromEmptyBond() = JoinConfirmViewModel("K7Q2PD", PREVIEW, fromEmpty, bonds, exit)

    @Test
    fun itShowsThePreviewWithoutResolvingAgain() {
        val state = viewModel.state.value
        assertEquals("Amara", state.inviterName)
        assertEquals("Amara and Tunde", state.bondName)
        assertEquals(RelationshipType.Couple, state.type)
        assertEquals(emptyList(), bonds.resolveCalls)
    }

    @Test
    fun parentAndChildReadsAsFamilyNeverACrash() {
        val vm = JoinConfirmViewModel("K7Q2PD", PREVIEW.copy(bondType = BondType.PARENT_CHILD), null, bonds, exit)
        assertEquals(RelationshipType.Family, vm.state.value.type)
    }

    @Test
    fun joiningAcceptsTheCodeAndFinishes() = runTest {
        viewModel.events.test {
            viewModel.onAction(JoinConfirmAction.OnJoinClick)
            assertEquals(JoinConfirmEvent.Finish, awaitItem())
        }
        assertEquals(listOf("K7Q2PD"), bonds.acceptCalls)
    }

    @Test
    fun whileJoiningTheButtonSaysSoAndASecondTapSpendsNothing() = runTest {
        bonds.gate = CompletableDeferred()
        viewModel.onAction(JoinConfirmAction.OnJoinClick)
        assertTrue(viewModel.state.value.joining)
        viewModel.onAction(JoinConfirmAction.OnJoinClick)
        assertEquals(1, bonds.acceptCalls.size)
        bonds.gate!!.complete(Unit)
    }

    @Test
    fun afterJoiningTheButtonStaysJoiningSoNoSecondAcceptIsSent() = runTest {
        viewModel.events.test {
            viewModel.onAction(JoinConfirmAction.OnJoinClick)
            assertEquals(JoinConfirmEvent.Finish, awaitItem())
            viewModel.onAction(JoinConfirmAction.OnJoinClick)
            expectNoEvents()
        }
        assertTrue(viewModel.state.value.joining)
        assertEquals(1, bonds.acceptCalls.size)
    }

    @Test
    fun aCodeThatStoppedWorkingGoesToCannotBeUsed() = runTest {
        for (refusal in listOf(problem(404, ApiErrorCode.INVITE_NOT_USABLE), problem(422, ApiErrorCode.VALIDATION_FAILED, "code"))) {
            bonds.acceptResult = Result.Failure(refusal)
            viewModel.events.test {
                viewModel.onAction(JoinConfirmAction.OnJoinClick)
                assertEquals(JoinConfirmEvent.NavigateToCannotBeUsed, awaitItem())
            }
        }
    }

    @Test
    fun alreadyAMemberIsWhereJoiningWouldLeadToday() = runTest {
        bonds.acceptResult = Result.Failure(problem(409, ApiErrorCode.ALREADY_MEMBER))
        viewModel.events.test {
            viewModel.onAction(JoinConfirmAction.OnJoinClick)
            assertEquals(JoinConfirmEvent.Finish, awaitItem())
        }
    }

    @Test
    fun anUnverifiedAccountGoesToTheGate() = runTest {
        bonds.acceptResult = Result.Failure(problem(403, ApiErrorCode.EMAIL_NOT_VERIFIED))
        viewModel.events.test {
            viewModel.onAction(JoinConfirmAction.OnJoinClick)
            assertEquals(JoinConfirmEvent.NavigateToGate, awaitItem())
        }
    }

    @Test
    fun theBondLimitIsSaidInline() {
        bonds.acceptResult = Result.Failure(problem(409, ApiErrorCode.BOND_LIMIT_REACHED))
        viewModel.onAction(JoinConfirmAction.OnJoinClick)
        assertEquals(UiText.Resource(Res.string.join_error_limit), viewModel.state.value.failure)
    }

    @Test
    fun offlineAndServerErrorsAreInlineAndTryAgainWorks() = runTest {
        bonds.acceptResult = Result.Failure(DataError.Network.Timeout)
        viewModel.onAction(JoinConfirmAction.OnJoinClick)
        assertEquals(UiText.Resource(Res.string.bond_create_error), viewModel.state.value.failure)
        bonds.acceptResult = Result.Failure(DataError.Network.Server(500))
        viewModel.onAction(JoinConfirmAction.OnJoinClick)
        assertEquals(UiText.Resource(Res.string.onboarding_error_generic), viewModel.state.value.failure)

        bonds.acceptResult = Result.Success(bond())
        viewModel.events.test {
            viewModel.onAction(JoinConfirmAction.OnJoinClick)
            assertEquals(JoinConfirmEvent.Finish, awaitItem())
        }
        assertNull(viewModel.state.value.failure)
    }

    @Test
    fun notNowAndBackReturnToTheCodeUnspent() = runTest {
        viewModel.events.test {
            viewModel.onAction(JoinConfirmAction.OnNotNowClick)
            assertEquals(JoinConfirmEvent.NavigateBack, awaitItem())
            viewModel.onAction(JoinConfirmAction.OnBackClick)
            assertEquals(JoinConfirmEvent.NavigateBack, awaitItem())
        }
        assertEquals(emptyList(), bonds.acceptCalls)
    }

    @Test
    fun withNoEmptyBondNothingIsSaidAndNothingIsLeft() = runTest {
        assertNull(viewModel.state.value.leaving)
        viewModel.events.test {
            viewModel.onAction(JoinConfirmAction.OnJoinClick)
            assertEquals(JoinConfirmEvent.Finish, awaitItem())
        }
        assertTrue(exit.leaves.isEmpty())
    }

    @Test
    fun fromAnEmptyBondTheScreenSaysJoinEndsIt() {
        assertEquals(fromEmpty, fromEmptyBond().state.value.leaving)
    }

    @Test
    fun fromAnEmptyBondItJoinsFirstThenEndsTheEmptyBond() = runTest {
        // The accept answers with the joined bond: its id is the one kept.
        bonds.acceptResult = Result.Success(bond(BondStatus.ACTIVE, invite = null).copy(id = "joined-1"))
        val vm = fromEmptyBond()
        vm.events.test {
            vm.onAction(JoinConfirmAction.OnJoinClick)
            assertEquals(JoinConfirmEvent.Finish, awaitItem())
        }
        assertEquals(listOf("K7Q2PD"), bonds.acceptCalls)
        assertEquals(listOf(FakeEmptyBondExit.Leave(emptyBondId = "empty-1", joinedBondId = "joined-1")), exit.leaves)
    }

    @Test
    fun aJoinThatFailsEndsNothing() = runTest {
        val vm = fromEmptyBond()
        for (refusal in listOf(
            DataError.Network.NoInternet,
            DataError.Network.Server(503),
            problem(409, ApiErrorCode.BOND_LIMIT_REACHED),
            problem(404, ApiErrorCode.INVITE_NOT_USABLE),
            problem(403, ApiErrorCode.EMAIL_NOT_VERIFIED),
        )) {
            bonds.acceptResult = Result.Failure(refusal)
            vm.onAction(JoinConfirmAction.OnJoinClick)
        }
        assertEquals(5, bonds.acceptCalls.size)
        assertTrue(exit.leaves.isEmpty(), "the empty bond is ended only after a join that succeeded")
    }

    @Test
    fun theirOwnCodeFromAnEmptyBondIsSaidAndEndsNothing() = runTest {
        bonds.acceptResult = Result.Failure(problem(409, ApiErrorCode.ALREADY_MEMBER))
        val vm = fromEmptyBond()
        vm.events.test {
            vm.onAction(JoinConfirmAction.OnJoinClick)
            expectNoEvents()
        }
        assertEquals(UiText.Resource(Res.string.join_error_own_code), vm.state.value.failure)
        assertEquals(false, vm.state.value.joining, "they can go back and share it instead")
        assertTrue(exit.leaves.isEmpty())
    }
}
