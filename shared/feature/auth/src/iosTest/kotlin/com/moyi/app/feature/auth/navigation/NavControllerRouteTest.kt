package com.moyi.app.feature.auth.navigation

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavHostController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.navigation.toRoute
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The full trip: navigate() URL-encodes the arguments into a route string and
 * the destination decodes them back. A `+` that came back as a space would send
 * the verify screen, and then sign in, the wrong address. iOS only, because
 * Android's NavHostController needs a Context that host tests do not have.
 */
class NavControllerRouteTest {
    @Test
    fun anEmailWithPlusAndUnicodeSurvivesNavigation() {
        val email = "a+b@exämple.com"
        val nav = NavHostController()
        nav.navigatorProvider.addNavigator(ComposeNavigator())
        nav.graph = nav.createGraph(startDestination = LoginRoute()) {
            composable<LoginRoute> {}
            composable<VerifyRoute> {}
        }

        nav.navigate(VerifyRoute(email))
        assertEquals(VerifyRoute(email), nav.currentBackStackEntry!!.toRoute<VerifyRoute>())

        nav.navigate(LoginRoute(email = email))
        assertEquals(LoginRoute(email = email), nav.currentBackStackEntry!!.toRoute<LoginRoute>())
    }

    @Test
    fun toRouteReadsTheEmailBackFromSavedArguments() {
        // What a ViewModel's SavedStateHandle would hold after navigate(VerifyRoute(email)).
        val email = "a+b@exämple.com"
        assertEquals(VerifyRoute(email), SavedStateHandle(mapOf("email" to email)).toRoute<VerifyRoute>())
    }

    @Test
    fun aLinkTokenAndAnEmptyAddressSurviveNavigation() {
        // The routes an opened link builds (LinkRouting): base64url token, empty address.
        val token = "q3Zx-8_kP0aBcDeFgHiJkLmNoPqRsTuVwXyZ0123456"
        val nav = NavHostController()
        nav.navigatorProvider.addNavigator(ComposeNavigator())
        nav.graph = nav.createGraph(startDestination = LoginRoute()) {
            composable<LoginRoute> {}
            composable<VerifyRoute> {}
            composable<ResetPasswordRoute> {}
        }

        nav.navigate(VerifyRoute("", linkToken = token, signedIn = true))
        assertEquals(VerifyRoute("", linkToken = token, signedIn = true), nav.currentBackStackEntry!!.toRoute<VerifyRoute>())

        nav.navigate(ResetPasswordRoute("", linkToken = token))
        assertEquals(ResetPasswordRoute("", linkToken = token), nav.currentBackStackEntry!!.toRoute<ResetPasswordRoute>())

        nav.navigate(LoginRoute(passwordChanged = true))
        assertEquals(LoginRoute(passwordChanged = true), nav.currentBackStackEntry!!.toRoute<LoginRoute>())
    }
}
