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
import com.moyi.app.feature.onboarding.join.LeavingBond
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

/** [bondId] and [bondName]: the bond just created, for "I have an invite code" there; null draws no link. */
@Serializable
data class InviteHandoffRoute(
    val code: String,
    val link: String,
    val bondId: String? = null,
    val bondName: String? = null,
)

/**
 * [prefilledCode] is what the `/i/{code}` lander stored; null is manual entry.
 *
 * [leavingBondId] and [leavingBondName]: the person's own bond that nobody has joined, when
 * the flow was opened from it ("I have an invite code" on Today, Bond settings or "Your bond is
 * ready"). Joining then ends it (owner ruling 2026-11-13). [leavingBondJustMade]: opened from
 * "Your bond is ready". Strings and a Boolean, for the same reason as [CreateBondRoute].
 *
 * [findEmptyBond]: opened by an invite link over Today ([fromLinkOverToday]), so the flow was
 * not opened from a bond and does not know yet whether the person is in one nobody has joined.
 * Continue looks for it (`GET /bonds`, by the same rule), and Confirm then says that Join ends it.
 */
@Serializable
data class JoinCodeRoute(
    val prefilledCode: String? = null,
    val leavingBondId: String? = null,
    val leavingBondName: String? = null,
    val leavingBondJustMade: Boolean = false,
    val findEmptyBond: Boolean = false,
) {
    val leaving: LeavingBond? get() = leavingBond(leavingBondId, leavingBondName, leavingBondJustMade)

    companion object {
        /** The join flow from [bondId], a bond nobody has joined: Join also ends it. */
        fun fromEmptyBond(bondId: String, bondName: String, justMade: Boolean = false) =
            JoinCodeRoute(leavingBondId = bondId, leavingBondName = bondName, leavingBondJustMade = justMade)

        /**
         * An invite link opened over Today: the prefilled code, and Continue looks for the
         * person's own bond nobody has joined, so a join from a link ends it as a typed code does.
         */
        fun fromLinkOverToday(code: String) = JoinCodeRoute(prefilledCode = code, findEmptyBond = true)
    }
}

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
    val leavingBondId: String? = null,
    val leavingBondName: String? = null,
    val leavingBondJustMade: Boolean = false,
) {
    constructor(code: String, preview: InvitePreview, leaving: LeavingBond? = null) :
        this(code, preview.inviterName, preview.bondName, preview.bondType.name, leaving?.id, leaving?.name, leaving?.justMade == true)

    val preview: InvitePreview get() = InvitePreview(inviterName, bondName, BondType.valueOf(bondTypeName))

    val leaving: LeavingBond? get() = leavingBond(leavingBondId, leavingBondName, leavingBondJustMade)
}

private fun leavingBond(id: String?, name: String?, justMade: Boolean): LeavingBond? =
    if (id != null && name != null) LeavingBond(id, name, justMade) else null

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
 * - the archived bond ([onBondArchived], in `:shared:feature:bond`) when they are in
 *   none and their newest bond has ended;
 * - the unverified gate when they are not verified and in none (FR-002); it
 *   replaces itself with relationship type once `GET /me` says verified;
 * - [OnboardingRoute] (relationship type, a root) otherwise.
 *
 * Creator path ([OnboardingRoute]):
 * - Relationship type → a card pushes Create bond (no Continue).
 * - Create bond → "Create bond" replaces everything with the invite handoff:
 *   the bond exists, so neither back nor system back may return to the form
 *   (back would read as un-creating it, SKILL.md §9).
 * - Invite handoff → "Continue" → [onOnboardingFinished]; "I have an invite code" pushes
 *   manual entry from the new bond ([JoinCodeRoute.fromEmptyBond]), so back returns to it.
 *
 * Joiner path (also inside [OnboardingRoute]):
 * - Relationship type → "I have an invite code" (not in Figma) pushes manual entry.
 * - Code → Continue resolves it, then pushes Confirm or Cannot be used.
 * - Confirm → "Join" → [onOnboardingFinished]; "Not now" and back pop to the
 *   code, unspent. A code that stopped working replaces Confirm with Cannot be used.
 * - Cannot be used → "Enter a different code" replaces it with manual entry.
 *
 * Joining from an empty bond ([JoinCodeRoute.leaving], from Today, Bond settings or the invite
 * handoff): the same screens. Code carries the bond to Confirm, which says that Join ends it,
 * and a fresh manual entry after Cannot be used keeps it.
 *
 * A `403 EMAIL_NOT_VERIFIED` from create or join replaces everything with the gate.
 *
 * An invite link (`https://getmoyi.app/i/{code}`) opens the prefilled code screen, put
 * there by the app shell (App.kt, LinkRouting) once the person is signed in, verified and
 * outside an open bond's Today, or over Today or the archived bond. Over Today it looks for
 * the person's own bond nobody has joined ([JoinCodeRoute.fromLinkOverToday]), so joining from
 * a link ends it exactly as joining from "I have an invite code" does.
 * Account ([onOpenAccount], in `:shared:feature:bond`): the gate and relationship type as a
 * root carry the account target (a person glyph, top right) that pushes it, so back returns. A person on either is
 * in no open bond and cannot reach Bond settings (owner ruling 2026-11-09).
 * Verify ([onOpenVerify], in `:shared:feature:auth`): the gate's "Type the code" pushes it for
 * the session's address (PROPOSED, docs/missing-components.md §63).
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
    onBondArchived: (partnerName: String?) -> Unit,
    onSignedInStartError: () -> Unit = {},
    onOpenSupport: () -> Unit,
    onOpenAccount: () -> Unit,
    onOpenVerify: (email: String) -> Unit,
) {
    composable<PromiseRoute> {
        PromiseRoot(onBegin = onNavigateToRegister, onHaveAccount = onNavigateToSignIn)
    }
    composable<SignedInStartRoute> {
        SignedInStartRoot(
            onNavigateToToday = onOnboardingFinished,
            onNavigateToOnboarding = { navController.resetTo(OnboardingRoute) },
            onNavigateToGate = { navController.resetTo(UnverifiedGateRoute) },
            onNavigateToArchived = onBondArchived,
            onShowingError = onSignedInStartError,
            onNavigateToSupport = onOpenSupport,
        )
    }
    composable<UnverifiedGateRoute> {
        UnverifiedGateRoot(
            onNavigateToOnboarding = { navController.resetTo(OnboardingRoute) },
            onNavigateToVerify = onOpenVerify,
            onNavigateToAccount = onOpenAccount,
        )
    }
    navigation<OnboardingRoute>(startDestination = RelationshipTypeRoute) {
        composable<RelationshipTypeRoute> {
            RelationshipTypeRoot(
                onNavigateToCreateBond = { type -> navController.navigate(CreateBondRoute(type)) },
                onNavigateToJoin = { navController.navigate(JoinCodeRoute()) },
                onNavigateToAccount = onOpenAccount,
                onNavigateBack = navController.upOrNull(),
            )
        }
        composable<CreateBondRoute> { entry ->
            val route = entry.toRoute<CreateBondRoute>()
            CreateBondRoot(
                type = route.type,
                onNavigateToInvite = { code, link, bondId, bondName -> navController.replaceWithInvite(code, link, bondId, bondName) },
                onNavigateToGate = { navController.resetTo(UnverifiedGateRoute) },
                onNavigateBack = { navController.navigateUp() },
            )
        }
        composable<InviteHandoffRoute> { entry ->
            val route = entry.toRoute<InviteHandoffRoute>()
            val bondId = route.bondId
            val bondName = route.bondName
            InviteHandoffRoot(
                code = route.code,
                link = route.link,
                onFinish = onOnboardingFinished,
                onNavigateToJoin = if (bondId != null && bondName != null) {
                    { navController.navigate(JoinCodeRoute.fromEmptyBond(bondId, bondName, justMade = true)) }
                } else {
                    null
                },
            )
        }
        composable<JoinCodeRoute> { entry ->
            val route = entry.toRoute<JoinCodeRoute>()
            JoinCodeRoot(
                prefilledCode = route.prefilledCode,
                // The bond the flow was opened from, or else the one Continue found (a link over Today).
                onNavigateToConfirm = { code, preview, found ->
                    navController.navigate(JoinConfirmRoute(code, preview, route.leaving ?: found))
                },
                onNavigateToCannotBeUsed = { navController.navigate(JoinCannotBeUsedRoute) },
                // From a link into onboarding the code screen is the root (states.md §9, "Join · code
                // from link": no prior screen), so it has no back. Over Today or the archived bond
                // (App.kt, LinkRouting) back returns there.
                onNavigateBack = navController.upOrNull(),
                findEmptyBond = route.findEmptyBond,
            )
        }
        composable<JoinConfirmRoute> { entry ->
            val route = entry.toRoute<JoinConfirmRoute>()
            JoinConfirmRoot(
                code = route.code,
                preview = route.preview,
                leaving = route.leaving,
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
internal fun NavController.replaceWithInvite(code: String, link: String, bondId: String? = null, bondName: String? = null) {
    navigate(InviteHandoffRoute(code, link, bondId, bondName)) {
        popUpTo(graph.id) { inclusive = true }
    }
}

/**
 * "Enter a different code": a fresh manual entry replaces both the failed
 * screen and the code screen that led to it, so back never returns to the
 * rejected code. The bond the flow was opened from, if any, is kept: Join still ends it. So is
 * a link's look for one ([JoinCodeRoute.findEmptyBond]).
 */
internal fun NavController.replaceWithManualEntry() {
    val opened = runCatching { getBackStackEntry<JoinCodeRoute>().toRoute<JoinCodeRoute>() }.getOrNull()
    val fresh = JoinCodeRoute(
        leavingBondId = opened?.leavingBondId,
        leavingBondName = opened?.leavingBondName,
        leavingBondJustMade = opened?.leavingBondJustMade == true,
        findEmptyBond = opened?.findEmptyBond == true,
    )
    navigate(fresh) {
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
