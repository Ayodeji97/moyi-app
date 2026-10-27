package com.moyi.app.feature.auth.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.moyi.app.feature.auth.home.HomePlaceholderRoot
import com.moyi.app.feature.auth.login.LoginRoot
import com.moyi.app.feature.auth.register.RegisterRoot
import com.moyi.app.feature.auth.verify.VerifyRoot
import kotlinx.serialization.Serializable

/** Sign in. [email] is prefilled after verifying; [sessionEnded] explains an involuntary sign-out. */
@Serializable
data class LoginRoute(val email: String = "", val sessionEnded: Boolean = false)

@Serializable
data object RegisterRoute

@Serializable
data class VerifyRoute(val email: String)

/** TEMPORARY: the signed-in placeholder, until Today exists. */
@Serializable
data object HomePlaceholderRoute

/**
 * The auth screens. Back stack, from the start destination (sign in):
 *
 * - Sign in → "Create an account" pushes Register.
 * - Register → back, or "I already have an account", pops to sign in.
 * - Register → 201 pushes Verify. Verify → back or "Use a different address"
 *   pops to Register, whose ViewModel kept the fields.
 * - Verify → 200 replaces the whole stack with sign in, email prefilled: the
 *   account is done, so back must not return to sign up.
 * - Sign in → success replaces the stack with the placeholder ([onSignedIn]).
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
            onNavigateBack = { navController.backToLogin() },
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
    composable<HomePlaceholderRoute> {
        HomePlaceholderRoot()
    }
}

/** Pops to sign in when it is underneath; otherwise makes it the only screen. */
private fun NavController.backToLogin() {
    if (previousBackStackEntry != null) {
        navigateUp()
    } else {
        navigate(LoginRoute()) { popUpTo(graph.id) { inclusive = true } }
    }
}
