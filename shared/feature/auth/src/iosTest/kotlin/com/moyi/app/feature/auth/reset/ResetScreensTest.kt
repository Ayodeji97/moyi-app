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
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.moyi.app.designsystem.MoyiTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The two reset screens whose words carry a rule: Reset link sent must not
 * disclose whether the address has an account, and Choose a new password must
 * state the consequence before the action (states.md §1b).
 */
class ResetScreensTest {
    private fun ComposeUiTest.inPhone(content: @Composable () -> Unit) = setContent {
        MoyiTheme(darkTheme = true) {
            Box(Modifier.size(390.dp, 760.dp)) { content() }
        }
    }

    @Test
    fun linkSentReadsTheNonDisclosureSentenceAndNothingAboutTheAddress() = runComposeUiTest {
        var chosen = 0
        inPhone { ResetLinkSentScreen(onBack = {}, onChooseNewPassword = { chosen++ }) }

        onNodeWithText(NON_DISCLOSURE).assertIsDisplayed()
        val spoken = texts(onRoot(useUnmergedTree = true).fetchSemanticsNode())
        assertTrue(spoken.none { "@" in it }, "nothing on this screen may name an address: $spoken")
        // The heading and the one line, word for word; no other sentence that could vary.
        assertTrue("Check your email" in spoken, spoken.toString())
        assertEquals(1, spoken.count { it == NON_DISCLOSURE }, spoken.toString())

        onNodeWithText("Choose a new password").performClick()
        assertEquals(1, chosen)
    }

    @Test
    fun choosingANewPasswordStatesTheConsequenceAboveTheAction() = runComposeUiTest {
        inPhone { ResetPasswordScreen(state = ResetPasswordState(email = "amara@example.com"), onAction = {}) }

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

    private fun texts(node: SemanticsNode): List<String> {
        val own = node.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }
        return listOfNotNull(own) + node.children.flatMap { texts(it) }
    }

    private companion object {
        const val NON_DISCLOSURE = "If that address has an account, we sent it a link. Open the email, copy the link and paste it in Moyi. It works for an hour."
        const val CONSEQUENCE = "This signs you out everywhere else. You will need to sign in again on your other devices."
    }
}
