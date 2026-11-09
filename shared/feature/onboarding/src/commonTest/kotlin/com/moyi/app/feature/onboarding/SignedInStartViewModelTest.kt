package com.moyi.app.feature.onboarding

import app.cash.turbine.test
import com.moyi.app.core.DataError
import com.moyi.app.core.Result
import com.moyi.app.domain.bond.BondStatus
import com.moyi.app.domain.today.BondDayStatus
import com.moyi.app.domain.today.CachedDay
import com.moyi.app.domain.today.Today
import com.moyi.app.domain.today.TodayCache
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
    private val cache = FakeTodayCache()

    private suspend fun firstEvent(): SignedInStartEvent {
        val viewModel = SignedInStartViewModel(bonds, account, cache)
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
    fun aNewestBondThatHasEndedIsTheArchivedScreenNamingTheOtherMember() = runTest {
        bonds.bondsResult = Result.Success(listOf(bond(BondStatus.ARCHIVED).copy(partnerName = "Amara"), bond(BondStatus.DELETED)))
        assertEquals(SignedInStartEvent.NavigateToArchived("Amara"), firstEvent())
    }

    @Test
    fun theArchivedScreenNeedsOnlyTheBondNotTheAccount() = runTest {
        bonds.bondsResult = Result.Success(listOf(bond(BondStatus.ARCHIVED)))
        account.result = Result.Failure(DataError.Network.NoInternet)
        assertEquals(SignedInStartEvent.NavigateToArchived(null), firstEvent())
    }

    @Test
    fun anOpenBondWinsOverAnEndedOne() = runTest {
        bonds.bondsResult = Result.Success(listOf(bond(BondStatus.ARCHIVED), bond(BondStatus.ACTIVE)))
        assertEquals(SignedInStartEvent.NavigateToToday, firstEvent())
    }

    @Test
    fun aBondInItsCoolingOffIsStillOpen() = runTest {
        bonds.bondsResult = Result.Success(listOf(bond(BondStatus.PENDING_DELETION)))
        assertEquals(SignedInStartEvent.NavigateToToday, firstEvent())
    }

    @Test
    fun aDeletedBondCountsAsNone() = runTest {
        bonds.bondsResult = Result.Success(listOf(bond(BondStatus.DELETED)))
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
        val viewModel = SignedInStartViewModel(bonds, account, cache)
        assertEquals(SignedInStartState.Failed, viewModel.state.value)

        bonds.bondsResult = Result.Success(emptyList())
        viewModel.events.test {
            viewModel.onAction(SignedInStartAction.OnRetryClick)
            assertEquals(SignedInStartEvent.NavigateToOnboarding, awaitItem())
        }
        assertEquals(2, bonds.bondsCalls)
    }

    @Test
    fun getHelpOnTheGenericErrorOpensSupportWithoutAskingAgain() = runTest {
        bonds.bondsResult = Result.Failure(DataError.Network.NoInternet)
        val viewModel = SignedInStartViewModel(bonds, account, cache)

        viewModel.events.test {
            viewModel.onAction(SignedInStartAction.OnGetHelpClick)
            assertEquals(SignedInStartEvent.NavigateToSupport, awaitItem())
        }
        assertEquals(SignedInStartState.Failed, viewModel.state.value)
        assertEquals(1, bonds.bondsCalls)
    }

    @Test
    fun noBondAndAnUnreadableAccountIsAFailureNotAGuess() = runTest {
        account.result = Result.Failure(DataError.Network.Server(500))
        val viewModel = SignedInStartViewModel(bonds, account, cache)
        assertEquals(SignedInStartState.Failed, viewModel.state.value)
    }

    // ---- A cached day ----

    @Test
    fun aCachedDayGoesToTodayAtOnceWithoutAskingTheServer() = runTest {
        cache.stored = CachedDay(bond(BondStatus.ACTIVE), Today("2026-09-14", BondDayStatus.PARTIAL, null, null, null))
        // Offline: without the cache this would be the Generic error.
        bonds.bondsResult = Result.Failure(DataError.Network.NoInternet)
        account.result = Result.Failure(DataError.Network.NoInternet)

        assertEquals(SignedInStartEvent.NavigateToToday, firstEvent())
        assertEquals(0, bonds.bondsCalls, "Today refreshes behind the cached day; nothing is asked here")
    }

    @Test
    fun withNoCachedDayTheServerStillDecides() = runTest {
        bonds.bondsResult = Result.Success(listOf(bond(BondStatus.ARCHIVED)))
        assertEquals(SignedInStartEvent.NavigateToArchived(null), firstEvent())
        assertEquals(1, bonds.bondsCalls)
    }
}

/** What the device has cached for Today; null is nothing (a new install, a new person, or no open bond when last seen). */
private class FakeTodayCache(var stored: CachedDay? = null) : TodayCache {
    override suspend fun read(): CachedDay? = stored
    override suspend fun write(day: CachedDay) {
        stored = day
    }
    override suspend fun clear() {
        stored = null
    }
}
