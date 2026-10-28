package com.moyi.app.feature.onboarding.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.toRoute
import com.moyi.app.feature.onboarding.common.RelationshipType
import com.moyi.app.feature.onboarding.createbond.CreateBondRoot
import com.moyi.app.feature.onboarding.invite.InviteHandoffRoot
import com.moyi.app.feature.onboarding.join.JoinCannotBeUsedRoot
import com.moyi.app.feature.onboarding.join.JoinCodeRoot
import com.moyi.app.feature.onboarding.join.JoinConfirmRoot
import com.moyi.app.feature.onboarding.promise.PromiseRoot
import com.moyi.app.feature.onboarding.relationship.RelationshipTypeRoot
import kotlinx.serialization.Serializable

/** Onboarding 1, the signed-out root. Outside [OnboardingRoute]: it comes before the account exists. */
@Serializable
data object PromiseRoute

/** The signed-in onboarding graph. Its start is relationship type. */
@Serializable
data object OnboardingRoute

@Serializable
data object RelationshipTypeRoute

/**
 * [typeName] is a [RelationshipType] by name. Navigation has no built-in
 * NavType for an enum on iOS (the app crashed building the graph), so the
 * route carries a String; [type] reads it back.
 */
@Serializable
data class CreateBondRoute(val typeName: String) {
    constructor(type: RelationshipType) : this(type.name)

    val type: RelationshipType get() = RelationshipType.valueOf(typeName)
}

@Serializable
data class InviteHandoffRoute(val code: String)

/** [prefilledCode] is what the `/i/{code}` lander stored; null is manual entry. */
@Serializable
data class JoinCodeRoute(val prefilledCode: String? = null)

@Serializable
data class JoinConfirmRoute(val code: String)

@Serializable
data object JoinCannotBeUsedRoute

/**
 * Onboarding. Cross-feature exits are lambdas; the app shell decides where
 * they go.
 *
 * Signed out:
 * - Promise → "Begin" → [onNavigateToRegister]; "I already have an account"
 *   → [onNavigateToSignIn]. Both push, so back returns to the promise.
 *
 * Signed in ([OnboardingRoute], creator path):
 * - Relationship type → a card pushes Create bond (no Continue). Back exists
 *   only when something is behind it.
 * - Create bond → "Create bond" replaces everything with the invite handoff:
 *   the bond exists, so neither back nor system back may return to the form
 *   (back would read as un-creating it, SKILL.md §9).
 * - Invite handoff → "Continue" → [onOnboardingFinished].
 *
 * Joiner path (also inside [OnboardingRoute]):
 * - Code → Continue pushes Confirm, or Cannot be used.
 * - Confirm → "Join" → [onOnboardingFinished]; "Not now" and back pop to the
 *   code, unspent. (Figma's "Not now" destination was not read; returning to
 *   the previous state is the spec's reading, §6.)
 * - Cannot be used → "Enter a different code" replaces it with manual entry.
 *
 * TODO(logic): the joiner path is entered when the lander handed the app a
 *  stored code after sign in (deep link, not built). Today the app reaches it
 *  only through the debug preview index.
 */
fun NavGraphBuilder.onboardingGraph(
    navController: NavController,
    onNavigateToRegister: () -> Unit,
    onNavigateToSignIn: () -> Unit,
    onOnboardingFinished: () -> Unit,
) {
    composable<PromiseRoute> {
        PromiseRoot(onBegin = onNavigateToRegister, onHaveAccount = onNavigateToSignIn)
    }
    navigation<OnboardingRoute>(startDestination = RelationshipTypeRoute) {
        composable<RelationshipTypeRoute> {
            RelationshipTypeRoot(
                onNavigateToCreateBond = { type -> navController.navigate(CreateBondRoute(type)) },
                onNavigateBack = navController.upOrNull(),
            )
        }
        composable<CreateBondRoute> { entry ->
            val route = entry.toRoute<CreateBondRoute>()
            CreateBondRoot(
                type = route.type,
                onNavigateToInvite = { code ->
                    navController.navigate(InviteHandoffRoute(code)) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                },
                onNavigateBack = { navController.navigateUp() },
            )
        }
        composable<InviteHandoffRoute> { entry ->
            InviteHandoffRoot(code = entry.toRoute<InviteHandoffRoute>().code, onFinish = onOnboardingFinished)
        }
        composable<JoinCodeRoute> { entry ->
            val route = entry.toRoute<JoinCodeRoute>()
            JoinCodeRoot(
                prefilledCode = route.prefilledCode,
                onNavigateToConfirm = { code -> navController.navigate(JoinConfirmRoute(code)) },
                onNavigateToCannotBeUsed = { navController.navigate(JoinCannotBeUsedRoute) },
                // A prefilled code is a root (deep link from a cold install), so it has no back.
                onNavigateBack = if (route.prefilledCode == null) navController.upOrNull() else null,
            )
        }
        composable<JoinConfirmRoute> { entry ->
            JoinConfirmRoot(
                code = entry.toRoute<JoinConfirmRoute>().code,
                onFinish = onOnboardingFinished,
                onNavigateBack = { navController.navigateUp() },
            )
        }
        composable<JoinCannotBeUsedRoute> {
            JoinCannotBeUsedRoot(
                onEnterDifferentCode = {
                    navController.navigate(JoinCodeRoute()) {
                        popUpTo<JoinCannotBeUsedRoute> { inclusive = true }
                    }
                },
                onNavigateBack = { navController.navigateUp() },
            )
        }
    }
}

/** navigateUp when there is a screen behind this one; null (no back drawn) when there is not. */
private fun NavController.upOrNull(): (() -> Unit)? =
    if (previousBackStackEntry != null) ({ navigateUp() }) else null
