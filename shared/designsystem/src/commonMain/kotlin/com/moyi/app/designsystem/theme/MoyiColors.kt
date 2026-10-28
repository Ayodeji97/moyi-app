package com.moyi.app.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * The Rosewood palette: seven colour tokens, no accent (moyi-design-system
 * `tokens.md` §1, decided 2026-09-03).
 *
 * Names are semantic and flat, matching the Figma variables exactly, so a
 * rename of the product is a values change and never a refactor.
 *
 * - There is **no accent**. Secondary actions are [ink] with an underline.
 * - [danger] is the only hue and is reserved for destructive confirmation
 *   and error text. It never appears on a streak surface.
 * - There is **no `onDanger`**: text on a [danger] fill uses [surface]
 *   (6.54:1 dark, 5.57:1 light).
 * - [outline] is a decorative hairline. Any border that is the only thing
 *   identifying a control uses [outlineStrong].
 */
@Immutable
data class MoyiColors(
    /** App ground. */
    val surface: Color,
    /** Entry card, sheet. */
    val surfaceRaised: Color,
    /** Body and entry text; the PrimaryAction fill; Field focus stroke. */
    val ink: Color,
    /** Dates, counters, secondary text. */
    val inkMuted: Color,
    /** Decorative hairline, divider. Never the only cue for a control. */
    val outline: Color,
    /** Field and control borders. */
    val outlineStrong: Color,
    /** Destructive confirm and error text only. */
    val danger: Color,
    /** True for the dark palette, which is the design reference. */
    val isDark: Boolean,
)

/** Dark is authoritative (`tokens.md` §1): most use is at night, in bed. */
val RosewoodDark = MoyiColors(
    surface = Color(0xFF171313),
    surfaceRaised = Color(0xFF211A1A),
    ink = Color(0xFFF8EFE9),
    inkMuted = Color(0xFFCDBBB5),
    outline = Color(0xFF493737),
    outlineStrong = Color(0xFF7E6A6A),
    danger = Color(0xFFD4857C),
    isDark = true,
)

/** Derived from dark (`tokens.md` §1). */
val RosewoodLight = MoyiColors(
    surface = Color(0xFFFFF9F5),
    surfaceRaised = Color(0xFFFFFFFF),
    ink = Color(0xFF2A2020),
    inkMuted = Color(0xFF695A57),
    outline = Color(0xFFE9D8D1),
    outlineStrong = Color(0xFF827270),
    danger = Color(0xFF9D4E46),
    isDark = false,
)
