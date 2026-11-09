package com.moyi.app.splash

import com.moyi.app.feature.today.common.DailyLoopState

/**
 * Which kind of screen the shell is on at launch, for [firstScreenDrawn].
 *
 * - [None]: the NavHost has no destination yet.
 * - [SignedInStart]: the signed-in start, deciding where the person goes.
 * - [Today]: Today, which draws the cached day at once or loads one.
 * - [Other]: any screen that draws on arrival (the promise, onboarding, the gate,
 *   the archived bond, the reveal).
 */
internal enum class StartScreen { None, SignedInStart, Today, Other }

/**
 * Whether the first screen has something on it, so the launch splash may hand over to
 * it. The splash must never give way to a bare `surface`: after the logo, a blank
 * frame reads as a glitch (owner, PR #56).
 *
 * - The signed-in start is the bare surface while it asks the server ([startFailed]: its
 *   Generic error is a real frame).
 * - Today is the bare surface while [day] is [DailyLoopState.Loading]: with a cached day
 *   it is `Ready` in the first moments (owner ruling 2026-11-08: open straight to Today and
 *   sync behind it); with none, once the server answers or the load fails.
 *   [DailyLoopState.NoOpenBond] sends the person back to the signed-in start.
 * - Every other screen draws on arrival.
 */
internal fun firstScreenDrawn(screen: StartScreen, day: DailyLoopState, startFailed: Boolean): Boolean = when (screen) {
    StartScreen.None -> false
    StartScreen.SignedInStart -> startFailed
    StartScreen.Today -> day is DailyLoopState.Ready || day is DailyLoopState.Failed
    StartScreen.Other -> true
}
