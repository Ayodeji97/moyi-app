package com.moyi.app.feature.onboarding.navigation

import androidx.navigation.NavHostController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.createGraph
import androidx.navigation.toRoute
import com.moyi.app.domain.bond.BondType
import com.moyi.app.domain.bond.InvitePreview
import com.moyi.app.feature.onboarding.common.RelationshipType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Builds the real onboarding graph and navigates to every route with an
 * argument. The first run on the iPhone simulator crashed while building the
 * graph: an enum route argument has no NavType on iOS. This catches that
 * class of failure without a device. iOS only, because Android's
 * NavHostController needs a Context that host tests do not have.
 */
class OnboardingGraphTest {
    private fun controller(): NavHostController = NavHostController().apply {
        navigatorProvider.addNavigator(ComposeNavigator())
        graph = createGraph(startDestination = PromiseRoute) {
            onboardingGraph(this@apply, onNavigateToRegister = {}, onNavigateToSignIn = {}, onOnboardingFinished = {})
        }
    }

    @Test
    fun everyRouteArgumentSurvivesNavigation() {
        val nav = controller()
        for (type in RelationshipType.entries) {
            nav.navigate(CreateBondRoute(type))
            assertEquals(type, nav.currentBackStackEntry!!.toRoute<CreateBondRoute>().type)
        }
        nav.navigate(InviteHandoffRoute("K7Q2PD", "https://getmoyi.app/i/K7Q2PD"))
        val invite = nav.currentBackStackEntry!!.toRoute<InviteHandoffRoute>()
        assertEquals("K7Q2PD", invite.code)
        assertEquals("https://getmoyi.app/i/K7Q2PD", invite.link)
        nav.navigate(JoinCodeRoute(prefilledCode = "K7Q2PD"))
        assertEquals("K7Q2PD", nav.currentBackStackEntry!!.toRoute<JoinCodeRoute>().prefilledCode)
        nav.navigate(JoinCodeRoute())
        assertEquals(null, nav.currentBackStackEntry!!.toRoute<JoinCodeRoute>().prefilledCode)
        for (type in BondType.entries) {
            val preview = InvitePreview(inviterName = "Amara", bondName = "Amara and Tunde", bondType = type)
            nav.navigate(JoinConfirmRoute("K7Q2PD", preview))
            val confirm = nav.currentBackStackEntry!!.toRoute<JoinConfirmRoute>()
            assertEquals("K7Q2PD", confirm.code)
            assertEquals(preview, confirm.preview)
        }
    }

    @Test
    fun theSignedInStartAndTheGateAreRoutes() {
        val nav = controller()
        nav.navigate(SignedInStartRoute)
        assertEquals(SignedInStartRoute, nav.currentBackStackEntry!!.toRoute<SignedInStartRoute>())
        nav.resetTo(UnverifiedGateRoute)
        assertEquals(UnverifiedGateRoute, nav.currentBackStackEntry!!.toRoute<UnverifiedGateRoute>())
        assertNull(nav.previousBackStackEntry, "the gate is a root")
        nav.resetTo(OnboardingRoute)
        assertEquals(RelationshipTypeRoute, nav.currentBackStackEntry!!.toRoute<RelationshipTypeRoute>())
        assertNull(nav.previousBackStackEntry, "relationship type is a root after the start")
    }

    @Test
    fun theSignedInGraphStartsAtRelationshipType() {
        val nav = controller()
        nav.navigate(OnboardingRoute)
        assertEquals(RelationshipTypeRoute, nav.currentBackStackEntry!!.toRoute<RelationshipTypeRoute>())
    }

    @Test
    fun theInviteHandoffCannotBeReturnedFrom() {
        val nav = controller()
        nav.navigate(OnboardingRoute)
        nav.navigate(CreateBondRoute(RelationshipType.entries.first()))

        nav.replaceWithInvite("K7Q2PD", "https://getmoyi.app/i/K7Q2PD")

        assertEquals("K7Q2PD", nav.currentBackStackEntry!!.toRoute<InviteHandoffRoute>().code)
        assertNull(nav.previousBackStackEntry, "the bond exists; back must not return to the form")
    }

    @Test
    fun enteringADifferentCodeLeavesNoRejectedCodeBehind() {
        val nav = controller()
        nav.navigate(OnboardingRoute)
        nav.navigate(JoinCodeRoute())
        nav.navigate(JoinCannotBeUsedRoute)

        nav.replaceWithManualEntry()

        assertEquals(null, nav.currentBackStackEntry!!.toRoute<JoinCodeRoute>().prefilledCode)
        assertEquals(RelationshipTypeRoute, nav.previousBackStackEntry!!.toRoute<RelationshipTypeRoute>())
    }
}
