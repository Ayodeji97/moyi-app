package com.moyi.app.feature.auth.navigation

import androidx.navigation.NavGraph
import androidx.navigation.NavHostController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The back stack the auth exits leave, on a real NavHostController with the
 * real auth graph. The calls are the ones the screens make
 * ([backToLogin], [backFromRegister]). iOS only, because Android's
 * NavHostController needs a Context that host tests do not have.
 */
class AuthBackStackTest {
    /** Onboarding 1 lives in another feature; this stands in for it as the signed-out root. */
    @Serializable
    private data object PromiseStandIn

    private fun controllerAtPromise(): NavHostController = NavHostController().apply {
        navigatorProvider.addNavigator(ComposeNavigator())
        graph = createGraph(startDestination = PromiseStandIn) {
            composable<PromiseStandIn> {}
            authGraph(this@apply, onSignedIn = {})
        }
    }

    /** The visible stack, bottom first, as route names (graph entries left out). */
    private fun NavHostController.stack(): List<String> =
        currentBackStack.value
            .filter { it.destination !is NavGraph }
            .map { it.destination.route!!.substringBefore('?').substringBefore('/').substringAfterLast('.') }

    @Test
    fun haveAnAccountFromRegisterOverThePromiseLeavesPromiseThenLogin() {
        val nav = controllerAtPromise()
        nav.navigate(RegisterRoute)

        nav.backToLogin()

        assertEquals(listOf("PromiseStandIn", "LoginRoute"), nav.stack())
        assertEquals(LoginRoute(), nav.currentBackStackEntry!!.toRoute<LoginRoute>())
    }

    @Test
    fun backFromRegisterOverThePromiseReturnsToThePromise() {
        val nav = controllerAtPromise()
        nav.navigate(RegisterRoute)

        nav.backFromRegister()

        assertEquals(listOf("PromiseStandIn"), nav.stack())
    }
}
