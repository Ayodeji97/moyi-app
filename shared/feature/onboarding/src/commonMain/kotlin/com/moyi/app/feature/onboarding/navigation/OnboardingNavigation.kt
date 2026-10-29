package com.moyi.app.feature.onboarding.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.toRoute
import com.moyi.app.domain.bond.BondType
import com.moyi.app.domain.bond.InvitePreview
import com.moyi.app.feature.onboarding.common.RelationshipType
import com.moyi.app.feature.onboarding.gate.UnverifiedGateRoot
import com.moyi.app.feature.onboarding.start.SignedInStartRoot
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

/**
 * Where every signed-in session starts, at launch and after sign in: it asks
 * the server for the person's bonds and account, then replaces itself with
 * Today, onboarding or the unverified gate.
 */
@Serializable
data object SignedInStartRoute

/** Account 5 · unverified gate (Figma 345:46). A root: signed in, not verified, no bond. */
@Serializable
data object UnverifiedGateRoute

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
data class InviteHandoffRoute(val code: String, val link: String)

/** [prefilledCode] is what the `/i/{code}` lander stored; null is manual entry. */
@Serializable
data class JoinCodeRoute(val prefilledCode: String? = null)

/**
 * The preview travels with the code, so the code resolves once. Strings, not
 * an [InvitePreview]: like [CreateBondRoute], a route carries only types every
 * platform's Navigation can write ([bondTypeName] is a [BondType] by name).
 */
@Serializable
data class JoinConfirmRoute(
    val code: String,
    val inviterName: String,
    val bondName: String,
    val bondTypeName: String,
) {
    constructor(code: String, preview: InvitePreview) :
        this(code, preview.inviterName, preview.bondName, preview.bondType.name)

    val preview: InvitePreview get() = InvitePreview(inviterName, bondName, BondType.valueOf(bondTypeName))
}

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
 * Signed in, from [SignedInStartRoute], which replaces itself with one of:
 * - Today ([onOnboardingFinished]) when the person is in an open bond;
 * - the unverified gate when they are not verified and in none (FR-002); it
 *   replaces itself with relationship type once `GET /me` says verified;
 * - [OnboardingRoute] (relationship type, a root) otherwise.
 *
 * Creator path ([OnboardingRoute]):
 * - Relationship type → a card pushes Create bond (no Continue).
 * - Create bond → "Create bond" replaces everything with the invite handoff:
 *   the bond exists, so neither back nor system back may return to the form
 *   (back would read as un-creating it, SKILL.md §9).
 * - Invite handoff → "Continue" → [onOnboardingFinished].
 *
 * Joiner path (also inside [OnboardingRoute]):
 * - Relationship type → "I have an invite code" (not in Figma) pushes manual entry.
 * - Code → Continue resolves it, then pushes Confirm or Cannot be used.
 * - Confirm → "Join" → [onOnboardingFinished]; "Not now" and back pop to the
 *   code, unspent. A code that stopped working replaces Confirm with Cannot be used.
 * - Cannot be used → "Enter a different code" replaces it with manual entry.
 *
 * A `403 EMAIL_NOT_VERIFIED` from create or join replaces everything with the gate.
 *
 * Owed: a code handed over by the `/i/{code}` lander after sign in (deep
 * links, site association files). Until then the prefilled layout is reached
 * only through the debug preview index; pasting the link into manual entry works.
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
    composable<SignedInStartRoute> {
        SignedInStartRoot(
            onNavigateToToday = onOnboardingFinished,
            onNavigateToOnboarding = { navController.resetTo(OnboardingRoute) },
            onNavigateToGate = { navController.resetTo(UnverifiedGateRoute) },
        )
    }
    composable<UnverifiedGateRoute> {
        UnverifiedGateRoot(onNavigateToOnboarding = { navController.resetTo(OnboardingRoute) })
    }
    navigation<OnboardingRoute>(startDestination = RelationshipTypeRoute) {
        composable<RelationshipTypeRoute> {
            RelationshipTypeRoot(
                onNavigateToCreateBond = { type -> navController.navigate(CreateBondRoute(type)) },
                onNavigateToJoin = { navController.navigate(JoinCodeRoute()) },
                onNavigateBack = navController.upOrNull(),
            )
        }
        composable<CreateBondRoute> { entry ->
            val route = entry.toRoute<CreateBondRoute>()
            CreateBondRoot(
                type = route.type,
                onNavigateToInvite = { code, link -> navController.replaceWithInvite(code, link) },
                onNavigateToGate = { navController.resetTo(UnverifiedGateRoute) },
                onNavigateBack = { navController.navigateUp() },
            )
        }
        composable<InviteHandoffRoute> { entry ->
            val route = entry.toRoute<InviteHandoffRoute>()
            InviteHandoffRoot(code = route.code, link = route.link, onFinish = onOnboardingFinished)
        }
        composable<JoinCodeRoute> { entry ->
            val route = entry.toRoute<JoinCodeRoute>()
            JoinCodeRoot(
                prefilledCode = route.prefilledCode,
                onNavigateToConfirm = { code, preview -> navController.navigate(JoinConfirmRoute(code, preview)) },
                onNavigateToCannotBeUsed = { navController.navigate(JoinCannotBeUsedRoute) },
                // A prefilled code is a root (deep link from a cold install), so it has no back.
                onNavigateBack = if (route.prefilledCode == null) navController.upOrNull() else null,
            )
        }
        composable<JoinConfirmRoute> { entry ->
            val route = entry.toRoute<JoinConfirmRoute>()
            JoinConfirmRoot(
                code = route.code,
                preview = route.preview,
                onFinish = onOnboardingFinished,
                onNavigateToCannotBeUsed = {
                    navController.navigate(JoinCannotBeUsedRoute) {
                        popUpTo<JoinConfirmRoute> { inclusive = true }
                    }
                },
                onNavigateToGate = { navController.resetTo(UnverifiedGateRoute) },
                onNavigateBack = { navController.navigateUp() },
            )
        }
        composable<JoinCannotBeUsedRoute> {
            JoinCannotBeUsedRoot(
                onEnterDifferentCode = { navController.replaceWithManualEntry() },
                onNavigateBack = { navController.navigateUp() },
            )
        }
    }
}

/**
 * The bond exists once Create bond succeeds, so the invite handoff replaces
 * everything: neither back nor system back may return to the form.
 */
internal fun NavController.replaceWithInvite(code: String, link: String) {
    navigate(InviteHandoffRoute(code, link)) {
        popUpTo(graph.id) { inclusive = true }
    }
}

/**
 * "Enter a different code": a fresh manual entry replaces both the failed
 * screen and the code screen that led to it, so back never returns to the
 * rejected code.
 */
internal fun NavController.replaceWithManualEntry() {
    navigate(JoinCodeRoute()) {
        popUpTo<JoinCodeRoute> { inclusive = true }
    }
}

/** Makes [route] the only screen: a decision has been made that back must not undo. */
internal fun NavController.resetTo(route: Any) {
    navigate(route) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}

/** navigateUp when there is a screen behind this one; null (no back drawn) when there is not. */
private fun NavController.upOrNull(): (() -> Unit)? =
    if (previousBackStackEntry != null) ({ navigateUp() }) else null
