package com.moyi.app.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.moyi.app.designsystem.theme.MoyiColors
import com.moyi.app.designsystem.theme.MoyiShapes
import com.moyi.app.designsystem.theme.MoyiSpacing
import com.moyi.app.designsystem.theme.MoyiTypography
import com.moyi.app.designsystem.theme.RosewoodDark
import com.moyi.app.designsystem.theme.RosewoodLight
import com.moyi.app.designsystem.theme.rememberMoyiTypography

// static: a theme value changes rarely (a dark/light switch), and when it does
// the whole tree should recompose anyway. A static local skips the per-reader
// tracking a dynamic one pays for on every read.
internal val LocalMoyiColors = staticCompositionLocalOf<MoyiColors> {
    error("MoyiTheme is missing: wrap the content in MoyiTheme { }")
}
internal val LocalMoyiTypography = staticCompositionLocalOf<MoyiTypography> {
    error("MoyiTheme is missing: wrap the content in MoyiTheme { }")
}
internal val LocalMoyiSpacing = staticCompositionLocalOf { MoyiSpacing() }
internal val LocalMoyiShapes = staticCompositionLocalOf { MoyiShapes() }

/**
 * The Rosewood theme.
 *
 * Dark-first: it follows the system setting, and dark is the design
 * reference. Material 3 **dynamic colour is off**, always: the product has
 * its own palette (`13` §107), and nothing here reads the wallpaper.
 *
 * Material 3 stays underneath because its internals (ripples, text
 * selection handles, any stray `Text`) read `MaterialTheme`. Its colour
 * scheme, typography and shapes are therefore mapped from the Rosewood
 * tokens, so none of them can show Material's default purple or its type
 * scale. Screens read [MoyiTheme], never `MaterialTheme`.
 */
@Composable
fun MoyiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) RosewoodDark else RosewoodLight
    val typography = rememberMoyiTypography()
    val spacing = remember { MoyiSpacing() }
    val shapes = remember { MoyiShapes() }
    val materialColors = remember(colors) { colors.toMaterialColorScheme() }
    val materialTypography = remember(typography) { typography.toMaterialTypography() }
    val materialShapes = remember(shapes) { Shapes(
        extraSmall = shapes.radius,
        small = shapes.radius,
        medium = shapes.radius,
        large = shapes.radius,
        extraLarge = shapes.radius,
    ) }
    val selection = remember(colors) {
        TextSelectionColors(handleColor = colors.ink, backgroundColor = colors.ink.copy(alpha = 0.3f))
    }

    CompositionLocalProvider(
        LocalMoyiColors provides colors,
        LocalMoyiTypography provides typography,
        LocalMoyiSpacing provides spacing,
        LocalMoyiShapes provides shapes,
    ) {
        MaterialTheme(
            colorScheme = materialColors,
            typography = materialTypography,
            shapes = materialShapes,
        ) {
            // MaterialTheme provides its own selection colours from `primary`;
            // ours goes inside it so it wins.
            CompositionLocalProvider(
                LocalTextSelectionColors provides selection,
                LocalContentColor provides colors.ink,
                content = content,
            )
        }
    }
}

/** Entry point for reading the tokens: `MoyiTheme.colors.ink`, `MoyiTheme.type.body`. */
object MoyiTheme {
    val colors: MoyiColors
        @Composable @ReadOnlyComposable get() = LocalMoyiColors.current
    val type: MoyiTypography
        @Composable @ReadOnlyComposable get() = LocalMoyiTypography.current
    val spacing: MoyiSpacing
        @Composable @ReadOnlyComposable get() = LocalMoyiSpacing.current
    val shapes: MoyiShapes
        @Composable @ReadOnlyComposable get() = LocalMoyiShapes.current
}

/**
 * Every Material role is pointed at a Rosewood token. The mapping is by job,
 * not by name: `primary` is what Material fills and tints with, which in this
 * system is [MoyiColors.ink] (the PrimaryAction fill and the focus stroke).
 * `surfaceTint` is the ground itself, so tonal elevation adds no colour.
 */
internal fun MoyiColors.toMaterialColorScheme(): ColorScheme {
    val scheme = if (isDark) darkColorScheme() else lightColorScheme()
    return scheme.copy(
        primary = ink,
        onPrimary = surface,
        primaryContainer = surfaceRaised,
        onPrimaryContainer = ink,
        inversePrimary = surface,
        secondary = inkMuted,
        onSecondary = surface,
        secondaryContainer = surfaceRaised,
        onSecondaryContainer = ink,
        tertiary = inkMuted,
        onTertiary = surface,
        tertiaryContainer = surfaceRaised,
        onTertiaryContainer = ink,
        background = surface,
        onBackground = ink,
        surface = surface,
        onSurface = ink,
        surfaceVariant = surfaceRaised,
        onSurfaceVariant = inkMuted,
        surfaceTint = surface,
        inverseSurface = ink,
        inverseOnSurface = surface,
        error = danger,
        onError = surface,
        errorContainer = surfaceRaised,
        onErrorContainer = danger,
        outline = outlineStrong,
        outlineVariant = outline,
        scrim = Color.Black,
        surfaceBright = surfaceRaised,
        surfaceDim = surface,
        surfaceContainerLowest = surface,
        surfaceContainerLow = surface,
        surfaceContainer = surfaceRaised,
        surfaceContainerHigh = surfaceRaised,
        surfaceContainerHighest = surfaceRaised,
    )
}

/** Any Material text style falls back to a Rosewood one, so a stray M3 `Text` still reads right. */
internal fun MoyiTypography.toMaterialTypography(): Typography = Typography(
    displayLarge = entry,
    displayMedium = entry,
    displaySmall = entry,
    headlineLarge = title,
    headlineMedium = title,
    headlineSmall = title,
    titleLarge = title,
    titleMedium = body,
    titleSmall = body,
    bodyLarge = body,
    bodyMedium = body,
    bodySmall = body,
    labelLarge = action,
    labelMedium = label,
    labelSmall = label,
)
