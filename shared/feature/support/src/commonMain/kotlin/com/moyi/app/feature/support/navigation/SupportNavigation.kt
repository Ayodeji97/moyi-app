package com.moyi.app.feature.support.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.moyi.app.feature.support.SupportOrigin
import com.moyi.app.feature.support.SupportRoot
import kotlinx.serialization.Serializable

/**
 * Get help. Pushed over the screen that opened it: Bond settings (Just you), or the
 * signed-in start's Generic error. [origin] names which, for the words of the way back.
 *
 * The route carries the origin's name, a String, as `CreateBondRoute` does: navigation
 * has no NavType for an enum on iOS (it finds one by reflection on Android only), and
 * building the graph there throws.
 */
@Serializable
data class SupportRoute(val originName: String) {
    constructor(origin: SupportOrigin) : this(origin.name)

    val origin: SupportOrigin get() = SupportOrigin.valueOf(originName)
}

/**
 * Support, one destination whose content follows the send (writing, sent, paused). Back,
 * Android Back, the iOS edge swipe, and the one action on Sent or Paused all [onClose],
 * which the shell answers by popping: the screen that opened it is under it.
 */
fun NavGraphBuilder.supportDestination(onClose: () -> Unit) {
    composable<SupportRoute> { entry ->
        SupportRoot(origin = entry.toRoute<SupportRoute>().origin, onClose = onClose)
    }
}
