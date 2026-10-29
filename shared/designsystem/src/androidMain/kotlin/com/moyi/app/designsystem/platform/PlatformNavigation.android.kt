package com.moyi.app.designsystem.platform

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition

internal actual val SystemBackIsEdgeSwipe: Boolean = false

internal actual val TaskPresentsAsSheet: Boolean = false

/** A full-screen dialog, opened like every other screen: with no transition (SKILL.md §5). */
actual fun taskEnterTransition(): EnterTransition = EnterTransition.None

actual fun taskExitTransition(): ExitTransition = ExitTransition.None
