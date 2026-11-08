package com.moyi.app.designsystem.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing

/**
 * Motion beyond the reveal. **Owner ruling, 2026-11-08** (PR smoke test): screens move
 * when they change, and a control that is busy shows it is alive. This amends SKILL.md §5
 * ("Motion budget: one"), which `tokens.md` does not yet reflect; see
 * docs/missing-components.md §33. The reveal stays the one *expressive* animation: all
 * of this is functional motion, short, with no bounce, overshoot or decoration.
 *
 * Every value respects the platform's reduced-motion setting: Android scales or skips
 * Compose animations with the system animator scale on its own, and iOS checks Reduce
 * Motion through [com.moyi.app.designsystem.platform.reduceMotionEnabled].
 */
object MoyiMotion {
    /** A pushed screen sliding in (Android's shared axis, iOS's navigation push). */
    const val PushMillis = 300

    /** iOS pushes run a touch longer, as UINavigationController's do. */
    const val PushMillisIos = 350

    /** The incoming screen's fade, after the outgoing one has faded (Android, Material). */
    const val FadeInMillis = 210
    const val FadeInDelayMillis = 90
    const val FadeOutMillis = 90

    /** A cross-fade standing in for movement when reduced motion is on. */
    const val CrossFadeMillis = 200

    /** A control changing state: a label swapping, a button dimming while it sends. */
    const val ControlMillis = 150

    /** One half of the busy label's breath (dim, then back), repeated while sending. */
    const val BreatheMillis = 900

    /** The lowest alpha the busy label breathes down to: still well above contrast minimums on the dimmed button. */
    const val BreatheMinAlpha = 0.55f

    /** Decelerating, close to UIKit's navigation curve; used by the iOS push. */
    val IosNavigationEasing: Easing = CubicBezierEasing(0.2f, 0.9f, 0.1f, 1f)
}
