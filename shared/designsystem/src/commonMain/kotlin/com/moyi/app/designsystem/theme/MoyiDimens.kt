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
        /** tokens.md L284 "65 for Code"; onboarding spec §3.5 (Field · code 308:9, box 65). A minimum. */
        val CodeBoxMinHeight = 65.dp
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

    /**
     * tokens.md §4 component 7. Onboarding spec §3.4: the instance is 358×195 (342:53). The
     * component page (219:2) was not read, so the inner layout is **Interpreted**: padding 24,
     * caption, 24, code, 24, a 48 copy target: 24 + 19 + 24 + 32.5 + 24 + 48 + 24 = 195.5.
     */
    object InviteCode {
        val Padding = 24.dp
        val Gap = 24.dp
        val CopyTarget = TouchTarget
        val CopyGap = 8.dp
    }

    /**
     * tokens.md §4 component 5. Onboarding spec §3.8: "cannot be used" is 350×185 (310:26), at
     * its native 350 width. The page (72:2) was not read, so the inside is **Interpreted**:
     * padding 24, heading 33, 16, line 24, 16, action 48, padding 24 = 185.
     */
    object StateBlock {
        val NativeWidth = 350.dp
        val Padding = 24.dp
        val Gap = 16.dp
    }

    /**
     * tokens.md §4 component 1, and the daily-loop spec §5 (`docs/figma/today-check.md`).
     * Instance heights seen in Figma: 144, 226, 321 (hug the text) and 107 (tombstone).
     */
    object EntryCard {
        /** tokens.md §2 "card padding 24×2"; daily-loop spec §5. */
        val Padding = 24.dp
        /**
         * **Interpreted** from Figma heights: 226 = 24 + 19 (label) + 16 + 143 (three entry
         * lines) + 24, and 107 = 24 + 19 + 16 + 24 (one body line) + 24. The slot gap is 16.
         */
        val LabelGap = 16.dp
        /** tokens.md §3: elevation is a hairline `outline`, no shadow. */
        val Stroke = 1.dp
    }

    /**
     * tokens.md §3 and §6 ("the composer toolbar lives inside the field"): text and icon
     * glyphs align at 20 from the box edge. A 48 target centring a 24 glyph contributes 12;
     * the text wrap matches it with 12; the box's own padding is the remaining 8.
     */
    object ComposeField {
        /** Figma 181:3: the instance is 358 × 360 with the keyboard up. A minimum, not a cap. */
        val MinHeight = 360.dp
        /** Figma 384:229: the box shrinks to 328 when the save-failed line takes 32 of it. */
        val MinHeightWithError = 328.dp
        /** 20 (tokens.md §6 optical edge) − 12 (the wrap). **Derived**, not on the 8 pt scale on purpose. */
        val BoxPadding = 8.dp
        /** tokens.md §3 "one derived exception: 12 pt inside the composer" = (48 − 24) / 2. */
        val Wrap = 12.dp
        /** tokens.md §6: the field border identifies the control, so `outlineStrong`, 1. */
        val Stroke = 1.dp
        /** Icon-only media and dismiss controls, 48 targets (tokens.md §4). */
        val IconTarget = TouchTarget
    }

    /** Today's `nav` row (Figma 159:2: 358 × 48 at y 24; daily-loop spec §2). */
    object NavRow {
        /** Figma 159:3 / 429:1352: 48 tall, the targets 48 × 48. */
        val Height = TouchTarget
    }

    object Scaffold {
        /** Figma §2 and §5: side margin 16 (content 358 on a 390 frame). */
        val Gutter = 16.dp
        /** Figma §2: 24 top and bottom, inside the system insets (D6, ruled by the orchestrator). */
        val PaddingVertical = 24.dp
        /** Figma §2: the root frame's item spacing, between header, content and action. */
        val ItemSpacing = 16.dp
        /**
         * Not in Figma: the least height the content keeps beside a pinned
         * action block. Below it (or with actions over half the height) the
         * actions scroll with the content instead of being pinned.
         */
        val MinContentHeight = 120.dp
        /** Figma §2: header back target 48×48 with a 24 chevron, no title. */
        val BackTarget = TouchTarget
    }
}
