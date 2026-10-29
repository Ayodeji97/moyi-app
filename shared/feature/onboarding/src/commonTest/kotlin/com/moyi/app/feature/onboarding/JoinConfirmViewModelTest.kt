package com.moyi.app.feature.onboarding

import app.cash.turbine.test
import com.moyi.app.core.ApiErrorCode
import com.moyi.app.core.DataError
import com.moyi.app.core.Result
import com.moyi.app.designsystem.presentation.UiText
import com.moyi.app.domain.bond.BondType
import com.moyi.app.feature.onboarding.common.RelationshipType
import com.moyi.app.feature.onboarding.join.JoinConfirmAction
import com.moyi.app.feature.onboarding.join.JoinConfirmEvent
import com.moyi.app.feature.onboarding.join.JoinConfirmViewModel
import com.moyi.app.feature.onboarding.resources.Res
import com.moyi.app.feature.onboarding.resources.bond_create_error
import com.moyi.app.feature.onboarding.resources.join_error_limit
import com.moyi.app.feature.onboarding.resources.onboarding_error_generic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JoinConfirmViewModelTest : MainDispatcherTest() {
    private val bonds = FakeBondRepository()
    private val viewModel = JoinConfirmViewModel("K7Q2PD", PREVIEW, bonds)

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
        val vm = JoinConfirmViewModel("K7Q2PD", PREVIEW.copy(bondType = BondType.PARENT_CHILD), bonds)
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
}
