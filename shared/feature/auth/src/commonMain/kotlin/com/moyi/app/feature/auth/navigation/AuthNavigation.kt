package com.moyi.app.feature.auth.navigation

import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.moyi.app.domain.auth.SessionEndReason
import com.moyi.app.feature.auth.legal.LegalRoot
import com.moyi.app.feature.auth.login.LoginRoot
import com.moyi.app.feature.auth.register.RegisterRoot
import com.moyi.app.feature.auth.reset.ChangePasswordRoot
import com.moyi.app.feature.auth.reset.ForgotPasswordRoot
import com.moyi.app.feature.auth.reset.ResetPasswordRoot
import com.moyi.app.feature.auth.sessionended.SessionEndedScreen
import com.moyi.app.feature.auth.verify.VerifyRoot
import kotlinx.serialization.Serializable

/**
 * Sign in. [email] is prefilled after verifying; [sessionEnded] explains an involuntary
 * sign-out; [verified] confirms the address was just verified, so the person knows the
 * link worked and that signing in is the last step; [passwordChanged] says the same after
 * a reset that saved the password without signing in.
 */
@Serializable
data class LoginRoute(
    val email: String = "",
    val sessionEnded: Boolean = false,
    val verified: Boolean = false,
    val passwordChanged: Boolean = false,
)

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
 * - [SessionEndReason.PasswordChanged]: sign in, saying the new password is saved. The
 *   password was changed here while signed in and signing in again with it failed.
 */
fun routeAfterSessionEnd(reason: SessionEndReason): Any = when (reason) {
    SessionEndReason.ReuseDetected -> SessionEndedRoute
    SessionEndReason.Invalid -> LoginRoute(sessionEnded = true)
    SessionEndReason.SignedOut -> LoginRoute()
    SessionEndReason.PasswordChanged -> LoginRoute(passwordChanged = true)
}

/**
 * Verify email. [email] is the address the link went to; empty when an emailed link opened
 * the app on a phone that does not know it. [linkToken] is that link's token (checked at
 * once), null when the person arrived from sign up. [signedIn]: the link opened over a
 * signed-in session (the unverified gate, or anywhere), so a confirmed address goes back to
 * the signed-in start rather than to sign in.
 */
@Serializable
data class VerifyRoute(val email: String, val linkToken: String? = null, val signedIn: Boolean = false)

/**
 * Terms and Privacy, pushed over the signed-in app. [prompt] is the re-consent step the
 * shell opens over Today when the documents changed; false is the read view from Bond
 * settings. Either way back pops it.
 */
@Serializable
data class LegalRoute(val prompt: Boolean = false)

/** Forgot password. [email] is prefilled from sign in's email field (possibly empty). */
@Serializable
data class ForgotPasswordRoute(val email: String = "")

/**
 * Choose a new password, opened straight from Forgot password on its code step. [email] is
 * the address the code was asked for: the code step names it, in a line that reads the same
 * whether or not it has an account, and the reset signs in with it after. Empty when an
 * emailed link opened the app without it. [linkToken] fills the paste
 * field when the emailed link opened the app. [signedIn]: opened from Change password, for
 * the person signed in here, who stays signed in (the save is `changePassword`).
 */
@Serializable
data class ResetPasswordRoute(val email: String, val linkToken: String? = null, val signedIn: Boolean = false)

/**
 * Change password, from Account while signed in (docs/missing-components.md §83): sends the
 * code to the signed-in address, then the reset's code step with [ResetPasswordRoute.signedIn].
 */
@Serializable
data object ChangePasswordRoute

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
 * - Sign in → success replaces the stack with the signed-in start ([onSignedIn]).
 * - Session ended → "Sign in" replaces the stack with sign in.
 * - Sign in → "I forgot my password" pushes Forgot password, prefilled with
 *   sign in's email. Forgot → 202 pushes Choose a new password on its code
 *   step (owner ruling 2026-11-16: no "Check your email" screen in between).
 *   Back pops one at a time: reset → forgot (which kept its field) → sign in.
 * - Choose a new password → saved and signed in: [onSignedIn], as sign in.
 *   Saved but the sign-in failed: the whole stack is replaced with sign in,
 *   email prefilled, as after verifying: the old password is gone, so back
 *   must not return into the reset.
 * - Account → Change password ([ChangePasswordRoute], push) → "Send the code" pushes the
 *   reset's code step, signed in. Back pops one at a time. Saved: both are popped, back to
 *   Account, and [onPasswordChanged] lets the shell say so. Saved but this phone could not
 *   sign in again: the session ends and the shell routes to sign in on that.
 */
fun NavGraphBuilder.authGraph(
    navController: NavController,
    onSignedIn: () -> Unit,
    onPasswordChanged: () -> Unit = {},
) {
    composable<LoginRoute> { entry ->
        val route = entry.toRoute<LoginRoute>()
        LoginRoot(
            initialEmail = route.email,
            sessionEnded = route.sessionEnded,
            verified = route.verified,
            passwordChanged = route.passwordChanged,
            onNavigateToHome = onSignedIn,
            onNavigateToRegister = { navController.navigate(RegisterRoute) },
            onNavigateToForgotPassword = { email -> navController.navigate(ForgotPasswordRoute(email)) },
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
            linkToken = route.linkToken,
            // Signed in already (a link opened over the session): the signed-in start reads
            // GET /me again and moves on from the gate.
            onNavigateToLogin = { email ->
                if (route.signedIn) onSignedIn() else navController.replaceStackWithLogin(email, verified = true)
            },
            onNavigateToSignIn = { navController.navigate(LoginRoute()) { launchSingleTop = true } },
            onNavigateBack = { navController.navigateUp() },
        )
    }
    composable<ForgotPasswordRoute> { entry ->
        val route = entry.toRoute<ForgotPasswordRoute>()
        ForgotPasswordRoot(
            initialEmail = route.email,
            onNavigateToCode = { email -> navController.navigate(ResetPasswordRoute(email)) },
            onNavigateBack = { navController.navigateUp() },
        )
    }
    composable<ResetPasswordRoute> { entry ->
        val route = entry.toRoute<ResetPasswordRoute>()
        ResetPasswordRoot(
            email = route.email,
            linkToken = route.linkToken,
            signedIn = route.signedIn,
            onNavigateToHome = onSignedIn,
            onNavigateToLogin = { email -> navController.replaceStackWithLogin(email, passwordChanged = true) },
            onNavigateBack = { navController.navigateUp() },
            onPasswordChanged = {
                navController.closePasswordChange()
                onPasswordChanged()
            },
        )
    }
    composable<ChangePasswordRoute> {
        ChangePasswordRoot(
            onNavigateToCode = { email -> navController.navigate(ResetPasswordRoute(email, signedIn = true)) },
            onNavigateBack = { navController.navigateUp() },
        )
    }
    composable<SessionEndedRoute> {
        SessionEndedScreen(onSignIn = { navController.signInAfterSessionEnded() })
    }
    composable<LegalRoute> { entry ->
        LegalRoot(prompt = entry.toRoute<LegalRoute>().prompt, onClose = { navController.popBackStack() })
    }
}

/** Back to the screen Change password was opened from (Account); just this screen if it was not on the stack. */
internal fun NavController.closePasswordChange() {
    if (!popBackStack<ChangePasswordRoute>(inclusive = true)) navigateUp()
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

/** Sign in, email prefilled, as the only screen: after verifying, or after a reset whose sign-in failed. */
internal fun NavController.replaceStackWithLogin(email: String, verified: Boolean = false, passwordChanged: Boolean = false) {
    navigate(LoginRoute(email = email, verified = verified, passwordChanged = passwordChanged)) {
        popUpTo(graph.id) { inclusive = true }
    }
}
