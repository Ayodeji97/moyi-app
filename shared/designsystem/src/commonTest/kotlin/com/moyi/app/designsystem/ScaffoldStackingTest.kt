package com.moyi.app.designsystem

import com.moyi.app.designsystem.component.shouldStackActions
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScaffoldStackingTest {
    @Test
    fun aSmallActionBlockOnATallScreenStaysPinned() {
        assertFalse(shouldStackActions(available = 700, action = 120, gap = 16, minContent = 120))
    }

    @Test
    fun actionsTakingOverHalfTheHeightScrollWithTheContent() {
        assertTrue(shouldStackActions(available = 400, action = 201, gap = 16, minContent = 120))
    }

    @Test
    fun contentLeftWithLessThanTheMinimumScrollsWithTheActions() {
        // Neither over half: 300 - 16 - 150 leaves 134, enough; 270 - 16 - 135 leaves 119, not.
        assertFalse(shouldStackActions(available = 300, action = 150, gap = 16, minContent = 120))
        assertTrue(shouldStackActions(available = 270, action = 135, gap = 16, minContent = 120))
    }
}
