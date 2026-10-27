package com.moyi.app.designsystem.theme

import androidx.compose.ui.unit.dp

/**
 * Every component dimension in one place, so the components can be checked
 * against Figma placement line by line. Components read these; none of them
 * carries an inline number.
 *
 * Each value names its source. "tokens.md" is the moyi-design-system
 * reference; values marked **interpreted** have no stated figure there and
 * are this PR's reading, listed in the PR for the Figma comparison.
 *
 * Spacing values are the 8 pt scale from [MoyiSpacing] (`tokens.md` §3).
 */
object MoyiDimens {

    /** NFR-101, SKILL.md §8: 48 on both platforms (iOS's 44 is the smaller). */
    val TouchTarget = 48.dp

    /** Lucide glyph size (`tokens.md` §6: 24 px, 2 px stroke). */
    val IconGlyph = 24.dp

    object PrimaryAction {
        /** `tokens.md` §8.1: minimum width 176, a floor not a fixed width. */
        val MinWidth = 176.dp
        /** `tokens.md` §8.1: height 48. Applied as a minimum so a 200 % label grows it. */
        val MinHeight = 48.dp
        /** **Interpreted**: 24 (spacing `lg`) each side of the label. Not stated in tokens.md. */
        val PaddingHorizontal = 24.dp
        /** **Interpreted**: 8 (spacing `sm`) above and below, so growth at 200 % keeps a margin. */
        val PaddingVertical = 8.dp
        /** `tokens.md` §7b: Disabled carries an `outline` hairline. */
        val DisabledBorder = 1.dp
    }

    object Field {
        /** `tokens.md` §4: 56 tall (65 for `Code`, not built). Applied as a minimum. */
        val BoxMinHeight = 56.dp
        /** **Interpreted**: Idle stroke 1, `outlineStrong`. tokens.md gives the colour, not the weight. */
        val StrokeIdle = 1.dp
        /**
         * **Interpreted**: Focus and Error stroke 2. `tokens.md` §4 requires a weight
         * change (NFR-104) and does not give the number.
         */
        val StrokeEmphasis = 2.dp
        /** **Interpreted**: Disabled stroke 1, decorative `outline`. */
        val StrokeDisabled = 1.dp
        /**
         * **Interpreted**: 8 between label and box, and between box and helper or
         * error. Consistent with `states.md` §6's 115 pt field block (label 18 +
         * 8 + box 56 + 8 + helper 24 = 114).
         */
        val LabelGap = 8.dp
        val SupportGap = 8.dp
        /** **Interpreted**: 16 (spacing `md`) from the box edge to the text. */
        val PaddingStart = 16.dp
        /** Text field without a trailing icon pads the end the same as the start. */
        val PaddingEnd = 16.dp
        /** **Interpreted**: 8 above and below the text inside the box, for growth at 200 %. */
        val PaddingVertical = 8.dp
        /** `tokens.md` §4: the Secure eye is "a second 48 dp target". */
        val RevealTarget = TouchTarget
    }

    object TextLink {
        /** NFR-101: the target is at least 48 x 48 even for short words. */
        val MinTarget = TouchTarget
    }

    /** Not in the ten components; every value here is **interpreted** (see ConsentCheckbox). */
    object ConsentCheckbox {
        val RowMinHeight = TouchTarget
        val Box = 20.dp
        val BoxStroke = 2.dp
        val CheckGlyph = 16.dp
        /** 16 (spacing `md`) between box and text. */
        val Gap = 16.dp
        /** 8 (spacing `sm`) above and below the row's text. */
        val RowPaddingVertical = 8.dp
    }

    object Scaffold {
        /** See [MoyiSpacing.gutter]: 24, and the 20-versus-scale conflict in tokens.md. */
        val Gutter = 24.dp
        /** `tokens.md` §3: 24 between blocks. Top and bottom of the scroll content. */
        val ContentPaddingVertical = 24.dp
        /** SKILL.md §9: a 48 x 48 back target holding `chevron-left`. */
        val BackTarget = TouchTarget
    }
}
