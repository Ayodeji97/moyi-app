package com.moyi.app.feature.onboarding

import app.cash.turbine.test
import com.moyi.app.core.DataError
import com.moyi.app.core.Result
import com.moyi.app.designsystem.presentation.UiText
import com.moyi.app.feature.onboarding.gate.GateResendStatus
import com.moyi.app.feature.onboarding.gate.UnverifiedGateAction
import com.moyi.app.feature.onboarding.gate.UnverifiedGateEvent
import com.moyi.app.feature.onboarding.gate.UnverifiedGateViewModel
import com.moyi.app.feature.onboarding.resources.Res
import com.moyi.app.feature.onboarding.resources.bond_create_error
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class UnverifiedGateViewModelTest : MainDispatcherTest() {
    private val auth = FakeAuthRepository()
    private val account = FakeAccountRepository().apply { result = Result.Success(user(verified = false)) }

    @Test
    fun itNamesTheAddressTheLinkWentTo() {
        val viewModel = UnverifiedGateViewModel(auth, account)
        assertEquals("tunde@example.com", viewModel.state.value.email)
    }

    @Test
    fun comingBackVerifiedMovesOnToOnboarding() = runTest {
        val viewModel = UnverifiedGateViewModel(auth, account)
        viewModel.events.test {
            viewModel.onAction(UnverifiedGateAction.OnResume)
            expectNoEvents()
            account.result = Result.Success(user(verified = true))
            viewModel.onAction(UnverifiedGateAction.OnResume)
            assertEquals(UnverifiedGateEvent.NavigateToOnboarding, awaitItem())
        }
    }

    @Test
    fun aFailedCheckChangesNothing() = runTest {
        account.result = Result.Failure(DataError.Network.NoInternet)
        val viewModel = UnverifiedGateViewModel(auth, account)
        viewModel.events.test {
            viewModel.onAction(UnverifiedGateAction.OnResume)
            expectNoEvents()
        }
    }

    @Test
    fun resendSendsToTheSessionsAddressAndAcknowledges() {
        val viewModel = UnverifiedGateViewModel(auth, account, resendCooldown = 60.seconds)
        viewModel.onAction(UnverifiedGateAction.OnResendClick)
        assertEquals(listOf("tunde@example.com"), auth.resendCalls)
        assertEquals(GateResendStatus.Sent, viewModel.state.value.resend)
        viewModel.onAction(UnverifiedGateAction.OnResendClick)
        assertEquals(1, auth.resendCalls.size, "the acknowledgement is not a second request")
    }

    @Test
    fun aFailedResendSaysWhyAndCanBeTriedAgain() {
        auth.resendResult = Result.Failure(DataError.Network.NoInternet)
        val viewModel = UnverifiedGateViewModel(auth, account)
        viewModel.onAction(UnverifiedGateAction.OnResendClick)
        assertEquals(UiText.Resource(Res.string.bond_create_error), viewModel.state.value.resendFailure)
        assertEquals(GateResendStatus.Ready, viewModel.state.value.resend)
    }
}
