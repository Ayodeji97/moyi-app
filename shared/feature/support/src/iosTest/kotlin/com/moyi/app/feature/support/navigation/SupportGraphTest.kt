package com.moyi.app.feature.support.navigation

import androidx.navigation.NavHostController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.createGraph
import androidx.navigation.toRoute
import com.moyi.app.feature.support.SupportOrigin
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Builds Support's destination and navigates to it from each origin. The first run on the
 * iPhone simulator crashed while building the graph: an enum route argument has no NavType
 * on iOS. iOS only, because Android's NavHostController needs a Context host tests lack.
 */
class SupportGraphTest {
    @Test
    fun eachOriginSurvivesNavigation() {
        val nav = NavHostController().apply {
            navigatorProvider.addNavigator(ComposeNavigator())
            graph = createGraph(startDestination = SupportRoute(SupportOrigin.Settings)) { supportDestination(onClose = {}) }
        }
        for (origin in SupportOrigin.entries) {
            nav.navigate(SupportRoute(origin))
            assertEquals(origin, nav.currentBackStackEntry!!.toRoute<SupportRoute>().origin)
        }
    }
}
