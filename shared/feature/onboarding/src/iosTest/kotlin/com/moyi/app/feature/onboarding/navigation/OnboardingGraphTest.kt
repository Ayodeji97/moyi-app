package com.moyi.app.feature.onboarding.navigation

import androidx.navigation.NavHostController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.createGraph
import androidx.navigation.toRoute
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
        nav.navigate(InviteHandoffRoute("K7Q2PD"))
        assertEquals("K7Q2PD", nav.currentBackStackEntry!!.toRoute<InviteHandoffRoute>().code)
        nav.navigate(JoinCodeRoute(prefilledCode = "K7Q2PD"))
        assertEquals("K7Q2PD", nav.currentBackStackEntry!!.toRoute<JoinCodeRoute>().prefilledCode)
        nav.navigate(JoinCodeRoute())
        assertEquals(null, nav.currentBackStackEntry!!.toRoute<JoinCodeRoute>().prefilledCode)
        nav.navigate(JoinConfirmRoute("K7Q2PD"))
        assertEquals("K7Q2PD", nav.currentBackStackEntry!!.toRoute<JoinConfirmRoute>().code)
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

        nav.replaceWithInvite("K7Q2PD")

        assertEquals("K7Q2PD", nav.currentBackStackEntry!!.toRoute<InviteHandoffRoute>().code)
        assertNull(nav.previousBackStackEntry, "the bond exists; back must not return to the form")
    }
}
