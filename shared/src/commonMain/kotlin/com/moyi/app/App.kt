package com.moyi.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.moyi.app.designsystem.platform.screenEnterTransition
import com.moyi.app.designsystem.platform.screenExitTransition
import com.moyi.app.designsystem.platform.screenPopEnterTransition
import com.moyi.app.designsystem.platform.screenPopExitTransition
import coil3.compose.setSingletonImageLoaderFactory
import com.moyi.app.data.network.StorageHttpClient
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
import com.moyi.app.feature.bond.navigation.AccountGraph
import com.moyi.app.feature.bond.navigation.BondArchivedRoute
import com.moyi.app.feature.bond.navigation.BondSettingsGraph
import com.moyi.app.feature.bond.navigation.accountGraph
import com.moyi.app.feature.bond.navigation.bondArchivedDestination
import com.moyi.app.feature.bond.navigation.bondSettingsGraph
import com.moyi.app.feature.onboarding.navigation.OnboardingRoute
import com.moyi.app.feature.onboarding.navigation.PromiseRoute
import com.moyi.app.feature.onboarding.navigation.SignedInStartRoute
import com.moyi.app.feature.onboarding.navigation.UnverifiedGateRoute
import com.moyi.app.feature.onboarding.navigation.onboardingGraph
import com.moyi.app.feature.today.common.DailyLoop
import com.moyi.app.feature.today.common.DailyLoopState
import com.moyi.app.feature.today.navigation.RevealRoute
import com.moyi.app.feature.today.navigation.TodayRoute
import com.moyi.app.feature.today.navigation.todayGraph
import org.koin.compose.koinInject
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
 *
 * A tapped push ([PushTapInbox]) opens its screen once the signed-in app is showing:
 * on a cold start after the signed-in start has landed on Today, on a warm start at
 * once, and when signed out after signing in ([pushRouting]).
 */
@Composable
fun App(viewModel: AppViewModel = koinViewModel()) {
    val storage = koinInject<StorageHttpClient>()
    setSingletonImageLoaderFactory { context -> moyiImageLoader(context, storage.client) }
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
            MoyiNavHost(startDestination = startDestination, viewModel = viewModel, session = session)
        }
    }
}

@Composable
private fun MoyiNavHost(startDestination: Any, viewModel: AppViewModel, session: SessionState) {
    val navController = rememberNavController()
    val pushTaps = koinInject<PushTapInbox>()
    val pendingTap by pushTaps.target.collectAsStateWithLifecycle()
    val currentEntry by navController.currentBackStackEntryAsState()
    // The Archive and the Streak read the bond from the day Today loads, so a tap opens
    // nothing above Today until that day is in.
    val dayLoop = koinInject<DailyLoop>()
    val dayLoaded = dayLoop.state.collectAsStateWithLifecycle().value is DailyLoopState.Ready
    // Account needs no bond, so every signed-in place that has no Bond settings opens it too.
    val openAccount = { navController.navigate(AccountGraph) { launchSingleTop = true } }

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
            onOpenAccount = openAccount,
        )
        // Starting a new bond pushes onboarding over the archived screen, so back returns to it.
        bondArchivedDestination(onStartNewBond = { navController.navigate(OnboardingRoute) }, onOpenAccount = openAccount)
        // Account: from Bond settings, and from each screen a person with no open bond lands on.
        accountGraph(navController)
        if (UiPreview.onboarding) uiPreviewGraph(navController)
    }

    // After NavHost, so the graph is set. Runs again on every screen change and session
    // change while a tap waits, which is how a cold start or a sign-in reaches it.
    LaunchedEffect(pendingTap, currentEntry, session, dayLoaded) {
        val target = pendingTap ?: return@LaunchedEffect
        if (currentEntry == null) return@LaunchedEffect
        when (val routing = pushRouting(target, navController.shellPlace(session, dayLoaded))) {
            PushRouting.Wait -> Unit
            PushRouting.Drop -> pushTaps.consume(target)
            is PushRouting.Open -> {
                pushTaps.consume(target)
                navController.openAboveToday(routing.routes)
            }
        }
    }
}

/** Where the shell is, for a waiting push tap (see [ShellPlace]). */
private fun NavHostController.shellPlace(session: SessionState, dayLoaded: Boolean): ShellPlace {
    if (session !is SessionState.SignedIn || UiPreviewConfig.UI_PREVIEW.isNotEmpty()) return ShellPlace.Settling
    val destination = currentDestination ?: return ShellPlace.Settling
    if (destination.hasRoute<SignedInStartRoute>()) return ShellPlace.Settling
    if (runCatching { getBackStackEntry<TodayRoute>() }.isSuccess) {
        // Today still loading (a cold start) waits; a Today that finds no open bond sends
        // the person to the signed-in start, and from there the tap is dropped or kept.
        return if (dayLoaded) ShellPlace.SignedInApp else ShellPlace.Settling
    }
    val outside = destination.hierarchy.any { it.hasRoute<OnboardingRoute>() } ||
        destination.hasRoute<UnverifiedGateRoute>() ||
        destination.hasRoute<BondArchivedRoute>() ||
        // Account over one of those (over Today it was answered above).
        destination.hierarchy.any { it.hasRoute<AccountGraph>() }
    // Neither: a sign-in screen in the moment before it moves to the signed-in start.
    return if (outside) ShellPlace.OutsideTheApp else ShellPlace.Settling
}

/**
 * Back to Today, then [routes] over it. A reveal playing over Today is left to settle
 * when the target is Today itself: it is what the push was about.
 */
private fun NavHostController.openAboveToday(routes: List<Any>) {
    val here = currentDestination
    val atToday = here != null && (here.hasRoute<TodayRoute>() || here.hasRoute<RevealRoute>())
    if (routes.isEmpty() && atToday) return
    popBackStack<TodayRoute>(inclusive = false)
    routes.forEach { route -> navigate(route) { launchSingleTop = true } }
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
