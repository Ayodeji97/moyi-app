package com.moyi.app.feature.today.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.moyi.app.feature.today.delete.DeleteEntryRoot
import com.moyi.app.feature.today.reveal.RevealRoot
import com.moyi.app.feature.today.today.TodayRoot
import com.moyi.app.feature.today.write.WriteRoot
import kotlinx.serialization.Serializable

/** Today, the root of the signed-in app. The shell navigates here; nothing sits behind it. */
@Serializable
data object TodayRoute

/** The composer. [editing] is true from "Edit what you wrote" (the action reads Save). */
@Serializable
data class WriteRoute(val editing: Boolean = false)

/** The reveal: a state of Today shown as its own destination for the moment, then replaced. */
@Serializable
data object RevealRoute

/** Delete after reveal: the confirmation. */
@Serializable
data object DeleteEntryRoute

/**
 * The daily loop. Back stack, from Today:
 *
 * - Today → Write (push). Write → back, or a send or save, pops to Today.
 * - Today → Reveal **replaces** Today, and the reveal settling replaces itself with
 *   Today again: the reveal is a state of Today, not somewhere to go back to (SKILL.md §9).
 * - Today → delete confirm (push). Delete or Cancel pops to Today.
 * - Settings and Archive leave the feature through callbacks; the shell decides where.
 *
 * Every transition is instant (the NavHost sets none): the reveal is the only motion.
 *
 * @param onOpenSettings the settings target in Today's nav row. TODO(logic): Bond
 *   settings (`GET /api/v1/bonds/{bondId}`, `…/members/me/settings`) is not built; the
 *   shell opens the auth placeholder for now, which is where sign-out lives.
 * @param debugTools PR #8 only: long-press switches that step the fake state. The
 *   shell passes true only for `-PmoyiUiPreview=today` builds.
 */
fun NavGraphBuilder.todayGraph(
    navController: NavController,
    onOpenSettings: () -> Unit,
    debugTools: Boolean,
) {
    composable<TodayRoute> {
        TodayRoot(
            onNavigateToWrite = { editing -> navController.navigate(WriteRoute(editing)) },
            onNavigateToReveal = { navController.replaceWith(RevealRoute) },
            onNavigateToDeleteConfirm = { navController.navigate(DeleteEntryRoute) },
            onNavigateToSettings = onOpenSettings,
            debugTools = debugTools,
        )
    }
    composable<WriteRoute> { entry ->
        val route = entry.toRoute<WriteRoute>()
        WriteRoot(
            editing = route.editing,
            onNavigateBack = { navController.popBackStack(TodayRoute, inclusive = false) },
            debugTools = debugTools,
        )
    }
    composable<RevealRoute> {
        RevealRoot(
            onSettle = { navController.replaceWith(TodayRoute) },
            debugTools = debugTools,
        )
    }
    composable<DeleteEntryRoute> {
        DeleteEntryRoot(onNavigateBack = { navController.popBackStack(TodayRoute, inclusive = false) })
    }
}

/** Swaps the current destination for [route], so back skips it. */
private fun NavController.replaceWith(route: Any) {
    val current = currentDestination?.id ?: return navigate(route)
    navigate(route) {
        popUpTo(current) { inclusive = true }
        launchSingleTop = true
    }
}
