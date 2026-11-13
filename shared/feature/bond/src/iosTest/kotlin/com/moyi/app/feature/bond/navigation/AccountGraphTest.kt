package com.moyi.app.feature.bond.navigation

import androidx.navigation.NavHostController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.createGraph
import androidx.navigation.toRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Account is reachable without a bond (owner ruling 2026-11-09): its own graph, beside Bond
 * settings rather than inside it. Builds the real graphs and navigates; nothing is composed.
 * iOS only, because Android's NavHostController needs a Context that host tests do not have.
 */
class AccountGraphTest {
    private fun controller(): NavHostController = NavHostController().apply {
        navigatorProvider.addNavigator(ComposeNavigator())
        graph = createGraph(startDestination = BondArchivedRoute()) {
            bondArchivedDestination(this@apply, onStartNewBond = {}, onOpenAccount = {})
            bondSettingsGraph(this@apply, onBondEnded = {}, onJoinWithCode = { _, _ -> })
            accountGraph(this@apply, onOpenLegal = {}, onOpenSupport = {})
        }
    }

    @Test
    fun accountOpensWithNoBondSettingsBehindIt() {
        val nav = controller()

        nav.navigate(AccountGraph)

        assertEquals(AccountRoute, nav.currentBackStackEntry!!.toRoute<AccountRoute>())
        assertTrue(
            runCatching { nav.getBackStackEntry<BondSettingsGraph>() }.isFailure,
            "no Bond settings was needed to get here",
        )
        nav.popBackStack()
        assertEquals(null, nav.currentBackStackEntry!!.toRoute<BondArchivedRoute>().partnerName, "back returns to where it opened")
    }

    @Test
    fun accountStillOpensFromBondSettingsAndBackReturnsThere() {
        val nav = controller()
        nav.navigate(BondSettingsGraph(bondId = "b-1"))

        nav.navigate(AccountGraph)
        nav.navigate(DeleteAccountRoute)
        nav.popBackStack(AccountRoute, inclusive = false)
        nav.popBackStack()

        assertEquals(BondSettingsRoute, nav.currentBackStackEntry!!.toRoute<BondSettingsRoute>())
    }

    @Test
    fun exportAndDeleteAreInsideTheAccountGraph() {
        val nav = controller()
        nav.navigate(AccountGraph)
        nav.navigate(ExportRoute)
        nav.popBackStack()
        nav.navigate(DeleteAccountRoute)

        // The delete screen shares Account's ViewModel through Account's entry.
        assertEquals(AccountRoute, nav.getBackStackEntry<AccountRoute>().toRoute<AccountRoute>())
        assertEquals(DeleteAccountRoute, nav.currentBackStackEntry!!.toRoute<DeleteAccountRoute>())
    }

    @Test
    fun theArchivedBondsExportOpensExportWithNoAccountUnderIt() {
        val nav = controller()

        nav.navigateToExport()

        assertEquals(ExportRoute, nav.currentBackStackEntry!!.toRoute<ExportRoute>())
        assertTrue(runCatching { nav.getBackStackEntry<AccountRoute>() }.isFailure, "export is opened directly, not through Account")
        nav.popBackStack()
        assertEquals(null, nav.currentBackStackEntry!!.toRoute<BondArchivedRoute>().partnerName, "back returns to the archived bond")
    }
}
