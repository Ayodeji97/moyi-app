@file:OptIn(ExperimentalTestApi::class)

package com.moyi.app.designsystem

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.moyi.app.designsystem.component.SecondaryAction
import com.moyi.app.designsystem.component.SecondaryActionPair
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Today's waiting note (owner request 2026-11-09): Edit and Delete side by side when both
 * labels fit in half the width, stacked full width when they do not.
 */
class SecondaryActionPairTest {
    private fun ComposeUiTest.pair(width: Dp, edit: String, delete: String) = setContent {
        MoyiTheme(darkTheme = true) {
            SecondaryActionPair(
                first = { SecondaryAction(edit, onClick = {}, contentDescription = "Edit what you wrote") },
                second = { SecondaryAction(delete, onClick = {}, destructive = true, contentDescription = "Delete what you wrote") },
                modifier = Modifier.width(width),
            )
        }
    }

    @Test
    fun shortLabelsSitSideBySideOnANarrowPhone() = runComposeUiTest {
        // 320 wide, less the 16 gutters.
        pair(288.dp, "Edit", "Delete")
        val edit = onNodeWithContentDescription("Edit what you wrote").fetchSemanticsNode().boundsInRoot
        val delete = onNodeWithContentDescription("Delete what you wrote").fetchSemanticsNode().boundsInRoot
        assertEquals(edit.top, delete.top, "one row")
        assertTrue(delete.left > edit.right, "Delete after Edit")
        assertEquals(edit.width, delete.width, "equal width")
        assertTrue(with(density) { edit.height.toDp() } >= 48.dp, "48 tall targets")
    }

    @Test
    fun longLabelsStackRatherThanWrap() = runComposeUiTest {
        pair(288.dp, "Edit what you wrote", "Delete what you wrote")
        val edit = onNodeWithContentDescription("Edit what you wrote").fetchSemanticsNode().boundsInRoot
        val delete = onNodeWithContentDescription("Delete what you wrote").fetchSemanticsNode().boundsInRoot
        assertTrue(delete.top >= edit.bottom, "stacked")
        assertEquals(edit.width, delete.width, "both full width")
    }

    @Test
    fun theFullNameIsSpokenOnce() = runComposeUiTest {
        pair(288.dp, "Edit", "Delete")
        val node = onNodeWithContentDescription("Delete what you wrote").fetchSemanticsNode()
        assertEquals(listOf("Delete what you wrote"), node.config.getOrNull(SemanticsProperties.ContentDescription))
        assertEquals(null, node.config.getOrNull(SemanticsProperties.Text), "the short label is not read a second time")
    }
}
