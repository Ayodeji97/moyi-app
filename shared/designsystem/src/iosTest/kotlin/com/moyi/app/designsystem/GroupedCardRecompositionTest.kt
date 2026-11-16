@file:OptIn(ExperimentalTestApi::class)

package com.moyi.app.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import com.moyi.app.designsystem.component.GroupedCard
import kotlin.test.Test

/**
 * The iOS smoke test of 2026-11-16: Bond settings saved a new reveal time, the ViewModel
 * held it, the picker opened at it, and the row kept showing the old one until the screen
 * was opened again. A [GroupedCard] whose caller recomposes with new values must draw them
 * at once, for a `row` as for an `item`, also when it sits inside another slot (as Bond
 * settings' cards sit inside a group heading).
 */
class GroupedCardRecompositionTest {

    @Composable
    private fun Rows(revealTime: String, signingOut: Boolean) {
        GroupedCard {
            row("Reveal time", value = revealTime, onClick = {})
            row(if (signingOut) "Signing out" else "Sign out", enabled = !signingOut, onClick = {})
        }
    }

    /** A slot around the card, as `SettingsGroup` is. */
    @Composable
    private fun Group(content: @Composable () -> Unit) = Column { content() }

    @Test
    fun aRowShowsTheNewValueAsSoonAsItArrives() = runComposeUiTest {
        var revealTime by mutableStateOf("20:00")
        setContent { MoyiTheme { Rows(revealTime, signingOut = false) } }
        onNodeWithText("20:00").assertExists()

        revealTime = "21:00"

        onNodeWithText("21:00").assertExists()
        onNodeWithText("20:00").assertDoesNotExist()
    }

    @Test
    fun aRowInsideAnotherSlotShowsTheNewValueToo() = runComposeUiTest {
        var revealTime by mutableStateOf("20:00")
        setContent { MoyiTheme { Group { Rows(revealTime, signingOut = false) } } }

        revealTime = "Saving"
        onNodeWithText("Saving").assertExists()
        revealTime = "21:00"

        onNodeWithText("21:00").assertExists()
        onNodeWithText("Saving").assertDoesNotExist()
    }

    @Test
    fun aRowsLabelAndEnabledFollowTooAsSignOutsDo() = runComposeUiTest {
        var signingOut by mutableStateOf(false)
        setContent { MoyiTheme { Rows("21:00", signingOut) } }

        signingOut = true

        onNodeWithText("Signing out").assertExists().assertIsNotEnabled()
    }

    // The card's slot is documented as able to read state directly: then only the slot
    // recomposes, and the card must still draw what it now says.
    @Test
    fun aValueReadInsideTheCardsSlotShowsAtOnce() = runComposeUiTest {
        var revealTime by mutableStateOf("20:00")
        setContent {
            MoyiTheme {
                GroupedCard { row("Reveal time", value = revealTime, onClick = {}) }
            }
        }

        revealTime = "21:00"

        onNodeWithText("21:00").assertExists()
        onNodeWithText("20:00").assertDoesNotExist()
    }
}
