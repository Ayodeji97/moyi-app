@file:OptIn(ExperimentalTestApi::class)

package com.moyi.app.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.moyi.app.designsystem.component.MoyiScaffold
import com.moyi.app.designsystem.component.MoyiText
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A task's header on iOS (D10, sheet style): Cancel where a pushed screen has its
 * back, no back control, and a swipe down on the header closes it like Cancel.
 */
class TaskCloseTest {
    private var closes = 0

    private fun ComposeUiTest.task() = setContent {
        MoyiTheme(darkTheme = true) {
            Box(Modifier.size(390.dp, 844.dp)) {
                MoyiScaffold(onClose = { closes++ }) { MoyiText("Body") }
            }
        }
    }

    @Test
    fun cancelClosesAndThereIsNoBack() = runComposeUiTest {
        task()
        onNodeWithContentDescription("Back").assertDoesNotExist()
        onNodeWithText("Cancel").performClick()
        assertEquals(1, closes)
    }

    /** A drag down from the empty end of the header row (y 24 to 72), beside Cancel. */
    private fun ComposeUiTest.dragHeaderDown(by: Dp) = onRoot().performTouchInput {
        val start = Offset(right - 40.dp.toPx(), 48.dp.toPx())
        swipe(start, start + Offset(0f, by.toPx()))
    }

    @Test
    fun aSwipeDownOnTheHeaderClosesOnce() = runComposeUiTest {
        task()
        dragHeaderDown(120.dp)
        assertEquals(1, closes)
    }

    @Test
    fun aShortDragIsNotAClose() = runComposeUiTest {
        task()
        // The threshold is 64: a drag shorter than that closes nothing.
        dragHeaderDown(40.dp)
        assertEquals(0, closes)
    }
}
