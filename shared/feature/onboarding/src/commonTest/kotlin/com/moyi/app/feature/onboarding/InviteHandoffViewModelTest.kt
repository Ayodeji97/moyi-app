package com.moyi.app.feature.onboarding

import app.cash.turbine.test
import com.moyi.app.feature.onboarding.invite.InviteHandoffAction
import com.moyi.app.feature.onboarding.invite.InviteHandoffEvent
import com.moyi.app.feature.onboarding.invite.InviteHandoffViewModel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InviteHandoffViewModelTest : MainDispatcherTest() {
    private val viewModel = InviteHandoffViewModel("K7Q2PD")

    @Test
    fun copyShowsCopied() {
        assertFalse(viewModel.state.value.copied)
        viewModel.onAction(InviteHandoffAction.OnCopyClick)
        assertTrue(viewModel.state.value.copied)
    }

    @Test
    fun shareNavigatesNowhere() = runTest {
        viewModel.events.test {
            viewModel.onAction(InviteHandoffAction.OnShareClick)
            expectNoEvents()
        }
    }

    @Test
    fun continueFinishesOnboarding() = runTest {
        viewModel.events.test {
            viewModel.onAction(InviteHandoffAction.OnContinueClick)
            assertEquals(InviteHandoffEvent.Finish, awaitItem())
        }
    }
}
