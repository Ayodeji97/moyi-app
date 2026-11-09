package com.moyi.app.feature.auth.legal

import app.cash.turbine.test
import com.moyi.app.core.ApiErrorCode
import com.moyi.app.core.DataError
import com.moyi.app.core.Result
import com.moyi.app.designsystem.presentation.UiText
import com.moyi.app.domain.legal.AcceptedVersions
import com.moyi.app.domain.legal.LegalStatus
import com.moyi.app.feature.auth.FakeLegalRepository
import com.moyi.app.feature.auth.MainDispatcherTest
import com.moyi.app.feature.auth.apiError
import com.moyi.app.feature.auth.common.LegalDocument
import com.moyi.app.feature.auth.common.SubmitStatus
import com.moyi.app.feature.auth.resources.Res
import com.moyi.app.feature.auth.resources.auth_error_generic
import com.moyi.app.feature.auth.resources.auth_error_rate_limited
import com.moyi.app.feature.auth.resources.legal_error_offline
import com.moyi.app.feature.auth.testLegalDocuments
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LegalViewModelTest : MainDispatcherTest() {
    private val repo = FakeLegalRepository()

    private fun upToDate() {
        repo.statusResult = Result.Success(LegalStatus(testLegalDocuments, AcceptedVersions("local-2", "local-2")))
    }

    @Test
    fun anOlderConsentOffersAccept() {
        val viewModel = LegalViewModel(prompt = true, legalRepository = repo)
        val state = viewModel.state.value
        assertEquals(LegalLoad.Ready, state.load)
        assertEquals(testLegalDocuments, state.documents)
        assertFalse(state.upToDate)
    }

    @Test
    fun aCurrentConsentOffersNoAccept() {
        upToDate()
        val viewModel = LegalViewModel(prompt = false, legalRepository = repo)
        assertTrue(viewModel.state.value.upToDate)
    }

    @Test
    fun aFailedLoadShowsTheErrorAndTryAgainReadsAgain() {
        repo.statusResult = Result.Failure(DataError.Network.NoInternet)
        val viewModel = LegalViewModel(prompt = false, legalRepository = repo)
        assertEquals(LegalLoad.Failed, viewModel.state.value.load)

        upToDate()
        viewModel.onAction(LegalAction.OnRetryClick)
        assertEquals(LegalLoad.Ready, viewModel.state.value.load)
        assertEquals(2, repo.statusCalls)
    }

    @Test
    fun eachDocumentOpensTheUrlTheServerNames() = runTest {
        val viewModel = LegalViewModel(prompt = false, legalRepository = repo)
        viewModel.events.test {
            viewModel.onAction(LegalAction.OnDocumentClick(LegalDocument.Terms))
            assertEquals(LegalEvent.OpenPage("https://example.test/terms"), awaitItem())
            viewModel.onAction(LegalAction.OnDocumentClick(LegalDocument.Privacy))
            assertEquals(LegalEvent.OpenPage("https://example.test/privacy"), awaitItem())
            viewModel.onAction(LegalAction.OnDocumentClick(LegalDocument.DataProcessing))
            assertEquals(LegalEvent.OpenPage("https://example.test/data"), awaitItem())
        }
    }

    @Test
    fun acceptFromThePromptRecordsConsentAndCloses() = runTest {
        val gate = CompletableDeferred<Unit>()
        repo.acceptGate = gate
        val viewModel = LegalViewModel(prompt = true, legalRepository = repo)
        viewModel.events.test {
            viewModel.onAction(LegalAction.OnAcceptClick)
            assertEquals(SubmitStatus.Sending, viewModel.state.value.accept)
            viewModel.onAction(LegalAction.OnAcceptClick)
            gate.complete(Unit)
            assertEquals(LegalEvent.Close, awaitItem())
        }
        assertEquals(1, repo.acceptCalls, "a second tap while Sending sends nothing")
        assertTrue(viewModel.state.value.upToDate)
    }

    @Test
    fun acceptFromSettingsStaysAndShowsTheConsentCurrent() = runTest {
        val viewModel = LegalViewModel(prompt = false, legalRepository = repo)
        viewModel.events.test {
            viewModel.onAction(LegalAction.OnAcceptClick)
            expectNoEvents()
        }
        assertTrue(viewModel.state.value.upToDate)
        assertEquals(SubmitStatus.Idle, viewModel.state.value.accept)
    }

    @Test
    fun anOfflineAcceptSaysToCheckTheConnectionAndOffersTryAgain() {
        repo.acceptResult = Result.Failure(DataError.Network.NoInternet)
        val viewModel = LegalViewModel(prompt = true, legalRepository = repo)
        viewModel.onAction(LegalAction.OnAcceptClick)
        assertEquals(
            SubmitStatus.Failed(UiText.Resource(Res.string.legal_error_offline), offerRetry = true),
            viewModel.state.value.accept,
        )
        assertFalse(viewModel.state.value.upToDate)
    }

    @Test
    fun errorsMapToTheirLines() {
        assertEquals(
            SubmitStatus.Failed(UiText.Resource(Res.string.legal_error_offline), offerRetry = true),
            LegalViewModel.acceptFailure(DataError.Network.Timeout),
        )
        assertEquals(
            SubmitStatus.Failed(UiText.Resource(Res.string.auth_error_rate_limited), offerRetry = false),
            LegalViewModel.acceptFailure(apiError(429, ApiErrorCode.RATE_LIMITED)),
        )
        assertEquals(
            SubmitStatus.Failed(UiText.Resource(Res.string.auth_error_generic), offerRetry = true),
            LegalViewModel.acceptFailure(apiError(403, ApiErrorCode.FORBIDDEN)),
        )
        assertEquals(
            SubmitStatus.Failed(UiText.Resource(Res.string.auth_error_generic), offerRetry = true),
            LegalViewModel.acceptFailure(DataError.Network.Server(503)),
        )
    }

    @Test
    fun closeRecordsNothing() = runTest {
        val viewModel = LegalViewModel(prompt = true, legalRepository = repo)
        viewModel.events.test {
            viewModel.onAction(LegalAction.OnCloseClick)
            assertEquals(LegalEvent.Close, awaitItem())
        }
        assertEquals(0, repo.acceptCalls)
    }
}
