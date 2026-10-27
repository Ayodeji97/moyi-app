package com.moyi.app.designsystem.theme

import androidx.compose.ui.unit.dp

/**
 * Every component dimension in one place, so the components can be checked
 * against Figma placement line by line. Components read these; none of them
 * carries an inline number.
 *
 * Sources, cited on each value:
 * - "Figma §n": `docs/figma/auth-placement-spec.md`, read from the Figma file
 *   on 2026-10-27. **Figma wins for placement and geometry.**
 * - "tokens.md Ln": the moyi-design-system `references/tokens.md`, by line.
 *   **tokens.md wins for colour** where the two disagree.
 * - **Interpreted**: neither states a figure; this PR's reading.
 */
object MoyiDimens {

    /** NFR-101, SKILL.md §8: 48 on both platforms. Figma §4.2 "Icon touch target is 48". */
    val TouchTarget = 48.dp

    /** Lucide glyph size. tokens.md L385; Figma §2 (chevron 24×24). */
    val IconGlyph = 24.dp

    object PrimaryAction {
        /** Figma §4.1 "min-width 176"; tokens.md L513, L528 (HUG with minWidth 176). */
        val MinWidth = 176.dp
        /**
         * Figma §4.1 "Height 48 (fixed)"; tokens.md L514. Applied as a minimum so a
         * label at 200 % font grows the control instead of clipping (NFR-102).
         */
        val MinHeight = 48.dp
        /** Figma §4.1 "Padding left and right 32". */
        val PaddingHorizontal = 32.dp
        /** Figma §4.1 "No vertical padding: the label is centred in the 48". */
        val PaddingVertical = 0.dp
        /** Figma §4.1: Queued and Disabled carry a 1 px stroke; tokens.md L499 (Disabled hairline). */
        val Stroke = 1.dp
        /** Figma §4.1: Sending is the whole button at 60 % opacity. No spinner. */
        const val SendingAlpha = 0.6f
    }

    object Field {
        /** Figma §4.2 Text/Secure box 56; tokens.md L284 ("56 tall, 65 for Code"). A minimum. */
        val BoxMinHeight = 56.dp
        /** Figma §4.2: Idle and Disabled stroke 1. */
        val StrokeIdle = 1.dp
        /** Figma §4.2: Focus and Error stroke 2; tokens.md L284 (weight changes, NFR-104). */
        val StrokeEmphasis = 2.dp
        /** Figma §4.2: Disabled stroke 1. (Its colour is D1; see `fieldStroke`.) */
        val StrokeDisabled = 1.dp
        /** Figma §4.2 "label above the box with an 8 gap". */
        val LabelGap = 8.dp
        /** Figma §4.2 "helper below the box with an 8 gap". */
        val SupportGap = 8.dp
        /** Figma §4.2 "padding 16 all round" (16 + 24 body line + 16 = 56). */
        val Padding = 16.dp
        /** Figma §4.2 "item gap 8" between the value and a trailing element. */
        val ItemGap = 8.dp
        /** Figma §3.1 5a / §4.2: the eye target is 48 wide, flush with the inner right padding. */
        val RevealTargetWidth = 48.dp
        /** Build instruction with Figma §3.1 5a: the eye's tap height is the box's full 56. */
        val RevealTargetMinHeight = BoxMinHeight
    }

    object TextLink {
        /** Figma §5 "full-width 48 target"; NFR-101. */
        val MinTarget = TouchTarget
    }

    /** Figma §3.1 rows 6 and 6a. tokens.md has no checkbox (not one of the ten components). */
    object ConsentCheckbox {
        /** Figma §3.1 row 6: the row is 48 tall. */
        val RowMinHeight = TouchTarget
        /** Figma §3.1 6a: 24×24, radius 0. */
        val Box = 24.dp
        /** Figma §3.1 6a: 1 px `outlineStrong`. */
        val BoxStroke = 1.dp
        /** **Interpreted**: Figma does not draw the checked state; 16 keeps the check inside a 24 box. */
        val CheckGlyph = 16.dp
        /** Figma §3.1 row 6: gap 16 between box and text. */
        val Gap = 16.dp
    }

    object Scaffold {
        /** Figma §2 and §5: side margin 16 (content 358 on a 390 frame). */
        val Gutter = 16.dp
        /** Figma §2: 24 top and bottom, inside the system insets (D6, ruled by the orchestrator). */
        val PaddingVertical = 24.dp
        /** Figma §2: the root frame's item spacing, between header, content and action. */
        val ItemSpacing = 16.dp
        /** Figma §2: header back target 48×48 with a 24 chevron, no title. */
        val BackTarget = TouchTarget
    }
}
