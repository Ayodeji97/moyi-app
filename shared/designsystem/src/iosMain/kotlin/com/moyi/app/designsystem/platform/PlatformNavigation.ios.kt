package com.moyi.app.designsystem.platform

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.unit.IntOffset
import com.moyi.app.designsystem.theme.MoyiMotion
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

/** The screen underneath drifts a third of the width, as UIKit's does. */
private const val ParallaxFraction = 3

private fun pushTween() = tween<IntOffset>(MoyiMotion.PushMillisIos, easing = MoyiMotion.IosNavigationEasing)

private fun crossFadeIn() = fadeIn(tween(MoyiMotion.CrossFadeMillis))

private fun crossFadeOut() = fadeOut(tween(MoyiMotion.CrossFadeMillis))

actual fun screenEnterTransition(): EnterTransition =
    if (reduceMotionEnabled()) crossFadeIn() else slideInHorizontally(pushTween()) { it }

actual fun screenExitTransition(): ExitTransition =
    if (reduceMotionEnabled()) crossFadeOut() else slideOutHorizontally(pushTween()) { -it / ParallaxFraction }

actual fun screenPopEnterTransition(): EnterTransition =
    if (reduceMotionEnabled()) crossFadeIn() else slideInHorizontally(pushTween()) { -it / ParallaxFraction }

actual fun screenPopExitTransition(): ExitTransition =
    if (reduceMotionEnabled()) crossFadeOut() else slideOutHorizontally(pushTween()) { it }

actual fun reduceMotionEnabled(): Boolean = UIAccessibilityIsReduceMotionEnabled()
