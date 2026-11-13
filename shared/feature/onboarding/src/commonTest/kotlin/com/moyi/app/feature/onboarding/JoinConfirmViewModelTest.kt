package com.moyi.app.feature.onboarding

import app.cash.turbine.test
import com.moyi.app.core.ApiErrorCode
import com.moyi.app.core.DataError
import com.moyi.app.core.Result
import com.moyi.app.designsystem.presentation.UiText
import com.moyi.app.domain.bond.Bond
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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JoinConfirmViewModelTest : MainDispatcherTest() {
    private val bonds = FakeBondRepository()
    private val exit = FakeEmptyBondExit()
    private val cache = FakeTodayCache()
    private val viewModel = JoinConfirmViewModel("K7Q2PD", PREVIEW, leaving = null, bonds, exit, cache)

    /** Opened from Esther's own bond, which nobody has joined: Join also ends it. */
    private val fromEmpty = LeavingBond(id = "empty-1", name = "Esther's space")
    private fun fromEmptyBond() = JoinConfirmViewModel("K7Q2PD", PREVIEW, fromEmpty, bonds, exit, cache)

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
        val vm = JoinConfirmViewModel("K7Q2PD", PREVIEW.copy(bondType = BondType.PARENT_CHILD), null, bonds, exit, cache)
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
    fun aJoinForgetsTheCachedDaySoTodayNeverDrawsTheEmptyBondFirst() = runTest {
        bonds.acceptResult = Result.Success(bond(BondStatus.ACTIVE, invite = null).copy(id = "joined-1"))
        val vm = fromEmptyBond()
        vm.events.test {
            vm.onAction(JoinConfirmAction.OnJoinClick)
            assertEquals(JoinConfirmEvent.Finish, awaitItem())
        }
        assertEquals(1, cache.clears, "the cached day is the empty bond's, not the joined one's")
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
        assertEquals(0, cache.clears, "the empty bond is still the open one, so its cached day stays")
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

    // A join whose answer was lost (code review of #101, finding 2).

    /** Esther's own bond, still waiting, as `GET /bonds` lists it. */
    private val emptyListed = Bond(
        id = "empty-1",
        name = "Esther's space",
        type = BondType.FRIENDS,
        status = BondStatus.PENDING_MEMBER,
        invite = null,
        activeMembers = 1,
    )

    /** Amara's bond, as it lists once Esther is in it: the invite's name, type and inviter. */
    private val joinedListed = Bond(
        id = "joined-1",
        name = PREVIEW.bondName,
        type = PREVIEW.bondType,
        status = BondStatus.ACTIVE,
        invite = null,
        partnerName = PREVIEW.inviterName,
        activeMembers = 2,
    )

    @Test
    fun anAcceptWhoseAnswerWasLostButJoinedFinishesAndEndsTheEmptyBond() = runTest {
        bonds.bondsResult = Result.Success(listOf(emptyListed))
        val vm = fromEmptyBond()
        // The server joined, but the answer never arrived.
        bonds.acceptResult = Result.Failure(DataError.Network.Timeout)
        bonds.bondsResult = Result.Success(listOf(joinedListed, emptyListed))
        vm.events.test {
            vm.onAction(JoinConfirmAction.OnJoinClick)
            assertEquals(JoinConfirmEvent.Finish, awaitItem())
        }
        assertEquals(listOf(FakeEmptyBondExit.Leave(emptyBondId = "empty-1", joinedBondId = "joined-1")), exit.leaves)
        assertEquals(1, cache.clears)
    }

    @Test
    fun spentOnTheNextTapAfterALostAnswerIsTheJoinThatHappened() = runTest {
        bonds.bondsResult = Result.Success(listOf(emptyListed))
        val vm = fromEmptyBond()
        // First tap: no answer, and the bonds cannot be read either, so nothing is assumed.
        bonds.acceptResult = Result.Failure(DataError.Network.NoInternet)
        bonds.bondsResult = Result.Failure(DataError.Network.NoInternet)
        vm.onAction(JoinConfirmAction.OnJoinClick)
        assertTrue(exit.leaves.isEmpty())
        assertFalse(vm.state.value.joining, "they can try again")
        // Second tap: the invite is spent, by that first accept.
        bonds.acceptResult = Result.Failure(problem(404, ApiErrorCode.INVITE_NOT_USABLE))
        bonds.bondsResult = Result.Success(listOf(joinedListed, emptyListed))
        vm.events.test {
            vm.onAction(JoinConfirmAction.OnJoinClick)
            assertEquals(JoinConfirmEvent.Finish, awaitItem())
        }
        assertEquals(listOf(FakeEmptyBondExit.Leave(emptyBondId = "empty-1", joinedBondId = "joined-1")), exit.leaves)
    }

    @Test
    fun spentWithNoLostAnswerBeforeItIsCannotBeUsedAndTheBondsAreNotReadAgain() = runTest {
        bonds.bondsResult = Result.Success(listOf(emptyListed))
        val vm = fromEmptyBond()
        val readsBefore = bonds.bondsCalls
        bonds.acceptResult = Result.Failure(problem(404, ApiErrorCode.INVITE_NOT_USABLE))
        bonds.bondsResult = Result.Success(listOf(joinedListed, emptyListed))
        vm.events.test {
            vm.onAction(JoinConfirmAction.OnJoinClick)
            assertEquals(JoinConfirmEvent.NavigateToCannotBeUsed, awaitItem())
        }
        assertEquals(readsBefore, bonds.bondsCalls)
        assertTrue(exit.leaves.isEmpty())
    }

    @Test
    fun aLostAnswerIsNeverTakenForAJoinOnDoubt() = runTest {
        val doubtful = listOf(
            // The bond was there before the accept: not this join.
            listOf(joinedListed, emptyListed) to listOf(joinedListed, emptyListed),
            // Not the invite's bond (another name).
            listOf(emptyListed) to listOf(joinedListed.copy(name = "Someone else"), emptyListed),
            // Still waiting for its second member: not a bond someone joined.
            listOf(emptyListed) to listOf(joinedListed.copy(status = BondStatus.PENDING_MEMBER), emptyListed),
            // Two that would fit: no way to tell which.
            listOf(emptyListed) to listOf(joinedListed, joinedListed.copy(id = "joined-2"), emptyListed),
            // The member list did not name the partner.
            listOf(emptyListed) to listOf(joinedListed.copy(partnerName = null), emptyListed),
        )
        doubtful.forEach { (before, after) ->
            bonds.bondsResult = Result.Success(before)
            val vm = fromEmptyBond()
            bonds.acceptResult = Result.Failure(DataError.Network.Timeout)
            bonds.bondsResult = Result.Success(after)
            vm.events.test {
                vm.onAction(JoinConfirmAction.OnJoinClick)
                expectNoEvents()
            }
            assertFalse(vm.state.value.joining, after.toString())
            assertTrue(vm.state.value.failure != null, after.toString())
        }
        assertTrue(exit.leaves.isEmpty())
        assertEquals(0, cache.clears)
    }

    @Test
    fun withNoReadingFromBeforeTheAcceptNothingIsAssumed() = runTest {
        // Read once on opening and once more before the accept; both fail.
        bonds.bondsResult = Result.Failure(DataError.Network.NoInternet)
        val vm = fromEmptyBond()
        bonds.acceptResult = Result.Failure(DataError.Network.Timeout)
        vm.onAction(JoinConfirmAction.OnJoinClick)
        bonds.bondsResult = Result.Success(listOf(joinedListed, emptyListed))
        bonds.acceptResult = Result.Failure(problem(404, ApiErrorCode.INVITE_NOT_USABLE))
        vm.events.test {
            vm.onAction(JoinConfirmAction.OnJoinClick)
            assertEquals(JoinConfirmEvent.NavigateToCannotBeUsed, awaitItem())
        }
        assertTrue(exit.leaves.isEmpty())
    }

    @Test
    fun withNoEmptyBondTheBondsAreNeverRead() = runTest {
        bonds.acceptResult = Result.Failure(DataError.Network.Timeout)
        viewModel.onAction(JoinConfirmAction.OnJoinClick)
        assertEquals(0, bonds.bondsCalls)
    }
}
