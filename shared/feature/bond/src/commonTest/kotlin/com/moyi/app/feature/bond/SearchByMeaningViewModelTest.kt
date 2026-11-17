package com.moyi.app.feature.bond

import app.cash.turbine.test
import com.moyi.app.core.ApiErrorCode
import com.moyi.app.core.DataError
import com.moyi.app.core.Result
import com.moyi.app.designsystem.presentation.UiText
import com.moyi.app.domain.bond.BondSettings
import com.moyi.app.domain.bond.BondStatus
import com.moyi.app.domain.bond.PendingSemanticSearchRequest
import com.moyi.app.feature.bond.resources.Res
import com.moyi.app.feature.bond.resources.settings_error_conflict
import com.moyi.app.feature.bond.resources.settings_error_generic
import com.moyi.app.feature.bond.resources.settings_error_rate_limited
import com.moyi.app.feature.bond.settings.BondSettingsAction
import com.moyi.app.feature.bond.settings.BondSettingsEvent
import com.moyi.app.feature.bond.settings.BondSettingsViewModel
import com.moyi.app.feature.bond.settings.SearchStep
import com.moyi.app.feature.bond.settings.SettingsError
import com.moyi.app.feature.bond.settings.SettingsField
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Search by meaning (FR-204, ADR-0054) on [BondSettingsViewModel]: every step and every way
 * between them. Off asks; the other member's ask is agreed or declined; mine is cancelled; on is
 * turned off alone, after the confirmation, with the version as `If-Match`.
 */
class SearchByMeaningViewModelTest : MainDispatcherTest() {
    private val off = settingsV1.copy(partnerName = "Tunde", semanticSearchEnabled = false)
    private val repo = FakeBondSettingsRepository(off)
    private val legal = FakeLegalRepository()
    private val viewModel by lazy { BondSettingsViewModel("b1", repo, FakeBondRepository(repo), legal, zoneIds = { emptySet() }) }

    private val generic = UiText.Resource(Res.string.settings_error_generic)

    private fun start(settings: BondSettings): BondSettingsViewModel {
        repo.server = settings
        return viewModel
    }

    // ----- the step the bond calls for -----

    @Test
    fun theStepFollowsTheBond() {
        assertEquals(SearchStep.Off, start(off).state.value.searchStep)
        repo.server = off.copy(semanticSearchEnabled = true)
        viewModel.onAction(BondSettingsAction.OnRetryClick)
        assertEquals(SearchStep.On, viewModel.state.value.searchStep)
        repo.server = off.copy(pendingSemanticSearch = PendingSemanticSearchRequest(requestedByMe = true, expiresAt = askLapses))
        viewModel.onAction(BondSettingsAction.OnRetryClick)
        assertEquals(SearchStep.Asked, viewModel.state.value.searchStep)
        repo.server = off.copy(pendingSemanticSearch = PendingSemanticSearchRequest(requestedByMe = false))
        viewModel.onAction(BondSettingsAction.OnRetryClick)
        assertEquals(SearchStep.PartnerAsked, viewModel.state.value.searchStep)
    }

    @Test
    fun theRowOpensTheScreenAndClearsAnOldError() = runTest {
        repo.searchAskAnswers += Result.Failure(DataError.Network.NoInternet)
        start(off).onAction(BondSettingsAction.OnSearchAsk)
        assertEquals(SettingsField.Search, viewModel.state.value.error?.field)
        viewModel.events.test {
            viewModel.onAction(BondSettingsAction.OnSearchClick)
            assertEquals(BondSettingsEvent.OpenSearch, awaitItem())
        }
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun openingTheScreenReadsTheBondAgainSoAnAskMadeSinceShows() = runTest {
        start(off)
        repo.server = off.copy(pendingSemanticSearch = PendingSemanticSearchRequest(requestedByMe = false))
        viewModel.events.test {
            viewModel.onAction(BondSettingsAction.OnSearchClick)
            assertEquals(BondSettingsEvent.OpenSearch, awaitItem())
        }
        assertEquals(SearchStep.PartnerAsked, viewModel.state.value.searchStep)
    }

    @Test
    fun theRowStillOpensDuringTheCoolingOff() = runTest {
        start(off.copy(status = BondStatus.PENDING_DELETION, semanticSearchEnabled = true)).events.test {
            viewModel.onAction(BondSettingsAction.OnSearchClick)
            assertEquals(BondSettingsEvent.OpenSearch, awaitItem())
        }
    }

    // ----- off: ask -----

    @Test
    fun askingWaitsForTheOtherMemberAndStaysOnTheScreen() = runTest {
        start(off).events.test {
            viewModel.onAction(BondSettingsAction.OnSearchAsk)
            expectNoEvents()
        }
        assertEquals(1, repo.searchAsks)
        assertEquals(SearchStep.Asked, viewModel.state.value.searchStep)
        assertEquals(askLapses, viewModel.state.value.settings?.pendingSemanticSearch?.expiresAt)
        assertNull(viewModel.state.value.saving)
    }

    @Test
    fun whileTheAskIsInFlightItIsThePrimaryAndOneTapIsOneAsk() {
        val gate = CompletableDeferred<Unit>()
        repo.gate = gate
        start(off).onAction(BondSettingsAction.OnSearchAsk)
        assertEquals(SettingsField.Search, viewModel.state.value.saving)
        assertFalse(viewModel.state.value.searchSecondaryInFlight)
        viewModel.onAction(BondSettingsAction.OnSearchAsk)
        gate.complete(Unit)
        assertEquals(1, repo.searchAsks)
    }

    @Test
    fun askingWhileNobodyHasJoinedTurnsItOnAtOnce() {
        start(off.copy(status = BondStatus.PENDING_MEMBER, partnerName = null)).onAction(BondSettingsAction.OnSearchAsk)
        assertEquals(SearchStep.On, viewModel.state.value.searchStep)
    }

    @Test
    fun anEndedBondIsNotAskedAtAll() {
        start(off.copy(status = BondStatus.PENDING_DELETION)).onAction(BondSettingsAction.OnSearchAsk)
        assertEquals(0, repo.searchAsks, "409 BOND_ARCHIVED is certain: nothing is sent")
        assertTrue(viewModel.state.value.settings!!.ended)
    }

    @Test
    fun anAskTheServerFindsEndedReadsTheBondAgainAndStaysHere() = runTest {
        repo.searchAskAnswers += apiError(409, ApiErrorCode.BOND_ARCHIVED)
        start(off)
        repo.server = off.copy(status = BondStatus.PENDING_DELETION)
        viewModel.events.test {
            viewModel.onAction(BondSettingsAction.OnSearchAsk)
            expectNoEvents()
        }
        assertTrue(viewModel.state.value.settings!!.ended, "the screen now says it has ended")
        assertNull(viewModel.state.value.error)
        assertNull(viewModel.state.value.saving)
    }

    @Test
    fun aRateLimitedAskSaysSoAndKeepsThePersonHere() {
        repo.searchAskAnswers += apiError(429, ApiErrorCode.RATE_LIMITED)
        start(off).onAction(BondSettingsAction.OnSearchAsk)
        assertEquals(SettingsError(SettingsField.Search, UiText.Resource(Res.string.settings_error_rate_limited)), viewModel.state.value.error)
        assertEquals(SearchStep.Off, viewModel.state.value.searchStep)
        assertNull(viewModel.state.value.saving)
    }

    @Test
    fun anAskOffLineIsTheGenericError() {
        repo.searchAskAnswers += Result.Failure(DataError.Network.NoInternet)
        start(off).onAction(BondSettingsAction.OnSearchAsk)
        assertEquals(SettingsError(SettingsField.Search, generic), viewModel.state.value.error)
    }

    @Test
    fun anAskOnABondThePersonIsNoLongerInEnds() = runTest {
        repo.searchAskAnswers += apiError(404, ApiErrorCode.NOT_FOUND)
        start(off).events.test {
            viewModel.onAction(BondSettingsAction.OnSearchAsk)
            assertEquals(BondSettingsEvent.Ended, awaitItem())
        }
    }

    // ----- the other member asked: agree or decline -----

    @Test
    fun agreeingTurnsItOn() {
        start(off.copy(pendingSemanticSearch = PendingSemanticSearchRequest(requestedByMe = false))).onAction(BondSettingsAction.OnSearchAsk)
        assertEquals(SearchStep.On, viewModel.state.value.searchStep)
        assertEquals(1, repo.searchAsks)
    }

    @Test
    fun decliningCallsTheRequestOffAndReturnsToTheRoot() = runTest {
        start(off.copy(pendingSemanticSearch = PendingSemanticSearchRequest(requestedByMe = false))).events.test {
            viewModel.onAction(BondSettingsAction.OnSearchCancel)
            assertEquals(BondSettingsEvent.Saved, awaitItem())
        }
        assertEquals(1, repo.searchCancels)
        assertEquals(SearchStep.Off, viewModel.state.value.searchStep)
        assertFalse(viewModel.state.value.searchSecondaryInFlight)
    }

    @Test
    fun whileTheDeclineIsInFlightItIsTheSecondary() {
        val gate = CompletableDeferred<Unit>()
        start(off.copy(pendingSemanticSearch = PendingSemanticSearchRequest(requestedByMe = false)))
        repo.gate = gate
        viewModel.onAction(BondSettingsAction.OnSearchCancel)
        assertEquals(SettingsField.Search, viewModel.state.value.saving)
        assertTrue(viewModel.state.value.searchSecondaryInFlight)
        gate.complete(Unit)
    }

    @Test
    fun aFailedDeclineKeepsThePersonHereWithTheReason() = runTest {
        repo.searchCancelAnswers += Result.Failure(DataError.Network.NoInternet)
        start(off.copy(pendingSemanticSearch = PendingSemanticSearchRequest(requestedByMe = false))).events.test {
            viewModel.onAction(BondSettingsAction.OnSearchCancel)
            expectNoEvents()
        }
        assertEquals(SettingsError(SettingsField.Search, generic), viewModel.state.value.error)
        assertEquals(SearchStep.PartnerAsked, viewModel.state.value.searchStep)
        assertFalse(viewModel.state.value.searchSecondaryInFlight)
    }

    // ----- I asked: cancel -----

    @Test
    fun cancellingMyAskReturnsToTheRoot() = runTest {
        start(off.copy(pendingSemanticSearch = PendingSemanticSearchRequest(requestedByMe = true, expiresAt = askLapses))).events.test {
            viewModel.onAction(BondSettingsAction.OnSearchCancel)
            assertEquals(BondSettingsEvent.Saved, awaitItem())
        }
        assertEquals(SearchStep.Off, viewModel.state.value.searchStep)
    }

    @Test
    fun aRequestAlreadyGoneIsWhatThePersonAskedFor() = runTest {
        repo.searchCancelAnswers += apiError(404, ApiErrorCode.NOT_FOUND)
        start(off.copy(pendingSemanticSearch = PendingSemanticSearchRequest(requestedByMe = true)))
        viewModel.events.test {
            viewModel.onAction(BondSettingsAction.OnSearchCancel)
            assertEquals(BondSettingsEvent.Saved, awaitItem())
        }
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun theOneWhoAskedCannotAgreeToTheirOwnAsk() {
        start(off.copy(pendingSemanticSearch = PendingSemanticSearchRequest(requestedByMe = true))).onAction(BondSettingsAction.OnSearchAsk)
        assertEquals(0, repo.searchAsks)
    }

    // ----- on: turn it off, after asking -----

    @Test
    fun turningItOffAsksFirstAndSendsNothingUntilConfirmed() {
        start(off.copy(semanticSearchEnabled = true)).onAction(BondSettingsAction.OnSearchTurnOffClick)
        assertTrue(viewModel.state.value.confirmingSearchOff)
        assertTrue(repo.searchOffs.isEmpty())
        viewModel.onAction(BondSettingsAction.OnSearchTurnOffDismiss)
        assertFalse(viewModel.state.value.confirmingSearchOff)
        assertTrue(repo.searchOffs.isEmpty(), "Cancel changes nothing")
        assertEquals(SearchStep.On, viewModel.state.value.searchStep)
    }

    @Test
    fun aConfirmedTurnOffSendsTheVersionAndShowsItOff() {
        start(off.copy(semanticSearchEnabled = true)).onAction(BondSettingsAction.OnSearchTurnOffClick)
        viewModel.onAction(BondSettingsAction.OnSearchTurnOffConfirm)
        assertEquals(listOf("\"1\""), repo.searchOffs, "If-Match is the version read")
        assertFalse(viewModel.state.value.confirmingSearchOff)
        assertEquals(SearchStep.Off, viewModel.state.value.searchStep)
        assertEquals("\"2\"", viewModel.state.value.settings?.version)
    }

    @Test
    fun whileTurningOffItIsThePrimary() {
        val gate = CompletableDeferred<Unit>()
        start(off.copy(semanticSearchEnabled = true))
        repo.gate = gate
        viewModel.onAction(BondSettingsAction.OnSearchTurnOffClick)
        viewModel.onAction(BondSettingsAction.OnSearchTurnOffConfirm)
        assertEquals(SettingsField.Search, viewModel.state.value.saving)
        assertFalse(viewModel.state.value.searchSecondaryInFlight)
        gate.complete(Unit)
    }

    @Test
    fun turningItOffWorksOnAnEndedBond() {
        start(off.copy(semanticSearchEnabled = true, status = BondStatus.PENDING_DELETION)).onAction(BondSettingsAction.OnSearchTurnOffClick)
        viewModel.onAction(BondSettingsAction.OnSearchTurnOffConfirm)
        assertEquals(1, repo.searchOffs.size)
        assertEquals(SearchStep.Off, viewModel.state.value.searchStep)
    }

    @Test
    fun aConflictReadsTheBondAgainAndSaysItChanged() {
        start(off.copy(semanticSearchEnabled = true))
        repo.searchOffAnswers += apiError(412, ApiErrorCode.PRECONDITION_FAILED)
        repo.server = off.copy(semanticSearchEnabled = true, name = "Us", version = "\"7\"")
        viewModel.onAction(BondSettingsAction.OnSearchTurnOffClick)
        viewModel.onAction(BondSettingsAction.OnSearchTurnOffConfirm)
        assertEquals(SettingsError(SettingsField.Search, UiText.Resource(Res.string.settings_error_conflict)), viewModel.state.value.error)
        assertEquals("\"7\"", viewModel.state.value.settings?.version, "the next try sends what is there now")
        assertEquals(SearchStep.On, viewModel.state.value.searchStep)
        assertNull(viewModel.state.value.saving)
    }

    @Test
    fun turnOffIsNotOfferedWhenItIsAlreadyOff() {
        start(off).onAction(BondSettingsAction.OnSearchTurnOffClick)
        assertFalse(viewModel.state.value.confirmingSearchOff)
    }

    // ----- the facts card's link -----

    @Test
    fun thePrivacyLinkOpensThePolicyInForce() = runTest {
        start(off).events.test {
            viewModel.onAction(BondSettingsAction.OnPrivacyPolicyClick)
            assertEquals(BondSettingsEvent.OpenPage(privacyUrl), awaitItem())
        }
    }

    @Test
    fun aPrivacyLinkThatCannotBeReadSaysSo() = runTest {
        legal.documentsAnswers += Result.Failure(DataError.Network.NoInternet)
        start(off).events.test {
            viewModel.onAction(BondSettingsAction.OnPrivacyPolicyClick)
            expectNoEvents()
        }
        assertEquals(SettingsError(SettingsField.Search, generic), viewModel.state.value.error)
    }
}
