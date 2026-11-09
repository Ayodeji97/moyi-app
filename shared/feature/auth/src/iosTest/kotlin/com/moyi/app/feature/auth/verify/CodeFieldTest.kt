@file:OptIn(ExperimentalTestApi::class)

package com.moyi.app.feature.auth.verify

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.moyi.app.designsystem.MoyiTheme
import com.moyi.app.designsystem.presentation.UiText
import com.moyi.app.feature.auth.common.FieldInput
import com.moyi.app.feature.auth.resources.Res
import com.moyi.app.feature.auth.resources.code_error_invalid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The code field is ONE control (tokens.md §4: not six boxes), named "6-digit code", and a
 * refusal that arrives with focus still in it is announced.
 */
class CodeFieldTest {
    private fun ComposeUiTest.inPhone(content: @Composable () -> Unit) = setContent {
        MoyiTheme(darkTheme = true) {
            Box(Modifier.size(390.dp, 760.dp)) { content() }
        }
    }

    @Test
    fun theCodeIsOneEditableControlNamedSixDigitCode() = runComposeUiTest {
        val sent = mutableListOf<VerifyAction>()
        inPhone { VerifyScreen(state = VerifyState(email = "amara@example.com"), onAction = { sent += it }) }

        val fields = onAllNodes(hasSetTextAction()).fetchSemanticsNodes()
        assertEquals(1, fields.size, "one text field on verify")
        val spoken = fields.single().config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }.orEmpty()
        assertTrue("6-digit code" in spoken, spoken)

        onNode(hasSetTextAction()).performTextInput("482913")
        assertEquals(VerifyAction.OnCodeChange("482913"), sent.filterIsInstance<VerifyAction.OnCodeChange>().last())
    }

    @Test
    fun aRefusalIsTheFieldsErrorAndALiveRegionOnlyWhileItShows() = runComposeUiTest {
        var state by mutableStateOf(VerifyState(email = "amara@example.com", code = FieldInput("482913")))
        inPhone { VerifyScreen(state = state, onAction = {}) }

        val calm = onNode(hasSetTextAction()).fetchSemanticsNode().config
        assertNull(calm.getOrNull(SemanticsProperties.LiveRegion))
        assertNull(calm.getOrNull(SemanticsProperties.Error))

        state = state.copy(code = FieldInput("482913", UiText.Resource(Res.string.code_error_invalid), showErrors = true))
        waitForIdle()
        val refused = onNode(hasSetTextAction()).fetchSemanticsNode().config
        assertEquals(LiveRegionMode.Polite, refused.getOrNull(SemanticsProperties.LiveRegion))
        assertTrue(refused.getOrNull(SemanticsProperties.Error).orEmpty().startsWith("That code did not work."))
    }
}
