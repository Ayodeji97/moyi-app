package com.moyi.app.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.moyi.app.designsystem.MoyiTheme
import com.moyi.app.designsystem.platform.rememberReduceMotion
import com.moyi.app.designsystem.resources.Res
import com.moyi.app.designsystem.resources.ds_wordmark
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/**
 * The first Compose frame and the launch animation over it (owner request 2026-11-09).
 *
 * The native launch frame on both platforms is `surface` alone (Info.plist
 * `UILaunchScreen`; Android's window background and, on API 31+, a blank splash icon), and
 * this begins as exactly that, so nothing jumps when Compose takes over. Then the logo
 * builds: the ring, the dot settling under it, the wordmark ([SplashTimeline]).
 *
 * It sits **above** the app, which is already composing underneath: the session restore
 * and whatever the first screen loads are never delayed by it. [ready] turns true when the
 * first screen is chosen ([signedIn] says which); the splash then leaves by
 * [SplashTimeline.handOff] and calls [onFinished] once it has faded out.
 *
 * Under Reduce Motion (iOS) or Remove animations (Android) it draws the finished lockup,
 * still, from the first frame, and leaves by the same rule. The timeline is driven frame by
 * frame rather than by an animation spec, so Android's animator scale does not reach it on
 * its own: [rememberReduceMotion] is what stills it.
 *
 * Decorative: screen readers hear nothing from it (the system already names the app it
 * opens), and touches do not reach the screen under it while it shows.
 */
@Composable
internal fun LaunchSplash(ready: Boolean, signedIn: Boolean, onFinished: () -> Unit) {
    val reduceMotion = rememberReduceMotion()
    val timeline = remember { mutableFloatStateOf(0f) }
    val opacity = remember { Animatable(1f) }
    val finished by rememberUpdatedState(onFinished)

    // Until the first screen is chosen, the entrance plays at its written pace and then
    // holds. When it is chosen, this effect restarts where the timeline stands and plays
    // the hand-off: the rest of the entrance (faster, if the rule says so), the hold, the exit.
    LaunchedEffect(ready) {
        val end = SplashTimeline.EntranceEndMillis.toFloat()
        if (!ready) {
            timeline.playTo(end, speed = 1f)
            return@LaunchedEffect
        }
        val handOff = SplashTimeline.handOff(timeline.floatValue, signedIn = signedIn)
        if (handOff.entranceMillis > 0) {
            timeline.playTo(end, speed = (end - timeline.floatValue) / handOff.entranceMillis)
        }
        delay(handOff.holdMillis.toLong())
        opacity.animateTo(0f, tween(SplashTimeline.ExitMillis, easing = LinearEasing))
        finished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = opacity.value }
            .background(MoyiTheme.colors.surface)
            .clearAndSetSemantics { }
            // Swallows every touch, so nothing reaches the screen composing underneath.
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } },
        contentAlignment = Alignment.Center,
    ) {
        SplashLockup(frame = { if (reduceMotion) SplashTimeline.FinalFrame else SplashTimeline.frameAt(timeline.floatValue) })
    }
}

/** Moves the timeline frame by frame to [target], at most one capped step per frame ([SplashTimeline.advance]). */
private suspend fun MutableFloatState.playTo(target: Float, speed: Float) {
    var last = withFrameMillis { it }
    while (floatValue < target) {
        withFrameMillis { now ->
            floatValue = SplashTimeline.advance(floatValue, (now - last).toFloat(), speed, target)
            last = now
        }
    }
}

/**
 * The mark above the wordmark, centred as one block. The [frame] lambda is read only in
 * the draw and layer phases, so the animation never recomposes.
 */
@Composable
private fun SplashLockup(frame: () -> SplashFrame) {
    val ink = MoyiTheme.colors.ink
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        OMark(color = ink, height = SplashLockupSize.MarkHeight, frame = frame)
        Spacer(Modifier.height(MoyiTheme.spacing.lg))
        // The wordmark is a logo, not text: it keeps its size whatever the font scale.
        val size = with(LocalDensity.current) { SplashLockupSize.WordmarkHeight.toSp() }
        BasicText(
            text = stringResource(Res.string.ds_wordmark),
            modifier = Modifier.graphicsLayer { alpha = frame().wordmarkAlpha },
            style = TextStyle(
                color = ink,
                fontFamily = MoyiTheme.type.wordmarkFamily,
                fontWeight = FontWeight.Medium,
                fontSize = size,
                lineHeight = size,
            ),
        )
    }
}

/**
 * The "o-dot" mark, variant V1 "open gap" (tools/icon/README.md), drawn from the same
 * geometry as the app icon: on a 160-unit canvas a ring at (80, 58), radius 36, stroke 13,
 * and a dot at (80, 124.5), radius 15. Its box is 85 by 124 units.
 */
@Composable
private fun OMark(color: Color, height: Dp, frame: () -> SplashFrame) {
    Canvas(Modifier.size(width = height * (MarkBox.WIDTH / MarkBox.HEIGHT), height = height)) {
        val unit = size.height / MarkBox.HEIGHT
        val f = frame()
        val centreX = size.width / 2
        val ringCentre = Offset(centreX, (MarkBox.RING_CY - MarkBox.TOP) * unit)
        scale(f.ringScale, pivot = ringCentre) {
            drawCircle(
                color = color,
                radius = MarkBox.RING_R * unit,
                center = ringCentre,
                alpha = f.ringAlpha,
                style = Stroke(width = MarkBox.RING_STROKE * unit),
            )
        }
        val dotRadius = MarkBox.DOT_R * unit
        drawCircle(
            color = color,
            radius = dotRadius,
            center = Offset(centreX, (MarkBox.DOT_CY - MarkBox.TOP) * unit - f.dotLift * 2 * dotRadius),
            alpha = f.dotAlpha,
        )
    }
}

/** Sizes of the lockup. The wordmark's 48 is Figma's splash wordmark box (379:5) and the logo's 48 px minimum (`19` §6). */
internal object SplashLockupSize {
    val MarkHeight: Dp = 96.dp
    val WordmarkHeight: Dp = 48.dp
}

/** The mark's geometry in icon units (tools/icon/make_icon.py). */
private object MarkBox {
    const val RING_CY = 58f
    const val RING_R = 36f
    const val RING_STROKE = 13f
    const val DOT_R = 15f
    const val GAP = 9f
    const val DOT_CY = RING_CY + RING_R + RING_STROKE / 2 + GAP + DOT_R // 124.5
    const val TOP = RING_CY - RING_R - RING_STROKE / 2 // 15.5
    const val HEIGHT = DOT_CY + DOT_R - TOP // 124
    const val WIDTH = 2 * (RING_R + RING_STROKE / 2) // 85
}
