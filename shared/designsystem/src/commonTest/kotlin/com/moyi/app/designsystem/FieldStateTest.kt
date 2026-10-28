package com.moyi.app.designsystem

import com.moyi.app.designsystem.component.FieldState
import com.moyi.app.designsystem.component.fieldStateOf
import com.moyi.app.designsystem.component.fieldStroke
import com.moyi.app.designsystem.theme.RosewoodDark
import com.moyi.app.designsystem.theme.RosewoodLight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FieldStateTest {

    @Test
    fun disabledWinsOverErrorAndFocus() {
        assertEquals(FieldState.Disabled, fieldStateOf(enabled = false, hasError = true, focused = true))
    }

    @Test
    fun errorWinsOverFocus() {
        assertEquals(FieldState.Error, fieldStateOf(enabled = true, hasError = true, focused = true))
    }

    @Test
    fun focusAndIdle() {
        assertEquals(FieldState.Focus, fieldStateOf(enabled = true, hasError = false, focused = true))
        assertEquals(FieldState.Idle, fieldStateOf(enabled = true, hasError = false, focused = false))
    }

    /** NFR-104: Focus and Error are told apart from Idle by stroke weight, not only by colour. */
    @Test
    fun focusAndErrorChangeStrokeWeight() {
        listOf(RosewoodDark, RosewoodLight).forEach { colors ->
            val idle = colors.fieldStroke(FieldState.Idle).width
            assertTrue(colors.fieldStroke(FieldState.Focus).width > idle)
            assertTrue(colors.fieldStroke(FieldState.Error).width > idle)
        }
    }
}
