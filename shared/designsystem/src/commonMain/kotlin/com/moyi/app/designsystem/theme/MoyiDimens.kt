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
        /** tokens.md §4, component 8: the `Multiline` box is 140 tall, top-aligned (Field · detail 599:1790). A minimum. */
        val MultilineBoxMinHeight = 140.dp
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

    /**
     * The outlined secondary action (owner ruling 2026-11-06, PR 30 smoke test). Not in
     * Figma or tokens.md yet, so every value here is **Interpreted** from its siblings.
     */
    object SecondaryAction {
        /** NFR-101: at least 48 x 48, as [TextLink]; a minimum, so a 200 % label grows it. */
        val MinTarget = TouchTarget
        /**
         * 1, as PrimaryAction's Queued and Disabled strokes. Here the border is what makes it
         * read as a control, so it is drawn in `ink` or `danger`, never the decorative `outline`.
         */
        val Stroke = 1.dp
        /** The same 32 as PrimaryAction, so the two read as one family. */
        val PaddingHorizontal = 32.dp
        /** Like PrimaryAction, the label is centred in the 48; padding only matters when it wraps. */
        val PaddingVertical = 8.dp
        /** Between two side by side ([com.moyi.app.designsystem.component.SecondaryActionPair]) or stacked: the 8 the stack already used. */
        val PairGap = 8.dp
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
    /**
     * tokens.md §4 component 4. Figma 227:3: a calendar cell is a 48 x 48 instance, the key's
     * swatch 20 x 20 (267:102). The page that draws each state was not read (no screenshot,
     * rate limit), so the strokes are **Interpreted**: the 2 pt emphasis stroke `Field` uses
     * for a shape that must carry meaning, and a 4 on 4 dash for the rest day's ring.
     */
    object DayCell {
        val Size = TouchTarget
        val KeySize = 20.dp
        val Stroke = 2.dp
        val Dash = 4.dp
    }

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
        /**
         * PROPOSED (docs/missing-components.md §66, the owner's iPhone, 2026-11-10): the words
         * never get fewer lines than this, however much is attached; the box scrolls instead.
         */
        const val MinTextLines = 3
        /**
         * PROPOSED (§66), **Interpreted**: the photo's thumbnail while the keyboard is up, inside
         * its 48 line with the 4 pt half-step above and below, like a 24 glyph in its 48 target.
         */
        val CompactThumbnail = 40.dp
    }

    /**
     * The voice-note waveform (PROPOSED, docs/missing-components.md §46): rounded vertical
     * bars, as messaging apps draw a voice message. Not in Figma or tokens.md, so every value
     * is **Interpreted**: a 48 tall target (NFR-101, it seeks) around a 32 tall drawing, on
     * the 4 pt half-step the composer already uses for optical sizes.
     */
    object Waveform {
        /** The touch height: tap or drag anywhere in it to seek. */
        val Target = TouchTarget
        /** The tallest bar. */
        val Height = 32.dp
        /** A bar's width; its ends are fully rounded. */
        val BarWidth = 3.dp
        /** Between two bars. */
        val BarGap = 2.dp
        /** The shortest bar, so silence still reads as the line of the voice note, a row of dots. */
        val MinBar = 3.dp
        /** Between the waveform and the control or the clock beside it. */
        val Gap = 8.dp
        /** The `danger` dot before the clock while recording (owner ruling 2026-11-09, §49). */
        val RecordingDot = 8.dp
        /**
         * PROPOSED (docs/missing-components.md §68), **Interpreted**: the narrowest the bars may
         * get with the clock and the speed pill beside them on one line (16 bars). Narrower, and
         * the clock and the pill move to a second line under the bars instead.
         */
        val MinWidth = 80.dp
    }

    /**
     * The voice note's play/pause control (PROPOSED, docs/missing-components.md §68).
     * **Interpreted** from [Chip]: a 40 visible circle with a `hairline` of `outlineStrong`,
     * centred in its 48 target (NFR-101), the 24 glyph in `ink` inside it.
     */
    object VoicePlay {
        val Target = TouchTarget
        val Circle = 40.dp
    }

    /**
     * The speed pill beside a voice note's clock (PROPOSED, docs/missing-components.md §64).
     * **Interpreted**: the visible pill hugs its `label` line (`spacing.xs` above and below,
     * `spacing.sm` across) so the clock line keeps its height; its target is the full 48
     * (NFR-101), reaching past the pill above and below.
     */
    object SpeedPill {
        val Target = TouchTarget
    }

    /** Today's `nav` row (Figma 159:2: 358 × 48 at y 24; daily-loop spec §2). */
    /**
     * tokens.md §4 row 9, `Chip` (Archive's filters): a 48 pt target wrapping a 40 pt
     * visible pill. Padding and gap are read from Figma's filters row (440:1475: All days
     * 100 wide, 8 between chips); the component page itself was not read.
     */
    object Chip {
        val Target = TouchTarget
        val PillHeight = 40.dp
        val PaddingHorizontal = 16.dp
        val Stroke = 1.dp
        val Gap = 8.dp
    }

    /** tokens.md §4 row 10, `FavouriteToggle`: a 48 pt row, a 24 px glyph, then the label. */
    object FavouriteToggle {
        val Target = TouchTarget
        val Gap = 8.dp
    }

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
        /**
         * Keyboard up: the gap between the action and the keyboard. Figma 181:3 and
         * 362:1196 draw the action frame 472 to 544 with the button 480 to 528.
         */
        val KeyboardGap = 16.dp
        /** Figma §2: header back target 48×48 with a 24 chevron, no title. */
        val BackTarget = TouchTarget
        /**
         * **Interpreted**, not in Figma: how far a swipe down on an iOS task's
         * header must travel to count as Cancel (D10, sheet style). Longer than
         * the 48 row, so a tap that wobbles is never a close.
         */
        val SwipeDownToClose = 64.dp
    }
}
