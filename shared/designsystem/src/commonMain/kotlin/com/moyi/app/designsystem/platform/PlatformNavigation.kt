package com.moyi.app.designsystem.platform

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState

/*
 * How each platform goes back and closes (owner, 2026-10-29; figma-deviations.md
 * "When a screen has a back arrow", D3, D10, D12):
 *
 * - A pushed screen has a back arrow, and system back does what the arrow does.
 *   The NavHost already pops on system back; nothing here is needed for it.
 * - A task (Write) closes instead of going back: an X on Android, Cancel on iOS.
 * - The iOS edge swipe follows the arrow exactly. Where there is no arrow (a root
 *   screen, a task, the reveal) it does nothing.
 */

/**
 * What the platform's system back is: false on Android (the Back button or the
 * back gesture, both meaning "back" everywhere), true on iOS (Compose's left-edge
 * swipe, which iOS people read as "go back to the screen under this one", D3).
 */
internal expect val SystemBackIsEdgeSwipe: Boolean

/**
 * Whether a task is presented as a sheet (iOS: it slides up, says Cancel, and a
 * swipe down closes it) or as a full-screen dialog (Android: no motion, an X).
 */
internal expect val TaskPresentsAsSheet: Boolean

/**
 * System back on a screen with **no back arrow**, which still has to answer it:
 * a task (Write, D10) or a moment (the reveal, D12).
 *
 * - **Android**: Back runs [onBack]. Without this, Back would pop the NavHost (and
 *   drop typed words without asking), or close the app from a screen that is the
 *   only one on the stack.
 * - **iOS**: the edge swipe is taken and does nothing. The swipe follows the arrow
 *   (D3), and this screen has none. A task closes with Cancel or a swipe down instead.
 *
 * Built on the Navigation Event APIs (`PredictiveBackHandler` is deprecated from
 * Compose Multiplatform 1.10). The most recently registered enabled handler takes
 * the event, so this one, composed inside the destination, wins over the NavHost's.
 * While [enabled] is false it steps aside and the NavHost decides as usual.
 *
 * Call it from a screen's Root, not its Screen: it needs the navigation event
 * dispatcher the platform host provides, which previews do not have.
 */
@Composable
fun NoArrowBackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    val state = rememberNavigationEventState(NavigationEventInfo.None)
    NavigationBackHandler(
        state = state,
        isBackEnabled = enabled,
        // NavigationBackHandler refreshes this lambda on every composition, so it
        // always calls the latest onBack.
        onBackCompleted = { if (!SystemBackIsEdgeSwipe) onBack() },
    )
}

/**
 * How a task enters (D10). iOS: the sheet slides up from the bottom edge, the one
 * transition outside the reveal, approved by the owner as an exception to "screens
 * change with no transition" (SKILL.md §5). None under Reduce Motion. Android: none.
 */
expect fun taskEnterTransition(): EnterTransition

/** How a task leaves (D10): the reverse of [taskEnterTransition]. */
expect fun taskExitTransition(): ExitTransition
