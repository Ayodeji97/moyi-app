@file:OptIn(ExperimentalTestApi::class)

package com.moyi.app.feature.bond.account

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.moyi.app.designsystem.MoyiTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/** The note card on Account (docs/missing-components.md §87): the line, and OK that asks for it to go. */
class AccountNoteCardTest {
    private val actions = mutableListOf<AccountAction>()

    private fun ComposeUiTest.account(note: AccountNote?) = setContent {
        MoyiTheme(darkTheme = false) {
            Box(Modifier.size(390.dp, 844.dp)) {
                AccountScreen(
                    AccountState(load = AccountLoad.Ready, email = "ada@example.com", note = note),
                    onAction = { actions += it },
                    onBack = {},
                )
            }
        }
    }

    @Test
    fun theCardSaysWhatHappenedAndOkAsksForItToGo() = runComposeUiTest {
        account(AccountNote.OtherAccountPasswordSaved)

        onNodeWithText("That link was for another account. Its new password is saved. You are still signed in here.")
            .assertIsDisplayed()
        onNodeWithText("OK").performClick()

        assertEquals(listOf<AccountAction>(AccountAction.OnNoteDismiss), actions)
    }

    @Test
    fun withNoNoteThereIsNoCard() = runComposeUiTest {
        account(note = null)

        onNodeWithText("OK").assertDoesNotExist()
        onNodeWithText("Signed in as ada@example.com").assertIsDisplayed()
    }
}
