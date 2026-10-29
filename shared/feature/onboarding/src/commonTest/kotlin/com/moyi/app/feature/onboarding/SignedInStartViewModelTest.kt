package com.moyi.app.feature.onboarding

import app.cash.turbine.test
import com.moyi.app.core.DataError
import com.moyi.app.core.Result
import com.moyi.app.domain.bond.BondStatus
import com.moyi.app.feature.onboarding.start.SignedInStartAction
import com.moyi.app.feature.onboarding.start.SignedInStartEvent
import com.moyi.app.feature.onboarding.start.SignedInStartState
import com.moyi.app.feature.onboarding.start.SignedInStartViewModel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SignedInStartViewModelTest : MainDispatcherTest() {
    private val bonds = FakeBondRepository()
    private val account = FakeAccountRepository()

    private suspend fun firstEvent(): SignedInStartEvent {
        val viewModel = SignedInStartViewModel(bonds, account)
        var event: SignedInStartEvent? = null
        viewModel.events.test { event = awaitItem() }
        return event!!
    }

    @Test
    fun anOpenBondGoesToTodayWhetherOrNotThePartnerHasJoined() = runTest {
        for (status in listOf(BondStatus.PENDING_MEMBER, BondStatus.ACTIVE, BondStatus.PENDING_DELETION)) {
            bonds.bondsResult = Result.Success(listOf(bond(status)))
            assertEquals(SignedInStartEvent.NavigateToToday, firstEvent(), status.name)
        }
    }

    @Test
    fun anOpenBondWinsEvenIfTheAccountCouldNotBeRead() = runTest {
        bonds.bondsResult = Result.Success(listOf(bond(BondStatus.ACTIVE)))
        account.result = Result.Failure(DataError.Network.NoInternet)
        assertEquals(SignedInStartEvent.NavigateToToday, firstEvent())
    }

    @Test
    fun noBondAndVerifiedIsOnboarding() = runTest {
        assertEquals(SignedInStartEvent.NavigateToOnboarding, firstEvent())
    }

    @Test
    fun onlyEndedBondsCountAsNone() = runTest {
        bonds.bondsResult = Result.Success(listOf(bond(BondStatus.ARCHIVED), bond(BondStatus.DELETED)))
        assertEquals(SignedInStartEvent.NavigateToOnboarding, firstEvent())
    }

    @Test
    fun noBondAndUnverifiedIsTheGate() = runTest {
        account.result = Result.Success(user(verified = false))
        assertEquals(SignedInStartEvent.NavigateToGate, firstEvent())
    }

    @Test
    fun aFailureShowsTheGenericErrorAndTryAgainAsksAgain() = runTest {
        bonds.bondsResult = Result.Failure(DataError.Network.NoInternet)
        val viewModel = SignedInStartViewModel(bonds, account)
        assertEquals(SignedInStartState.Failed, viewModel.state.value)

        bonds.bondsResult = Result.Success(emptyList())
        viewModel.events.test {
            viewModel.onAction(SignedInStartAction.OnRetryClick)
            assertEquals(SignedInStartEvent.NavigateToOnboarding, awaitItem())
        }
        assertEquals(2, bonds.bondsCalls)
    }

    @Test
    fun noBondAndAnUnreadableAccountIsAFailureNotAGuess() = runTest {
        account.result = Result.Failure(DataError.Network.Server(500))
        val viewModel = SignedInStartViewModel(bonds, account)
        assertEquals(SignedInStartState.Failed, viewModel.state.value)
    }
}
