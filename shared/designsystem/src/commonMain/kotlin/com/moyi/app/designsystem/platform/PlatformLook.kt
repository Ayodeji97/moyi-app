package com.moyi.app.designsystem.platform

import androidx.compose.foundation.Indication
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/*
 * Where Android and iOS behave differently, the platform's convention wins
 * (owner ruling, 2026-10-29; docs/figma/figma-deviations.md D2, D5, D6). Figma
 * keeps colour, type, spacing and copy; these decide only the behaviour and the
 * one glyph each platform's users read as "back".
 *
 * Each is an `expect` declaration with one `actual` per platform, so common
 * code asks one question and never branches on the platform itself.
 */

/**
 * The back glyph (D2): Material's arrow on Android, the chevron on iOS. Tinted
 * at the call site with `ink`, like every icon in [com.moyi.app.designsystem.icon.MoyiIcons].
 */
internal expect val PlatformBackIcon: ImageVector

/**
 * Press feedback (D5): a ripple on Android, a dim on iOS. Provided once as
 * `LocalIndication` by `MoyiTheme`, so every `clickable` gets it with no
 * argument. [color] tints the Android ripple (Unspecified reads
 * `LocalContentColor`); the iOS dim has no colour and ignores it.
 */
internal expect fun platformPressIndication(color: Color = Color.Unspecified): Indication

/**
 * Whether tapping outside a field, or dragging the content, puts the keyboard
 * away (D6). True on iOS, which has no back key to close it; false on Android,
 * where Back closes the keyboard and a tap outside leaves it up.
 */
internal expect val DismissKeyboardOnTapAndDrag: Boolean
