package com.moyi.app.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.moyi.app.designsystem.component.FieldState
import com.moyi.app.designsystem.component.PrimaryActionState
import com.moyi.app.designsystem.component.fieldFill
import com.moyi.app.designsystem.component.fieldStroke
import com.moyi.app.designsystem.component.fieldText
import com.moyi.app.designsystem.component.primaryActionLook
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
 * Component pairs are read from the components' own look functions, so a
 * colour change in a component is caught here, not only a token change.
 *
 * Thresholds: 4.5 for text, 3.0 for UI component boundaries (WCAG 1.4.11).
 * If a pair fails, the token is not changed here: the failure is a design
 * finding for the owner.
 */
class TokenContrastTest {

    private data class Pair(val name: String, val fg: (MoyiColors) -> Color, val bg: (MoyiColors) -> Color, val needs: Double)

    /** Token pairings with no component function behind them yet: body text, error text, the destructive fill. */
    private val tokenPairs = listOf(
        Pair("ink on surface (body and entry text)", { it.ink }, { it.surface }, TEXT),
        Pair("ink on surfaceRaised (entry card)", { it.ink }, { it.surfaceRaised }, TEXT),
        Pair("inkMuted on surface (labels, helper)", { it.inkMuted }, { it.surface }, TEXT),
        Pair("inkMuted on surfaceRaised", { it.inkMuted }, { it.surfaceRaised }, TEXT),
        Pair("surface on danger (destructive fill)", { it.surface }, { it.danger }, TEXT),
        Pair("danger on surface (error text)", { it.danger }, { it.surface }, TEXT),
        Pair("danger on surfaceRaised", { it.danger }, { it.surfaceRaised }, TEXT),
    )

    /**
     * Read from [primaryActionLook], so a change to a state's colours is
     * tested as built. Sending is pinned on its own below; QueuedOffline's
     * stroke is its edge against the page. Disabled's `outline` hairline is
     * exempt (WCAG 1.4.11, inactive) and is not checked.
     */
    private val primaryActionPairs = listOf(
        PrimaryActionState.Idle,
        PrimaryActionState.QueuedOffline("x"),
        PrimaryActionState.Disabled,
    ).flatMap { state ->
        val name = state::class.simpleName
        listOfNotNull(
            Pair("PrimaryAction $name label on fill", { it.primaryActionComposite(state).label }, { it.primaryActionComposite(state).fill }, TEXT),
            if (state is PrimaryActionState.QueuedOffline) {
                Pair("PrimaryAction $name stroke on surface", { it.primaryActionLook(state).stroke }, { it.surface }, UI)
            } else {
                null
            },
        )
    }

    /**
     * Read from [fieldStroke], [fieldFill] and [fieldText]. A stroke is a
     * boundary on both sides: against the box's own fill and against the
     * page. Disabled's stroke is exempt (WCAG 1.4.11) and not checked; its
     * value text is.
     */
    private val fieldPairs = FieldState.entries.flatMap { state ->
        val strokes = if (state == FieldState.Disabled) {
            emptyList()
        } else {
            listOf(
                Pair("Field $state stroke on its fill", { it.fieldStroke(state).color }, { it.fieldFill(state) }, UI),
                Pair("Field $state stroke on surface", { it.fieldStroke(state).color }, { it.surface }, UI),
            )
        }
        strokes + Pair("Field $state value on its fill", { it.fieldText(state) }, { it.fieldFill(state) }, TEXT)
    }

    private val pairs = tokenPairs + primaryActionPairs + fieldPairs

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

    /**
     * **Known finding in light, a pass in dark.** Figma draws Sending as the
     * whole button at 60 % opacity (`docs/figma/auth-placement-spec.md` §4.1),
     * and `Modifier.alpha` does the same: the button is drawn opaque into one
     * layer, then that layer is blended over `surface`. So the label
     * (`surface`) and the fill (`ink`) are each composited at 60 % over the
     * page, and the label comes back as `surface` itself.
     *
     * Dark measures **6.41:1** and passes. A device pixel check read #171313
     * on #9E9793, which is that figure. Light measures **4.19:1**, below 4.5:
     * WCAG 1.4.3 exempts inactive controls, but the label is the only
     * feedback that something is happening (`states.md` §1c), so it is raised
     * with the owner (`docs/missing-components.md` §3) and not changed here.
     * Both numbers are pinned, so a fix or a regression fails this test and
     * gets looked at.
     */
    @Test
    fun sendingLabelContrastIsPinned() {
        val sending = PrimaryActionState.Sending("x")
        val dark = RosewoodDark.primaryActionComposite(sending).let { round2(contrast(it.label, it.fill)) }
        val light = RosewoodLight.primaryActionComposite(sending).let { round2(contrast(it.label, it.fill)) }
        println("PrimaryAction Sending label at 60 %: dark $dark, light $light (needs $TEXT)")
        assertEquals(6.41, dark)
        assertTrue(dark >= TEXT, "Sending passes in dark")
        assertEquals(4.19, light)
        assertTrue(light < TEXT, "Light Sending is a known owner finding; if it now passes, update docs/missing-components.md §3")
    }

    @Test
    fun sendingLabelCompositesToSurface() {
        for (colors in listOf(RosewoodDark, RosewoodLight)) {
            assertEquals(colors.surface, colors.primaryActionComposite(PrimaryActionState.Sending("x")).label)
        }
    }

    private data class Composite(val fill: Color, val label: Color)

    /**
     * The button as it reaches the eye. The look's alpha applies to the whole
     * button as one flattened layer, so each colour is blended at that alpha
     * over the page (`surface`), never the label over the blended fill.
     */
    private fun MoyiColors.primaryActionComposite(state: PrimaryActionState): Composite {
        val look = primaryActionLook(state)
        return Composite(
            fill = look.fill.copy(alpha = look.alpha).compositeOver(surface),
            label = look.label.copy(alpha = look.alpha).compositeOver(surface),
        )
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
