@file:OptIn(ExperimentalTestApi::class)

package com.moyi.app.feature.auth.sessionended

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.moyi.app.designsystem.MoyiTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/** The session-ended screen (TOKEN_REUSE_DETECTED): says what happened, offers one way on, no back. */
class SessionEndedScreenTest {
    private var signIns = 0

    private fun ComposeUiTest.screen() = setContent {
        MoyiTheme(darkTheme = true) {
            Box(Modifier.size(390.dp, 844.dp)) { SessionEndedScreen(onSignIn = { signIns++ }) }
        }
    }

    @Test
    fun saysWhatHappenedAndThatNothingElseChanged() = runComposeUiTest {
        screen()
        onNodeWithText("This sign-in has ended").assertIsDisplayed()
        onNodeWithText("Anything else you are signed in on is unaffected", substring = true).assertIsDisplayed()
    }

    @Test
    fun signInIsTheOneWayOnAndThereIsNoBack() = runComposeUiTest {
        screen()
        onNodeWithContentDescription("Back").assertDoesNotExist()
        onNodeWithText("Sign in").performClick()
        assertEquals(1, signIns)
    }
}
