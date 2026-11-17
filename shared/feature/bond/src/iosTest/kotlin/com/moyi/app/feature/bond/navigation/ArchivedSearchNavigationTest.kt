package com.moyi.app.feature.bond.navigation

import androidx.navigation.NavHostController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.createGraph
import androidx.navigation.toRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Bond · archived › Search by meaning (docs/missing-components.md §91): the screen is pushed over
 * the archived bond, with no Bond settings under it, and back returns to the archived bond, whose
 * entry keeps the ViewModel both share. Builds the real graphs and navigates; nothing is composed.
 * iOS only, because Android's NavHostController needs a Context that host tests do not have.
 */
class ArchivedSearchNavigationTest {
    private fun controller(): NavHostController = NavHostController().apply {
        navigatorProvider.addNavigator(ComposeNavigator())
        graph = createGraph(startDestination = BondArchivedRoute(partnerName = "Tunde", bondId = "b-1")) {
            bondArchivedDestination(this@apply, onStartNewBond = {}, onOpenAccount = {})
            bondSettingsGraph(this@apply, onBondEnded = {}, onJoinWithCode = { _, _ -> })
            accountGraph(this@apply, onOpenLegal = {}, onOpenSupport = {})
        }
    }

    @Test
    fun theArchivedBondCarriesItsIdForTheScreenToRead() {
        val nav = controller()

        val archived = nav.currentBackStackEntry!!.toRoute<BondArchivedRoute>()

        assertEquals(BondArchivedRoute(partnerName = "Tunde", bondId = "b-1"), archived)
    }

    @Test
    fun searchByMeaningOpensOverTheArchivedBondWithNoBondSettingsUnderIt() {
        val nav = controller()

        nav.navigate(ArchivedSearchByMeaningRoute) { launchSingleTop = true }

        assertEquals(ArchivedSearchByMeaningRoute, nav.currentBackStackEntry!!.toRoute<ArchivedSearchByMeaningRoute>())
        assertEquals("b-1", nav.getBackStackEntry<BondArchivedRoute>().toRoute<BondArchivedRoute>().bondId, "the ViewModel's entry is under it")
        assertTrue(runCatching { nav.getBackStackEntry<BondSettingsGraph>() }.isFailure, "an ended bond has no Bond settings")
    }

    @Test
    fun backReturnsToTheArchivedBond() {
        val nav = controller()
        nav.navigate(ArchivedSearchByMeaningRoute) { launchSingleTop = true }

        nav.popBackStack(ArchivedSearchByMeaningRoute, inclusive = true)

        assertEquals("b-1", nav.currentBackStackEntry!!.toRoute<BondArchivedRoute>().bondId, "back returns to the archived bond")
    }

    @Test
    fun theRowOpensOneScreenNotTwo() {
        val nav = controller()

        nav.navigate(ArchivedSearchByMeaningRoute) { launchSingleTop = true }
        nav.navigate(ArchivedSearchByMeaningRoute) { launchSingleTop = true }
        nav.popBackStack()

        assertEquals("b-1", nav.currentBackStackEntry!!.toRoute<BondArchivedRoute>().bondId, "one screen, not two")
    }
}
