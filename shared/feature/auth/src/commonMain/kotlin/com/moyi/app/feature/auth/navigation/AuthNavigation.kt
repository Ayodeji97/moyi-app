package com.moyi.app.feature.auth.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.moyi.app.domain.auth.SessionEndReason
import com.moyi.app.feature.auth.home.HomePlaceholderRoot
import com.moyi.app.feature.auth.login.LoginRoot
import com.moyi.app.feature.auth.register.RegisterRoot
import com.moyi.app.feature.auth.sessionended.SessionEndedScreen
import com.moyi.app.feature.auth.verify.VerifyRoot
import kotlinx.serialization.Serializable

/** Sign in. [email] is prefilled after verifying; [sessionEnded] explains an involuntary sign-out. */
@Serializable
data class LoginRoute(val email: String = "", val sessionEnded: Boolean = false)

@Serializable
data object RegisterRoute

/** Why the person was signed out, after `TOKEN_REUSE_DETECTED`. A root: nothing to go back to. */
@Serializable
data object SessionEndedRoute

/**
 * Where the app goes when the session ends, by why it ended:
 *
 * - [SessionEndReason.ReuseDetected]: the session-ended screen, which says what happened.
 * - [SessionEndReason.Invalid]: sign in, with the neutral line. The token expired, was
 *   revoked by a password reset, or could not be kept; "used from two places" would be false.
 * - [SessionEndReason.SignedOut]: sign in, plain. The person asked for it.
 */
fun routeAfterSessionEnd(reason: SessionEndReason): Any = when (reason) {
    SessionEndReason.ReuseDetected -> SessionEndedRoute
    SessionEndReason.Invalid -> LoginRoute(sessionEnded = true)
    SessionEndReason.SignedOut -> LoginRoute()
}

@Serializable
data class VerifyRoute(val email: String)

/** TEMPORARY: the signed-in placeholder, until Today exists. */
@Serializable
data object HomePlaceholderRoute

/**
 * The auth screens. Back stack, from the start destination (sign in):
 *
 * - Sign in → "Create an account" pushes Register.
 * - Register → back pops to whatever is underneath (sign in, or the promise);
 *   "I already have an account" pops to sign in, or replaces sign up with it.
 * - Register → 201 pushes Verify. Verify → back or "Use a different address"
 *   pops to Register, whose ViewModel kept the fields.
 * - Verify → 200 replaces the whole stack with sign in, email prefilled: the
 *   account is done, so back must not return to sign up.
 * - Sign in → success replaces the stack with the placeholder ([onSignedIn]).
 * - Session ended → "Sign in" replaces the stack with sign in.
 */
fun NavGraphBuilder.authGraph(
    navController: NavController,
    onSignedIn: () -> Unit,
) {
    composable<LoginRoute> { entry ->
        val route = entry.toRoute<LoginRoute>()
        LoginRoot(
            initialEmail = route.email,
            sessionEnded = route.sessionEnded,
            onNavigateToHome = onSignedIn,
            onNavigateToRegister = { navController.navigate(RegisterRoute) },
            onBack = if (navController.previousBackStackEntry != null) ({ navController.navigateUp() }) else null,
        )
    }
    composable<RegisterRoute> {
        RegisterRoot(
            onNavigateToVerify = { email -> navController.navigate(VerifyRoute(email)) },
            onNavigateToLogin = { navController.backToLogin() },
            onNavigateBack = { navController.backFromRegister() },
        )
    }
    composable<VerifyRoute> { entry ->
        val route = entry.toRoute<VerifyRoute>()
        VerifyRoot(
            email = route.email,
            onNavigateToLogin = { email ->
                navController.navigate(LoginRoute(email = email)) {
                    popUpTo(navController.graph.id) { inclusive = true }
                }
            },
            onNavigateBack = { navController.navigateUp() },
        )
    }
    composable<SessionEndedRoute> {
        SessionEndedScreen(onSignIn = { navController.signInAfterSessionEnded() })
    }
    composable<HomePlaceholderRoute> {
        HomePlaceholderRoot()
    }
}

/** From the promise, back returns to it; from sign in, to sign in; alone, sign in replaces it. */
internal fun NavController.backFromRegister() {
    if (previousBackStackEntry != null) navigateUp() else backToLogin()
}

/**
 * Pops to sign in when it is directly underneath. Otherwise (sign up was
 * opened from the promise, or is the only screen) sign in replaces sign up,
 * so back from sign in returns to whatever was under sign up.
 */
internal fun NavController.backToLogin() {
    if (previousBackStackEntry?.destination?.hasRoute<LoginRoute>() == true) {
        navigateUp()
    } else {
        navigate(LoginRoute()) { popUpTo<RegisterRoute> { inclusive = true } }
    }
}

/** Sign in replaces the session-ended screen: back from sign in must not return to the notice. */
internal fun NavController.signInAfterSessionEnded() {
    navigate(LoginRoute()) { popUpTo(graph.id) { inclusive = true } }
}
