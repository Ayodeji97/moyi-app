package com.moyi.app.feature.today.navigation

import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.createGraph
import androidx.navigation.toRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The daily loop's back stack on a real [NavHostController] (D10, D12). iOS only,
 * because Android's NavHostController needs a Context that host tests do not have.
 * What system back does on each screen, through the NavHost, is [TodayBackTest].
 */
class TodayGraphTest {
    private fun controller(): NavHostController = NavHostController().apply {
        navigatorProvider.addNavigator(ComposeNavigator())
        graph = createGraph(startDestination = TodayRoute) {
            todayGraph(this@apply, onOpenSettings = {}, debugTools = false)
        }
    }

    private fun NavHostController.isOn(route: kotlin.reflect.KClass<*>) =
        currentBackStackEntry!!.destination.hasRoute(route)

    @Test
    fun theRevealSitsOnTodaySoTheNavHostsBackLandsOnToday() {
        // D12: before, the reveal replaced Today, so Android Back closed the app.
        val nav = controller()
        nav.openReveal()
        assertTrue(nav.isOn(RevealRoute::class))
        assertTrue(nav.previousBackStackEntry!!.destination.hasRoute(TodayRoute::class))

        nav.popBackStack()

        assertTrue(nav.isOn(TodayRoute::class))
    }

    @Test
    fun theRevealSettlingLeavesTodayAloneOnTheStack() {
        val nav = controller()
        nav.openReveal()

        nav.backToToday()

        assertTrue(nav.isOn(TodayRoute::class))
        assertNull(nav.previousBackStackEntry, "Today is the root: nothing behind it, and no reveal to go back to")
    }

    @Test
    fun opensTheRevealOnceEvenIfAskedTwice() {
        val nav = controller()
        nav.openReveal()
        nav.openReveal()
        assertTrue(nav.previousBackStackEntry!!.destination.hasRoute(TodayRoute::class))
    }

    @Test
    fun aRevealWithNothingUnderItStillSettlesOnToday() {
        // The future "Your day is open." push may open the reveal with no Today below it.
        val nav = controller()
        nav.navigate(RevealRoute) { popUpTo(TodayRoute) { inclusive = true } }
        assertNull(nav.previousBackStackEntry)

        nav.backToToday()

        assertTrue(nav.isOn(TodayRoute::class))
        assertNull(nav.previousBackStackEntry)
    }

    @Test
    fun closingWriteReturnsToToday() {
        val nav = controller()
        nav.navigate(WriteRoute(editing = true))
        assertEquals(true, nav.currentBackStackEntry!!.toRoute<WriteRoute>().editing)

        nav.backToToday()

        assertTrue(nav.isOn(TodayRoute::class))
        assertNull(nav.previousBackStackEntry)
    }
}
