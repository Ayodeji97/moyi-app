@file:OptIn(ExperimentalTestApi::class)

package com.moyi.app.feature.auth.reset

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.moyi.app.designsystem.MoyiTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The reset screen's words that carry a rule: the code step must not disclose whether the
 * address has an account, and the password step must state the consequence before the
 * action (states.md §1b).
 */
class ResetScreensTest {
    private fun ComposeUiTest.inPhone(content: @Composable () -> Unit) = setContent {
        MoyiTheme(darkTheme = true) {
            Box(Modifier.size(390.dp, 760.dp)) { content() }
        }
    }

    @Test
    fun theCodeStepSaysWhereTheCodeWentWithoutSayingTheAccountExists() = runComposeUiTest {
        inPhone { ResetPasswordScreen(state = ResetPasswordState(email = "amara@example.com"), onAction = {}) }

        // Forgot password lands here (no "Check your email" screen since 2026-11-16), so this
        // one line carries the non-disclosure rule: an "if", the same words for any address.
        onNodeWithText(SENT_TO_AMARA).assertIsDisplayed()
        val spoken = texts(onRoot(useUnmergedTree = true).fetchSemanticsNode())
        assertEquals(1, spoken.count { "@" in it }, "only that line names the address: $spoken")
    }

    @Test
    fun theCodeStepWithNoAddressNamesNone() = runComposeUiTest {
        // A refused emailed link, on a phone that never asked: the code step with no address.
        inPhone { ResetPasswordScreen(state = ResetPasswordState(email = ""), onAction = {}) }

        onNodeWithText("Type your code").assertIsDisplayed()
        val spoken = texts(onRoot(useUnmergedTree = true).fetchSemanticsNode())
        assertTrue(spoken.none { it.startsWith("If ") }, spoken.toString())
    }

    @Test
    fun choosingANewPasswordStatesTheConsequenceAboveTheAction() = runComposeUiTest {
        inPhone {
            ResetPasswordScreen(
                state = ResetPasswordState(email = "amara@example.com", step = ResetStep.Password),
                onAction = {},
            )
        }

        val consequence = onNodeWithText(CONSEQUENCE).assertIsDisplayed().fetchSemanticsNode()
        val action = onNodeWithText("Save and sign in").assertIsDisplayed().fetchSemanticsNode()
        assertTrue(
            consequence.boundsInRoot.bottom <= action.boundsInRoot.top,
            "the consequence must sit above the action: ${consequence.boundsInRoot} vs ${action.boundsInRoot}",
        )
        // And before it in the order a screen reader walks.
        val spoken = texts(onRoot(useUnmergedTree = true).fetchSemanticsNode())
        assertTrue(spoken.indexOf(CONSEQUENCE) in 0 until spoken.indexOf("Save and sign in"), spoken.toString())
    }

    @Test
    fun theCodeStepAsksForOnlyTheCodeAndTheNewPasswordWaitsForTheNextStep() = runComposeUiTest {
        inPhone { ResetPasswordScreen(state = ResetPasswordState(email = "amara@example.com"), onAction = {}) }

        onNodeWithText("Type your code").assertIsDisplayed()
        onNodeWithText("Check the code").assertIsDisplayed()
        val spoken = texts(onRoot(useUnmergedTree = true).fetchSemanticsNode())
        assertTrue(CONSEQUENCE !in spoken && "Save and sign in" !in spoken && "Password" !in spoken, spoken.toString())
    }

    @Test
    fun signedInTheCodeStepNamesTheAddressPlainly() = runComposeUiTest {
        inPhone { ResetPasswordScreen(state = ResetPasswordState(email = "amara@example.com", signedIn = true), onAction = {}) }

        onNodeWithText("We sent a 6-digit code to amara@example.com. Type it below, or open the link in the same email.")
            .assertIsDisplayed()
    }

    @Test
    fun signedInThePasswordStepSaysThisPhoneStaysBeforeTheAction() = runComposeUiTest {
        inPhone {
            ResetPasswordScreen(
                state = ResetPasswordState(email = "amara@example.com", step = ResetStep.Password, signedIn = true),
                onAction = {},
            )
        }

        val consequence = onNodeWithText(STAYS_SIGNED_IN).assertIsDisplayed().fetchSemanticsNode()
        val action = onNodeWithText("Save the new password").assertIsDisplayed().fetchSemanticsNode()
        assertTrue(consequence.boundsInRoot.bottom <= action.boundsInRoot.top)
        val spoken = texts(onRoot(useUnmergedTree = true).fetchSemanticsNode())
        assertTrue("Save and sign in" !in spoken && CONSEQUENCE !in spoken, spoken.toString())
    }

    @Test
    fun changePasswordSaysWhereTheCodeGoesAndTheConsequenceBeforeTheAction() = runComposeUiTest {
        inPhone { ChangePasswordScreen(state = ChangePasswordState(email = "amara@example.com"), onAction = {}) }

        onNodeWithText("We will email a 6-digit code to amara@example.com. Type it on the next screen, then choose your new password.")
            .assertIsDisplayed()
        val consequence = onNodeWithText(STAYS_SIGNED_IN).assertIsDisplayed().fetchSemanticsNode()
        val action = onNodeWithText("Send the code").assertIsDisplayed().fetchSemanticsNode()
        assertTrue(consequence.boundsInRoot.bottom <= action.boundsInRoot.top)
        val spoken = texts(onRoot(useUnmergedTree = true).fetchSemanticsNode())
        assertTrue(spoken.indexOf(STAYS_SIGNED_IN) in 0 until spoken.indexOf("Send the code"), spoken.toString())
    }

    private fun texts(node: SemanticsNode): List<String> {
        val own = node.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }
        return listOfNotNull(own) + node.children.flatMap { texts(it) }
    }

    private companion object {
        const val SENT_TO_AMARA = "If amara@example.com has an account, we sent it a 6-digit code."
        const val STAYS_SIGNED_IN = "Your other devices will be signed out. This phone stays signed in."
        const val CONSEQUENCE = "This signs you out everywhere else. You will need to sign in again on your other devices."
    }
}
