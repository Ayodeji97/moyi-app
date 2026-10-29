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
    private val viewModel = InviteHandoffViewModel(INVITE.code, INVITE.link)

    @Test
    fun copyPutsTheCodeOnTheClipboardAndShowsCopied() = runTest {
        assertFalse(viewModel.state.value.copied)
        viewModel.events.test {
            viewModel.onAction(InviteHandoffAction.OnCopyClick)
            assertEquals(InviteHandoffEvent.CopyCode("K7Q2PD"), awaitItem())
        }
        assertTrue(viewModel.state.value.copied)
    }

    @Test
    fun shareOpensTheSheetWithTheLinkAndCodeAndNavigatesNowhere() = runTest {
        viewModel.events.test {
            viewModel.onAction(InviteHandoffAction.OnShareClick)
            assertEquals(InviteHandoffEvent.Share(link = INVITE.link, code = INVITE.code), awaitItem())
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
