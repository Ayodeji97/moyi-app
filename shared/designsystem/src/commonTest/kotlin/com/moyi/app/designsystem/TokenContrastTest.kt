package com.moyi.app.designsystem

import androidx.compose.ui.graphics.Color
import com.moyi.app.designsystem.theme.MoyiColors
import com.moyi.app.designsystem.theme.RosewoodDark
import com.moyi.app.designsystem.theme.RosewoodLight
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Contrast is verified with a checker, not by eye, in both themes (`14` §6,
 * NFR-102). This makes the measured table in `tokens.md` §1 executable: a
 * token edit that breaks a pairing the product uses fails the build.
 *
 * Thresholds: 4.5 for text, 3.0 for UI component boundaries (WCAG 1.4.11).
 * If a pair fails, the token is not changed here: the failure is a design
 * finding for the owner.
 */
class TokenContrastTest {

    private data class Pair(val name: String, val fg: (MoyiColors) -> Color, val bg: (MoyiColors) -> Color, val needs: Double)

    private val pairs = listOf(
        Pair("ink on surface (body and entry text)", { it.ink }, { it.surface }, TEXT),
        Pair("ink on surfaceRaised (entry card)", { it.ink }, { it.surfaceRaised }, TEXT),
        Pair("inkMuted on surface (labels, helper)", { it.inkMuted }, { it.surface }, TEXT),
        Pair("inkMuted on surfaceRaised (disabled action label)", { it.inkMuted }, { it.surfaceRaised }, TEXT),
        Pair("surface on ink (PrimaryAction label)", { it.surface }, { it.ink }, TEXT),
        Pair("surface on danger (destructive fill)", { it.surface }, { it.danger }, TEXT),
        Pair("danger on surface (error text)", { it.danger }, { it.surface }, TEXT),
        Pair("danger on surfaceRaised", { it.danger }, { it.surfaceRaised }, TEXT),
        Pair("outlineStrong on surface (Field border)", { it.outlineStrong }, { it.surface }, UI),
        Pair("outlineStrong on surfaceRaised", { it.outlineStrong }, { it.surfaceRaised }, UI),
        Pair("ink on surface (Field focus stroke)", { it.ink }, { it.surface }, UI),
    )

    @Test
    fun darkPairsMeetWcagAa() = assertAll(RosewoodDark, "dark")

    @Test
    fun lightPairsMeetWcagAa() = assertAll(RosewoodLight, "light")

    /** Guards the checker itself against the figures `tokens.md` §1 measured. */
    @Test
    fun ratiosMatchTheMeasuredTable() {
        assertEquals(16.26, round2(contrast(RosewoodDark.ink, RosewoodDark.surface)))
        assertEquals(15.18, round2(contrast(RosewoodLight.ink, RosewoodLight.surface)))
        assertEquals(9.98, round2(contrast(RosewoodDark.inkMuted, RosewoodDark.surface)))
        assertEquals(6.29, round2(contrast(RosewoodLight.inkMuted, RosewoodLight.surface)))
        assertEquals(6.54, round2(contrast(RosewoodDark.surface, RosewoodDark.danger)))
        assertEquals(5.57, round2(contrast(RosewoodLight.surface, RosewoodLight.danger)))
        assertEquals(1.0, contrast(Color.White, Color.White))
        assertEquals(21.0, round2(contrast(Color.Black, Color.White)))
    }

    private fun assertAll(colors: MoyiColors, mode: String) {
        val failures = pairs.mapNotNull { pair ->
            val ratio = contrast(pair.fg(colors), pair.bg(colors))
            println("$mode  ${pair.name}: ${round2(ratio)} (needs ${pair.needs})")
            if (ratio < pair.needs) "${pair.name}: ${round2(ratio)} < ${pair.needs}" else null
        }
        assertTrue(failures.isEmpty(), "Rosewood $mode fails WCAG AA:\n" + failures.joinToString("\n"))
    }

    private companion object {
        const val TEXT = 4.5
        const val UI = 3.0

        /** WCAG 2.x contrast ratio: (L1 + 0.05) / (L2 + 0.05), lighter over darker. */
        fun contrast(a: Color, b: Color): Double {
            val la = luminance(a)
            val lb = luminance(b)
            return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
        }

        /** WCAG relative luminance from sRGB channels. */
        fun luminance(c: Color): Double {
            fun channel(v: Float): Double {
                val s = v.toDouble()
                return if (s <= 0.04045) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
            }
            return 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)
        }

        fun round2(v: Double): Double = kotlin.math.round(v * 100) / 100
    }
}
