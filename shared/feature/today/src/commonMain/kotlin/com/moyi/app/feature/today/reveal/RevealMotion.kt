package com.moyi.app.feature.today.reveal

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The reveal's timing: the product's whole motion budget (SKILL.md §5).
 *
 * TODO(motion): EVERY value here is a placeholder chosen to be visible and quiet,
 *  not read from Figma. The real values are on the motion board 180:21 (Reveal /
 *  Motion spec), never opened (daily-loop spec §8). The design system has no motion
 *  tokens yet; when the board is read, these become tokens in :shared:designsystem.
 */
internal object RevealMotion {
    /** How long the before frame (179:3) shows before it turns. */
    val BeforeHold: Duration = 1.2.seconds

    /** The after frame (179:8) holds this long, then settles into Today · revealed (159:19). */
    val AfterHold: Duration = 4.seconds

    /** The reveal itself: the after content fading in and rising. */
    const val RevealMillis = 700

    /** The before content leaving. */
    const val FadeOutMillis = 300

    /** The after content rises by its height divided by this (a small lift, not a slide). */
    const val RiseFraction = 12

    /** Reduced motion: a cross-fade with no movement (`14` §6). */
    const val CrossFadeMillis = 400

    // The settle into 159:19 is instant (navigation has no transitions); whether the
    // board choreographs it is unknown. TODO(motion).
}
