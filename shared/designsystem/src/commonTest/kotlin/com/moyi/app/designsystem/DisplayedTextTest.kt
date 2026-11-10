package com.moyi.app.designsystem

import com.moyi.app.designsystem.component.TextRole
import com.moyi.app.designsystem.component.displayedText
import kotlin.test.Test
import kotlin.test.assertEquals

/** The label diet (docs/missing-components.md §69): only the Label role is drawn in capitals. */
class DisplayedTextTest {
    @Test
    fun anAuthorLineIsStillUppercased() {
        assertEquals("FROM PAX", displayedText(TextRole.Label, "From Pax"))
    }

    @Test
    fun aChipOrLegendTagIsDrawnAsTyped() {
        assertEquals("Thank you", displayedText(TextRole.Tag, "Thank you"))
        assertEquals("Both wrote", displayedText(TextRole.Tag, "Both wrote"))
    }
}
