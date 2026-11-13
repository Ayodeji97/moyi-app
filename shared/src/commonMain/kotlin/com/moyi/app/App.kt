package com.moyi.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.moyi.app.designsystem.platform.screenEnterTransition
import com.moyi.app.designsystem.platform.screenExitTransition
import com.moyi.app.designsystem.platform.screenPopEnterTransition
import com.moyi.app.designsystem.platform.screenPopExitTransition
import coil3.compose.setSingletonImageLoaderFactory
import com.moyi.app.data.network.StorageHttpClient
import com.moyi.app.designsystem.MoyiTheme
import com.moyi.app.designsystem.component.MoyiSnackbar
import com.moyi.app.designsystem.presentation.ObserveAsEvents
import com.moyi.app.domain.auth.SessionEvent
import com.moyi.app.domain.auth.SessionState
import com.moyi.app.debug.UiPreviewRoute
import com.moyi.app.debug.uiPreviewGraph
import com.moyi.app.feature.auth.navigation.LegalRoute
import com.moyi.app.feature.auth.navigation.alreadyVerifiedNotice
import com.moyi.app.feature.auth.navigation.LoginRoute
import com.moyi.app.feature.auth.navigation.routeAfterSessionEnd
import com.moyi.app.feature.auth.navigation.RegisterRoute
import com.moyi.app.feature.auth.navigation.ResetLinkSentRoute
import com.moyi.app.feature.auth.navigation.ResetPasswordRoute
import com.moyi.app.feature.auth.navigation.VerifyRoute
import com.moyi.app.feature.auth.navigation.authGraph
import com.moyi.app.feature.bond.navigation.AccountGraph
import com.moyi.app.feature.bond.navigation.BondArchivedRoute
import com.moyi.app.feature.bond.navigation.BondSettingsGraph
import com.moyi.app.feature.bond.navigation.accountGraph
import com.moyi.app.feature.bond.navigation.bondArchivedDestination
import com.moyi.app.feature.bond.navigation.bondSettingsGraph
import com.moyi.app.feature.onboarding.navigation.InviteHandoffRoute
import com.moyi.app.feature.onboarding.navigation.JoinCodeRoute
import com.moyi.app.feature.bond.navigation.openReport
import com.moyi.app.feature.bond.navigation.reportGraph
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
import com.moyi.app.splash.LaunchSplash
import com.moyi.app.splash.StartScreen
import com.moyi.app.splash.firstScreenDrawn
import com.moyi.app.feature.support.SupportOrigin
import com.moyi.app.feature.support.navigation.SupportRoute
import com.moyi.app.feature.support.navigation.supportDestination
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * The app: the session decides the first screen.
 *
 * - **Unknown** (restoring): the launch splash ([LaunchSplash], owner request
 *   2026-11-09), which builds the logo over `surface` and then holds it, never a
 *   spinner (CLAUDE.md). It also covers a locked token store, which keeps the
 *   state Unknown until a foreground retry succeeds. Once the state is known the
 *   first screen composes under the splash, and the splash waits until that screen
 *   has drawn something ([firstScreenDrawn]: never a bare `surface`, so a signed-in
 *   start goes from the logo straight to Today), then leaves by its hand-off rule
 *   (`SplashTimeline.handOff`): signed out, when it has played (1.1 s); signed in,
 *   at most 400 ms later. Then a 200 ms cross-fade.
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
 *
 * A link to getmoyi.app that opened the app ([LinkInbox]) opens its screen the same way:
 * a verify or reset link as soon as there is a screen ([accountLinkRouting]), an invite link
 * once someone is signed in, verified and out of the signed-in start ([inviteRouting]).
 * The re-consent step (Terms and Privacy, [LegalRoute] with `prompt`) opens over Today
 * when [AppViewModel.legalConsentDue] says the person's consent is older than the
 * documents in force. Only over Today with its day in: never over the composer, a
 * reveal or a settings screen, and never before the cached day has shown.
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

        // Shown once per launch, from the first frame: a UI preview starts with its screen
        // chosen and skips it, and saved state keeps a rotation from playing it again.
        var splashDone by rememberSaveable { mutableStateOf(start != null) }
        // Latched: once the first screen has drawn something, later screens change nothing.
        var firstScreenDrawn by remember { mutableStateOf(false) }
        // A verify link that opened nothing because the address is already confirmed (§62).
        var alreadyVerified by remember { mutableStateOf(false) }

        val startDestination = start
        Box(Modifier.fillMaxSize()) {
            if (startDestination == null) {
                Box(Modifier.fillMaxSize().background(MoyiTheme.colors.surface))
            } else {
                // Composed under the splash at once: the first screen loads while it plays.
                MoyiNavHost(
                    startDestination = startDestination,
                    viewModel = viewModel,
                    session = session,
                    onFirstScreenDrawn = { firstScreenDrawn = true },
                    onAlreadyVerified = { alreadyVerified = true },
                )
            }
            if (!splashDone) {
                LaunchSplash(
                    ready = firstScreenDrawn,
                    signedIn = session is SessionState.SignedIn,
                    onFinished = { splashDone = true },
                )
            } else {
                // Over every screen, and only once the splash has gone, so its time is all seen.
                MoyiSnackbar(
                    message = if (alreadyVerified) alreadyVerifiedNotice() else null,
                    onDismiss = { alreadyVerified = false },
                )
            }
        }
    }
}

@Composable
private fun MoyiNavHost(
    startDestination: Any,
    viewModel: AppViewModel,
    session: SessionState,
    onFirstScreenDrawn: () -> Unit,
    /** A verify link opened nothing because the address is already confirmed: say so. */
    onAlreadyVerified: () -> Unit,
) {
    val navController = rememberNavController()
    val pushTaps = koinInject<PushTapInbox>()
    val pendingTap by pushTaps.target.collectAsStateWithLifecycle()
    val links = koinInject<LinkInbox>()
    val pendingAccountLink by links.accountLink.collectAsStateWithLifecycle()
    val pendingInvite by links.inviteLink.collectAsStateWithLifecycle()
    val currentEntry by navController.currentBackStackEntryAsState()
    // The Archive and the Streak read the bond from the day Today loads, so a tap opens
    // nothing above Today until that day is in.
    val dayLoop = koinInject<DailyLoop>()
    val dayState by dayLoop.state.collectAsStateWithLifecycle()
    val dayLoaded = dayState is DailyLoopState.Ready
    var signedInStartFailed by remember { mutableStateOf(false) }

    val legalConsentDue by viewModel.legalConsentDue.collectAsStateWithLifecycle()
    val todayShowing = session is SessionState.SignedIn && dayLoaded && UiPreviewConfig.UI_PREVIEW.isEmpty() &&
        currentEntry?.destination?.hasRoute<TodayRoute>() == true
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
            // The Archive day view's Report (FR-100): the report flow, over the day.
            onReport = { bondId, entryId, partnerName -> navController.openReport(bondId, entryId, partnerName) },
            // Left or ended on the other phone: the signed-in start asks the server again.
            onNoOpenBond = { navController.resetTo(SignedInStartRoute) },
            // "I have an invite code" while nobody has joined: the join flow over Today, from that bond.
            onJoinWithCode = { bondId, bondName -> navController.navigate(JoinCodeRoute.fromEmptyBond(bondId, bondName)) },
            debugTools = UiPreview.today,
        )
        bondSettingsGraph(
            navController = navController,
            // Left, blocked, or found ended: the same path as Today finding no open bond.
            onBondEnded = { navController.resetTo(SignedInStartRoute) },
            // The same join flow, over Bond settings, so back returns there.
            onJoinWithCode = { bondId, bondName -> navController.navigate(JoinCodeRoute.fromEmptyBond(bondId, bondName)) },
        )
        reportGraph(navController)
        onboardingGraph(
            navController = navController,
            onNavigateToRegister = { navController.navigate(RegisterRoute) },
            onNavigateToSignIn = { navController.navigate(LoginRoute()) },
            onOnboardingFinished = { navController.resetTo(TodayRoute) },
            // The bond has ended: the archived screen, a root with no back (SKILL.md).
            onBondArchived = { partnerName -> navController.resetTo(BondArchivedRoute(partnerName)) },
            onSignedInStartError = { signedInStartFailed = true },
            // The Generic error's Get help: Support over it, so its way back returns to Try again.
            onOpenSupport = { navController.navigate(SupportRoute(SupportOrigin.Error)) { launchSingleTop = true } },
            onOpenAccount = openAccount,
            // The gate's "Type the code": the verify screen over the gate, signed in, so a
            // confirmed address returns to the signed-in start and back returns to the gate.
            onOpenVerify = { email -> navController.navigate(VerifyRoute(email = email, signedIn = true)) { launchSingleTop = true } },
        )
        // Get help (FR-102): pushed over whichever screen opened it, and popped to return there.
        supportDestination(onClose = { navController.popBackStack() })
        // Starting a new bond pushes onboarding over the archived screen, so back returns to it.
        // Its Export your archive and account target push over it too.
        bondArchivedDestination(navController, onStartNewBond = { navController.navigate(OnboardingRoute) }, onOpenAccount = openAccount)
        // Account: from Bond settings, and from each screen a person with no open bond lands on.
        // Its Get help and Terms and Privacy open the same screens Bond settings does, over it.
        accountGraph(
            navController = navController,
            onOpenLegal = { navController.navigate(LegalRoute()) { launchSingleTop = true } },
            onOpenSupport = { navController.navigate(SupportRoute(SupportOrigin.Account)) { launchSingleTop = true } },
        )
        if (UiPreview.onboarding) uiPreviewGraph(navController)
    }

    // The launch splash hands over only once the screen under it has drawn something.
    val drawn = firstScreenDrawn(currentEntry?.destination.startScreen(), dayState, signedInStartFailed)
    LaunchedEffect(drawn) { if (drawn) onFirstScreenDrawn() }
    LaunchedEffect(todayShowing) { viewModel.onTodayShowing(todayShowing) }
    LaunchedEffect(legalConsentDue, todayShowing) {
        if (legalConsentDue && todayShowing) {
            viewModel.onLegalConsentShown()
            navController.navigate(LegalRoute(prompt = true)) { launchSingleTop = true }
        }
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

    // The same for an opened link. Nothing here logs the link: it may carry a token.
    LaunchedEffect(pendingAccountLink, currentEntry, session, dayLoaded) {
        val link = pendingAccountLink ?: return@LaunchedEffect
        if (currentEntry == null) return@LaunchedEffect
        val user = (session as? SessionState.SignedIn)?.user
        // The session's emailVerified is as old as the sign-in: true is final, false may be stale
        // (verified at the gate since), so a verified-only screen under this one counts too.
        val routing = accountLinkRouting(
            link,
            navController.linkPlace(session, dayLoaded),
            navController.currentAccountRoute(),
            signedInEmail = user?.email,
            signedInVerified = user?.emailVerified == true || navController.verifiedOnlyScreenOnBackStack(),
        )
        if (routing != LinkRouting.Wait) {
            links.consume(link)
            if (routing == LinkRouting.AlreadyVerified) onAlreadyVerified() else navController.follow(routing)
        }
    }
    LaunchedEffect(pendingInvite, currentEntry, session, dayLoaded) {
        val link = pendingInvite ?: return@LaunchedEffect
        if (currentEntry == null) return@LaunchedEffect
        val routing = inviteRouting(link, navController.linkPlace(session, dayLoaded))
        if (routing != LinkRouting.Wait) {
            links.consume(link)
            navController.follow(routing)
        }
    }
}

/** Where the shell is, for a waiting link (see [LinkPlace]). */
private fun NavHostController.linkPlace(session: SessionState, dayLoaded: Boolean): LinkPlace {
    if (UiPreviewConfig.UI_PREVIEW.isNotEmpty() || session == SessionState.Unknown) return LinkPlace.Settling
    val destination = currentDestination ?: return LinkPlace.Settling
    if (session !is SessionState.SignedIn) return LinkPlace.SignedOut
    return when {
        destination.hasRoute<SignedInStartRoute>() -> LinkPlace.Settling
        runCatching { getBackStackEntry<TodayRoute>() }.isSuccess ->
            if (dayLoaded) LinkPlace.SignedInApp else LinkPlace.Settling
        // The bond was just created and its own invite is on screen: an opened invite waits
        // for Continue (Today) rather than replace the code the person is about to share.
        destination.hasRoute<InviteHandoffRoute>() -> LinkPlace.Elsewhere
        destination.hierarchy.any { it.hasRoute<OnboardingRoute>() } -> LinkPlace.Onboarding
        destination.hasRoute<UnverifiedGateRoute>() -> LinkPlace.Gate
        destination.hasRoute<BondArchivedRoute>() -> LinkPlace.Archived
        else -> LinkPlace.Elsewhere
    }
}

/**
 * Whether a screen only a verified person reaches (onboarding, Today, the archived bond) is on
 * the back stack, e.g. under Account opened from relationship type ([accountLinkRouting]).
 */
private fun NavHostController.verifiedOnlyScreenOnBackStack(): Boolean =
    runCatching { getBackStackEntry<OnboardingRoute>() }.isSuccess ||
        runCatching { getBackStackEntry<TodayRoute>() }.isSuccess ||
        runCatching { getBackStackEntry<BondArchivedRoute>() }.isSuccess

/** The route on screen when a verify or reset link continues it ([accountLinkRouting]); else null. */
private fun NavHostController.currentAccountRoute(): Any? {
    val entry = currentBackStackEntry ?: return null
    val destination = entry.destination
    return when {
        destination.hasRoute<VerifyRoute>() -> entry.toRoute<VerifyRoute>()
        destination.hasRoute<ResetLinkSentRoute>() -> entry.toRoute<ResetLinkSentRoute>()
        destination.hasRoute<ResetPasswordRoute>() -> entry.toRoute<ResetPasswordRoute>()
        else -> null
    }
}

private fun NavHostController.follow(routing: LinkRouting) {
    when (routing) {
        LinkRouting.Wait -> Unit
        is LinkRouting.Push -> {
            val current = currentDestination
            navigate(routing.route) {
                if (routing.replaceCurrent && current != null) popUpTo(current.id) { inclusive = true }
            }
        }
        LinkRouting.AlreadyVerified -> Unit
        is LinkRouting.Reset -> resetTo(routing.route)
        is LinkRouting.AboveToday -> {
            popBackStack<TodayRoute>(inclusive = false)
            navigate(routing.route) { launchSingleTop = true }
        }
    }
}

/** Which kind of screen [this] is, for the launch splash ([firstScreenDrawn]). */
private fun NavDestination?.startScreen(): StartScreen = when {
    this == null -> StartScreen.None
    hasRoute<SignedInStartRoute>() -> StartScreen.SignedInStart
    hasRoute<TodayRoute>() -> StartScreen.Today
    else -> StartScreen.Other
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
