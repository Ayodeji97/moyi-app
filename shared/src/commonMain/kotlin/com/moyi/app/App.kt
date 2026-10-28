package com.moyi.app

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.moyi.app.designsystem.MoyiTheme
import com.moyi.app.designsystem.presentation.ObserveAsEvents
import com.moyi.app.domain.auth.SessionEndReason
import com.moyi.app.domain.auth.SessionEvent
import com.moyi.app.domain.auth.SessionState
import com.moyi.app.debug.UiPreviewRoute
import com.moyi.app.debug.uiPreviewGraph
import com.moyi.app.feature.auth.navigation.HomePlaceholderRoute
import com.moyi.app.feature.auth.navigation.LoginRoute
import com.moyi.app.feature.auth.navigation.RegisterRoute
import com.moyi.app.feature.auth.navigation.authGraph
import com.moyi.app.feature.onboarding.navigation.OnboardingRoute
import com.moyi.app.feature.onboarding.navigation.PromiseRoute
import com.moyi.app.feature.onboarding.navigation.onboardingGraph
import org.koin.compose.viewmodel.koinViewModel

/**
 * The app: the session decides the first screen.
 *
 * - **Unknown** (restoring): a blank `surface`, no spinner and no splash
 *   motion (states.md §0; CLAUDE.md "no spinner is invented"). It also covers
 *   a locked token store, which keeps the state Unknown until a foreground
 *   retry succeeds.
 * - **SignedIn**: onboarding (relationship type). Its last action calls
 *   `onOnboardingFinished`, which lands on the placeholder until Today exists.
 * - **SignedOut**: the promise (Onboarding 1), leading to sign up or sign in.
 *   A sign-out or an ended session still lands on sign in (below).
 * - **UI preview** (debug builds with `-PmoyiUiPreview=true` only): a preview
 *   index, chosen before the session is known; see `UiPreviewIndex`.
 *
 * The start destination is chosen once, when the state first leaves Unknown.
 * Later changes are navigation: a sign-in navigates from the screen, and a
 * session end arrives as [SessionEvent.Ended], handled here.
 */
@Composable
fun App(viewModel: AppViewModel = koinViewModel()) {
    MoyiTheme {
        val session by viewModel.session.collectAsStateWithLifecycle()
        LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onForeground() }

        var start by remember { mutableStateOf<Any?>(if (AppBuildKonfig.UI_PREVIEW) UiPreviewRoute else null) }
        if (start == null && session != SessionState.Unknown) {
            // TODO(logic): skip onboarding when the person already has a bond (`GET /api/v1/bonds`).
            start = if (session is SessionState.SignedIn) OnboardingRoute else PromiseRoute
        }

        val startDestination = start
        if (startDestination == null) {
            Box(Modifier.fillMaxSize().background(MoyiTheme.colors.surface))
        } else {
            MoyiNavHost(startDestination = startDestination, viewModel = viewModel)
        }
    }
}

@Composable
private fun MoyiNavHost(startDestination: Any, viewModel: AppViewModel) {
    val navController = rememberNavController()

    // Composed after NavHost has its graph, so an event buffered during the
    // restore (a refresh token the server no longer accepts) navigates safely.
    ObserveAsEvents(viewModel.sessionEvents) { event ->
        when (event) {
            is SessionEvent.Ended -> navController.resetTo(
                // A sign-out the person asked for needs no explanation; the
                // server ending the session does (copy owed, states.md §1c).
                LoginRoute(sessionEnded = event.reason != SessionEndReason.SignedOut),
            )
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = Modifier.fillMaxSize().background(MoyiTheme.colors.surface),
        // Screens change with no transition: the motion budget is the reveal (SKILL.md §5).
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None },
    ) {
        authGraph(
            navController = navController,
            // TODO(logic): skip onboarding when the person already has a bond (`GET /api/v1/bonds`).
            onSignedIn = { navController.resetTo(OnboardingRoute) },
        )
        onboardingGraph(
            navController = navController,
            onNavigateToRegister = { navController.navigate(RegisterRoute) },
            onNavigateToSignIn = { navController.navigate(LoginRoute()) },
            // PR #8 swaps the placeholder for Today.
            onOnboardingFinished = { navController.resetTo(HomePlaceholderRoute) },
        )
        if (AppBuildKonfig.UI_PREVIEW) uiPreviewGraph(navController)
    }
}

/** Makes [route] the only screen: nothing to go back to. */
private fun NavHostController.resetTo(route: Any) {
    navigate(route) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}
