package com.moyi.app

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
import com.moyi.app.designsystem.platform.screenEnterTransition
import com.moyi.app.designsystem.platform.screenExitTransition
import com.moyi.app.designsystem.platform.screenPopEnterTransition
import com.moyi.app.designsystem.platform.screenPopExitTransition
import com.moyi.app.designsystem.MoyiTheme
import com.moyi.app.designsystem.presentation.ObserveAsEvents
import com.moyi.app.domain.auth.SessionEvent
import com.moyi.app.domain.auth.SessionState
import com.moyi.app.debug.UiPreviewRoute
import com.moyi.app.debug.uiPreviewGraph
import com.moyi.app.feature.auth.navigation.LoginRoute
import com.moyi.app.feature.auth.navigation.routeAfterSessionEnd
import com.moyi.app.feature.auth.navigation.RegisterRoute
import com.moyi.app.feature.auth.navigation.authGraph
import com.moyi.app.feature.bond.navigation.BondArchivedRoute
import com.moyi.app.feature.bond.navigation.BondSettingsGraph
import com.moyi.app.feature.bond.navigation.bondArchivedDestination
import com.moyi.app.feature.bond.navigation.bondSettingsGraph
import com.moyi.app.feature.onboarding.navigation.OnboardingRoute
import com.moyi.app.feature.onboarding.navigation.PromiseRoute
import com.moyi.app.feature.onboarding.navigation.SignedInStartRoute
import com.moyi.app.feature.onboarding.navigation.onboardingGraph
import com.moyi.app.feature.today.navigation.TodayRoute
import com.moyi.app.feature.today.navigation.todayGraph
import org.koin.compose.viewmodel.koinViewModel

/**
 * The app: the session decides the first screen.
 *
 * - **Unknown** (restoring): a blank `surface`, no spinner and no splash
 *   motion (states.md §0; CLAUDE.md "no spinner is invented"). It also covers
 *   a locked token store, which keeps the state Unknown until a foreground
 *   retry succeeds.
 * - **SignedIn**: the signed-in start, which asks the server where this
 *   person is (`GET /bonds`, `GET /me`) and replaces itself with Today, the
 *   archived bond, the unverified gate or onboarding. Onboarding's last action calls
 *   `onOnboardingFinished`, which lands on Today, the root of the signed-in app.
 * - **SignedOut**: the promise (Onboarding 1), leading to sign up or sign in.
 *   A sign-out or an ended session still lands on sign in (below).
 * - **UI preview** (debug builds only), chosen before the session is known:
 *   `-PmoyiUiPreview=onboarding` opens a preview index (see `UiPreviewIndex`);
 *   `-PmoyiUiPreview=today` opens Today on a preview day (PreviewDailyLoop), no backend;
 *   its calendar target opens the Archive on thirty preview days (PreviewArchive.kt).
 *   its settings target opens Bond settings on preview settings (PreviewBondSettings).
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

        var start by remember {
            mutableStateOf<Any?>(
                when {
                    UiPreview.onboarding -> UiPreviewRoute
                    UiPreview.today -> TodayRoute
                    else -> null
                },
            )
        }
        if (start == null && session != SessionState.Unknown) {
            start = if (session is SessionState.SignedIn) SignedInStartRoute else PromiseRoute
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
            // Each reason gets the screen whose words are true for it (routeAfterSessionEnd).
            is SessionEvent.Ended -> navController.resetTo(routeAfterSessionEnd(event.reason))
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = Modifier.fillMaxSize().background(MoyiTheme.colors.surface),
        // Screens move as each platform's own do (owner ruling 2026-11-08, MoyiMotion):
        // Android's shared axis, the iOS push. A destination may still set its own.
        enterTransition = { screenEnterTransition() },
        exitTransition = { screenExitTransition() },
        popEnterTransition = { screenPopEnterTransition() },
        popExitTransition = { screenPopExitTransition() },
    ) {
        authGraph(
            navController = navController,
            onSignedIn = { navController.resetTo(SignedInStartRoute) },
        )
        todayGraph(
            navController = navController,
            onOpenSettings = { bondId -> navController.navigate(BondSettingsGraph(bondId)) { launchSingleTop = true } },
            // Left or ended on the other phone: the signed-in start asks the server again.
            onNoOpenBond = { navController.resetTo(SignedInStartRoute) },
            debugTools = UiPreview.today,
        )
        bondSettingsGraph(
            navController = navController,
            // Left, blocked, or found ended: the same path as Today finding no open bond.
            onBondEnded = { navController.resetTo(SignedInStartRoute) },
        )
        onboardingGraph(
            navController = navController,
            onNavigateToRegister = { navController.navigate(RegisterRoute) },
            onNavigateToSignIn = { navController.navigate(LoginRoute()) },
            onOnboardingFinished = { navController.resetTo(TodayRoute) },
            // The bond has ended: the archived screen, a root with no back (SKILL.md).
            onBondArchived = { partnerName -> navController.resetTo(BondArchivedRoute(partnerName)) },
        )
        // Starting a new bond pushes onboarding over the archived screen, so back returns to it.
        bondArchivedDestination(onStartNewBond = { navController.navigate(OnboardingRoute) })
        if (UiPreview.onboarding) uiPreviewGraph(navController)
    }
}

/** Makes [route] the only screen: nothing to go back to. */
private fun NavHostController.resetTo(route: Any) {
    navigate(route) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}

/** The `moyiUiPreview` build switch (shared/build.gradle.kts). Empty in every normal build. */
private object UiPreview {
    val onboarding: Boolean = UiPreviewConfig.UI_PREVIEW == "onboarding"
    val today: Boolean = UiPreviewConfig.UI_PREVIEW == "today"
}
