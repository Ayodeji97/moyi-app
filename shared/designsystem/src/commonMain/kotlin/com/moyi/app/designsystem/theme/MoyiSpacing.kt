package com.moyi.app.designsystem.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The 8 pt spacing scale (`tokens.md` §3): `4 · 8 · 16 · 24 · 32 · 48 · 64`.
 *
 * - [xs] (4) is for optical adjustment inside a component, never for layout.
 * - Screen rhythm: [lg] (24) between blocks, [md] (16) inside a block.
 * - [gutter] is the screen's side padding. See its note.
 */
@Immutable
data class MoyiSpacing(
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 24.dp,
    val xl: Dp = 32.dp,
    val xxl: Dp = 48.dp,
    val xxxl: Dp = 64.dp,
    /** Side padding of every screen: 16 (Figma §2, content 358 on a 390 frame). */
    val gutter: Dp = MoyiDimens.Scaffold.Gutter,
    /** Minimum touch target on both platforms (NFR-101; iOS's 44 is the smaller, so 48). */
    val touchTarget: Dp = MoyiDimens.TouchTarget,
)

/**
 * Radius and elevation (`tokens.md` §3).
 *
 * - Radius is 12, plus [full] for the avatar. Nothing else.
 * - Elevation is a [hairline] `outline` border first; a shadow only where a
 *   surface genuinely floats. Nothing in this PR floats.
 */
@Immutable
data class MoyiShapes(
    val radius: CornerBasedShape = RoundedCornerShape(12.dp),
    val full: Shape = CircleShape,
    val hairline: Dp = MoyiDimens.PrimaryAction.Stroke,
)
