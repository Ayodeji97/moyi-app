package com.moyi.app.feature.today.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.moyi.app.designsystem.platform.taskEnterTransition
import com.moyi.app.designsystem.platform.taskExitTransition
import com.moyi.app.feature.today.archive.ArchiveDayRoot
import com.moyi.app.feature.today.archive.ArchiveRoot
import com.moyi.app.feature.today.reveal.RevealRoot
import com.moyi.app.feature.today.streak.StreakRoot
import com.moyi.app.feature.today.today.TodayRoot
import com.moyi.app.feature.today.write.WriteRoot
import kotlinx.serialization.Serializable

/** Today, the root of the signed-in app. The shell navigates here; nothing sits behind it. */
@Serializable
data object TodayRoute

/** The composer, a task (D10). [editing] is true from "Edit what you wrote" (the action reads Save). */
@Serializable
data class WriteRoute(val editing: Boolean = false)

/** The reveal: a moment on top of Today, which it settles back into (D12). */
@Serializable
data object RevealRoute

/** The Archive, from Today's calendar target. Back returns to Today. */
@Serializable
data object ArchiveRoute

/** One day of the Archive, [date] in ISO form (`2026-09-14`), from a day card. Back returns to the list. */
@Serializable
data class ArchiveDayRoute(val date: String)

/** The Streak screen, from the Archive's "Your streak". Back returns to the Archive. */
@Serializable
data object StreakRoute

/**
 * The daily loop. Back stack, from Today:
 *
 * - Today → Write (push), a **task** (D10): it closes, it does not go back. Its X
 *   (Android) or Cancel (iOS), Android Back and the iOS swipe down all close it,
 *   asking first if words would be lost. A send or save closes it too. The iOS edge
 *   swipe does nothing on it: there is no arrow to follow (D3).
 * - Today → Reveal (push, D12). The reveal settling pops back to Today, now
 *   revealed. Android Back during it settles it the same way, early; before D12
 *   the reveal replaced Today, so Back closed the app mid-moment. The iOS edge
 *   swipe does nothing: the reveal has no arrow, and settles on its own.
 * - Deleting a revealed entry confirms in a dialog over Today (D11): no destination.
 * - Today → Archive (push), from the calendar target → a day (push), from a card. Back
 *   (the arrow, Android Back, the iOS edge swipe) goes one step down each time. The
 *   empty Archive's Write opens the composer over it; Write closing lands on Today.
 * - Archive → Streak (push), from "Your streak". Back returns to the Archive.
 * - Settings leaves the feature through a callback; the shell decides where.
 *
 * Every transition is instant (the NavHost sets none), with one exception: on iOS,
 * Write slides up and down as a sheet (owner approval, 2026-10-29), and not under
 * Reduce Motion. Today stays drawn beneath it while it moves.
 *
 * @param onOpenSettings the settings target in Today's nav row, with the day's bond:
 *   the shell opens Bond settings.
 * @param onReport the day view's Report: the shell opens the report flow over it (feature
 *   bond), about the partner's entry when one is given, or the member.
 * @param onNoOpenBond the person is in no open bond any more (left or ended on the
 *   other phone). The shell sends them back to the signed-in start, which decides.
 * @param debugTools long-press switches that step the preview day. The shell passes
 *   true only for `-PmoyiUiPreview=today` builds.
 */
fun NavGraphBuilder.todayGraph(
    navController: NavController,
    onOpenSettings: (bondId: String) -> Unit,
    onReport: (bondId: String, entryId: String?, partnerName: String?) -> Unit,
    onNoOpenBond: () -> Unit,
    /** "I have an invite code" on Today: the join flow from the bond nobody has joined (in onboarding). */
    onJoinWithCode: (bondId: String, bondName: String) -> Unit,
    debugTools: Boolean,
) {
    composable<TodayRoute>(
        // Today stays drawn under Write while the task rises over it. Anywhere else,
        // null falls back to the NavHost's push (MoyiMotion).
        exitTransition = {
            if (targetState.destination.hasRoute<WriteRoute>()) ExitTransition.KeepUntilTransitionsFinished else null
        },
    ) {
        TodayRoot(
            onNavigateToWrite = { editing -> navController.navigate(WriteRoute(editing)) },
            onNavigateToReveal = { navController.openReveal() },
            onNavigateToSettings = onOpenSettings,
            onNavigateToArchive = { navController.navigate(ArchiveRoute) { launchSingleTop = true } },
            onNoOpenBond = onNoOpenBond,
            onNavigateToJoin = onJoinWithCode,
            debugTools = debugTools,
        )
    }
    composable<WriteRoute>(
        enterTransition = { taskEnterTransition() },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { taskExitTransition() },
    ) { entry ->
        val route = entry.toRoute<WriteRoute>()
        WriteRoot(
            editing = route.editing,
            onClose = { navController.backToToday() },
            debugTools = debugTools,
        )
    }
    composable<ArchiveRoute> {
        ArchiveRoot(
            onBack = { navController.backToToday() },
            onOpenDay = { date -> navController.navigate(ArchiveDayRoute(date.toString())) { launchSingleTop = true } },
            onWrite = { navController.navigate(WriteRoute()) },
            onOpenStreak = { navController.navigate(StreakRoute) { launchSingleTop = true } },
            debugTools = debugTools,
        )
    }
    composable<StreakRoute> {
        StreakRoot(onBack = { navController.popBackStack() }, debugTools = debugTools)
    }
    composable<ArchiveDayRoute> { entry ->
        ArchiveDayRoot(
            date = entry.toRoute<ArchiveDayRoute>().date,
            onBack = { navController.popBackStack() },
            onReport = onReport,
        )
    }
    composable<RevealRoute> {
        RevealRoot(
            onSettle = { navController.backToToday() },
            debugTools = debugTools,
        )
    }
}

/** Opens the reveal over Today, so Back from it lands on Today (D12). */
internal fun NavController.openReveal() {
    navigate(RevealRoute) { launchSingleTop = true }
}

/**
 * Back to Today from a screen above it: Write closing, the reveal settling.
 *
 * If Today is not on the stack (TODO(logic): the reveal opened from the "Your day
 * is open." push, with nothing under it), Today replaces the current screen, so
 * the person still lands on Today and never outside the app.
 */
internal fun NavController.backToToday() {
    if (popBackStack(TodayRoute, inclusive = false)) return
    val current = currentDestination?.id ?: return navigate(TodayRoute)
    navigate(TodayRoute) {
        popUpTo(current) { inclusive = true }
        launchSingleTop = true
    }
}
