package com.moyi.app.designsystem.platform

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled

internal actual val SystemBackIsEdgeSwipe: Boolean = true

internal actual val TaskPresentsAsSheet: Boolean = true

/**
 * **Interpreted**: neither Figma nor tokens.md gives the sheet a duration. Short,
 * and a plain slide with no bounce or fade, so the one motion the product spends
 * on navigation stays as small as the sheet allows.
 */
private const val SheetMillis = 250

/**
 * Read at each transition rather than once, so turning Reduce Motion on applies
 * to the next sheet without a restart.
 */
actual fun taskEnterTransition(): EnterTransition =
    if (UIAccessibilityIsReduceMotionEnabled()) {
        EnterTransition.None
    } else {
        slideInVertically(tween(SheetMillis, easing = FastOutSlowInEasing)) { fullHeight -> fullHeight }
    }

actual fun taskExitTransition(): ExitTransition =
    if (UIAccessibilityIsReduceMotionEnabled()) {
        ExitTransition.None
    } else {
        slideOutVertically(tween(SheetMillis, easing = FastOutSlowInEasing)) { fullHeight -> fullHeight }
    }
