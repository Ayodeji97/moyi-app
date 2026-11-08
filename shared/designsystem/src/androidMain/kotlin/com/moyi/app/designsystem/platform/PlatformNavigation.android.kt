package com.moyi.app.designsystem.platform

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import com.moyi.app.designsystem.theme.MoyiMotion

internal actual val SystemBackIsEdgeSwipe: Boolean = false

internal actual val TaskPresentsAsSheet: Boolean = false

/**
 * A full-screen dialog (Write): it rises a short way from the bottom while fading in,
 * Material's motion for a dialog-like screen, and falls back the same way on close.
 */
actual fun taskEnterTransition(): EnterTransition =
    slideInVertically(tween(MoyiMotion.PushMillis, easing = FastOutSlowInEasing)) { it / AxisShiftFraction } +
        fadeIn(tween(MoyiMotion.FadeInMillis, delayMillis = MoyiMotion.FadeInDelayMillis, easing = LinearOutSlowInEasing))

actual fun taskExitTransition(): ExitTransition =
    slideOutVertically(tween(MoyiMotion.PushMillis, easing = FastOutSlowInEasing)) { it / AxisShiftFraction } +
        fadeOut(tween(MoyiMotion.FadeOutMillis))

/** Material's shared axis moves 30 dp on a phone; a tenth of the width is that, scaled. */
private const val AxisShiftFraction = 10

private fun slideTween() = tween<androidx.compose.ui.unit.IntOffset>(MoyiMotion.PushMillis, easing = FastOutSlowInEasing)

private fun fadeThroughIn() =
    fadeIn(tween(MoyiMotion.FadeInMillis, delayMillis = MoyiMotion.FadeInDelayMillis, easing = LinearOutSlowInEasing))

private fun fadeThroughOut() = fadeOut(tween(MoyiMotion.FadeOutMillis, easing = FastOutSlowInEasing))

actual fun screenEnterTransition(): EnterTransition =
    slideInHorizontally(slideTween()) { it / AxisShiftFraction } + fadeThroughIn()

actual fun screenExitTransition(): ExitTransition =
    slideOutHorizontally(slideTween()) { -it / AxisShiftFraction } + fadeThroughOut()

actual fun screenPopEnterTransition(): EnterTransition =
    slideInHorizontally(slideTween()) { -it / AxisShiftFraction } + fadeThroughIn()

actual fun screenPopExitTransition(): ExitTransition =
    slideOutHorizontally(slideTween()) { it / AxisShiftFraction } + fadeThroughOut()

actual fun reduceMotionEnabled(): Boolean = false
