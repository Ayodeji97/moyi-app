package com.moyi.app.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.moyi.app.designsystem.resources.Res
import com.moyi.app.designsystem.resources.newsreader_medium
import com.moyi.app.designsystem.resources.newsreader_regular
import org.jetbrains.compose.resources.Font

/**
 * The type scale (`tokens.md` §2): four sizes, not five, plus two derived
 * styles that reuse those sizes with the serif face.
 *
 * | Style    | Face                  | Size / line height                |
 * |----------|-----------------------|-----------------------------------|
 * | entry    | Newsreader 400        | 34 / 1.40                         |
 * | title    | Newsreader 400        | 26 / 1.25                         |
 * | body     | system sans           | 16 / 1.50                         |
 * | label    | system sans 500       | 13 / 1.40, uppercase, +0.11em     |
 * | action   | Newsreader 400        | 16 (body size, serif face)        |
 * | code     | Newsreader 400        | 26 (title size), +0.28em tracking |
 *
 * Sizes are `sp` and tracking and line height are `em`, so all of it follows
 * the platform font scale (NFR-102: 200 % without truncation).
 *
 * `label` is uppercase. A [TextStyle] cannot carry a text transform, so the
 * uppercasing lives in `MoyiText`; render labels through it, never through a
 * bare `Text`.
 */
@Immutable
data class MoyiTypography(
    val entry: TextStyle,
    val title: TextStyle,
    val body: TextStyle,
    val label: TextStyle,
    val action: TextStyle,
    val code: TextStyle,
    /** Newsreader Medium (500). The wordmark is an asset behind one token, not a type style. */
    val wordmarkFamily: FontFamily,
)

/**
 * The UI face. [FontFamily.Default] resolves to the platform system font:
 * Roboto on Android and SF on iOS. `tokens.md` §2 asks for the system font
 * "via expect/actual"; both actuals would be `FontFamily.Default`, so the
 * indirection is not written until a platform needs something different.
 * It inherits Dynamic Type and costs no bundle bytes.
 */
internal val SystemSans: FontFamily = FontFamily.Default

@Composable
internal fun rememberMoyiTypography(): MoyiTypography {
    // Font(resource) is @Composable: Compose resources load the file per
    // platform (Android assets, the iOS framework's compose-resources folder).
    val regular = Font(Res.font.newsreader_regular, FontWeight.Normal)
    val medium = Font(Res.font.newsreader_medium, FontWeight.Medium)
    return remember(regular, medium) {
        val newsreader = FontFamily(regular)
        moyiTypography(
            serif = newsreader,
            wordmark = FontFamily(medium),
        )
    }
}

internal fun moyiTypography(serif: FontFamily, wordmark: FontFamily): MoyiTypography = MoyiTypography(
    entry = TextStyle(
        fontFamily = serif,
        fontWeight = FontWeight.Normal,
        fontSize = 34.sp,
        lineHeight = 1.40.em,
    ),
    title = TextStyle(
        fontFamily = serif,
        fontWeight = FontWeight.Normal,
        fontSize = 26.sp,
        lineHeight = 1.25.em,
    ),
    body = TextStyle(
        fontFamily = SystemSans,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 1.50.em,
    ),
    label = TextStyle(
        fontFamily = SystemSans,
        // Medium (500): the Figma `type/label` style. tokens.md §2 names the
        // face and size and is silent on weight.
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 1.40.em,
        // 0.11em, never 1.43px: Figma stores pixels only because it forces a
        // bound letter-spacing into pixel units (tokens.md §2).
        letterSpacing = 0.11.em,
    ),
    action = TextStyle(
        fontFamily = serif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 1.50.em,
    ),
    code = TextStyle(
        fontFamily = serif,
        fontWeight = FontWeight.Normal,
        fontSize = 26.sp,
        lineHeight = 1.25.em,
        letterSpacing = 0.28.em,
    ),
    wordmarkFamily = wordmark,
)
