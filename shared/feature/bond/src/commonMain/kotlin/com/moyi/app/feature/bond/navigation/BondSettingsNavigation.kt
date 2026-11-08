package com.moyi.app.feature.bond.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.toRoute
import com.moyi.app.designsystem.platform.rememberShareSheet
import com.moyi.app.designsystem.platform.rememberTextClipboard
import com.moyi.app.designsystem.presentation.ObserveAsEvents
import com.moyi.app.feature.bond.ending.BondArchivedScreen
import com.moyi.app.feature.bond.settings.BlockScreen
import com.moyi.app.feature.bond.settings.BondNameScreen
import com.moyi.app.feature.bond.settings.BondSettingsAction
import com.moyi.app.feature.bond.settings.BondSettingsEvent
import com.moyi.app.feature.bond.settings.BondSettingsScreen
import com.moyi.app.feature.bond.settings.BondSettingsViewModel
import com.moyi.app.feature.bond.settings.BondTypeScreen
import com.moyi.app.feature.bond.settings.CloseBoxScreen
import com.moyi.app.feature.bond.settings.InviteScreen
import com.moyi.app.feature.bond.settings.LeaveScreen
import com.moyi.app.feature.bond.settings.TimeZoneScreen
import com.moyi.app.feature.bond.settings.ZonePickerScreen
import com.moyi.app.feature.onboarding.resources.invite_share_text
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import com.moyi.app.feature.onboarding.resources.Res as OnboardingRes

/** Bond settings for [bondId]: the graph the root and its editors share one ViewModel in. */
@Serializable
data class BondSettingsGraph(val bondId: String)

@Serializable
internal data object BondSettingsRoute

@Serializable
internal data object BondNameRoute

@Serializable
internal data object BondTypeRoute

@Serializable
internal data object TimeZoneRoute

@Serializable
internal data object ZonePickerRoute

@Serializable
internal data object InviteRoute

@Serializable
internal data object LeaveRoute

@Serializable
internal data object BlockRoute

@Serializable
internal data object CloseBoxRoute

/**
 * Bond · archived, the signed-in start's answer when the person's bond has ended and they
 * are in no open one. [partnerName] is the other member's, for the line; null when unknown.
 */
@Serializable
data class BondArchivedRoute(val partnerName: String? = null)

/**
 * Bond settings. Back stack, from Today:
 *
 * - Today → Bond settings (push). Its back arrow, Android Back and the iOS edge swipe
 *   return to Today.
 * - Bond settings → Bond name, → Relationship type (push). Back returns to the root
 *   unsaved; a save returns to it too, showing what was saved.
 * - Bond settings → Shared time zone (push), one destination whose content follows the
 *   pending change (propose, proposed, confirm). From Propose, the zone field → the city
 *   list (push); choosing one pops back to Propose. Proposing stays on the screen, which
 *   turns to Proposed; agreeing, cancelling, declining, or a change that applied at once
 *   (nobody has joined), returns to the root.
 * - Bond settings → Invite (push). Share opens the OS sheet and goes nowhere; cancelling
 *   or creating an invite changes the screen in place.
 * - The reveal time picker is a dialog over the root: no destination.
 * - Bond settings → Leave, → Block and leave (push): full confirm screens, as Figma draws
 *   them, because each carries FR-029a's withdraw row. Back and Cancel return to the root.
 * - Bond settings → Close the box (push), one destination whose content follows the bond
 *   (ask, asked, agree, cooling off). Asking stays on it; withdrawing, declining or
 *   calling it off returns to the root.
 * - Leaving, blocking, or finding the bond ended, calls [onBondEnded]: the shell sends the
 *   person to the signed-in start, which shows the archived bond. Signing out needs
 *   nothing here; the shell routes on the session ending.
 */
fun NavGraphBuilder.bondSettingsGraph(
    navController: NavController,
    onBondEnded: () -> Unit,
) {
    navigation<BondSettingsGraph>(startDestination = BondSettingsRoute) {
        composable<BondSettingsRoute> { entry ->
            SettingsDestination(entry, navController, onBondEnded) { viewModel ->
                val state by viewModel.state.collectAsStateWithLifecycle()
                BondSettingsScreen(state = state, onAction = viewModel::onAction, onBack = { navController.popBackStack() })
            }
        }
        composable<BondNameRoute> { entry ->
            SettingsDestination(entry, navController, onBondEnded) { viewModel ->
                val state by viewModel.state.collectAsStateWithLifecycle()
                BondNameScreen(state = state, onAction = viewModel::onAction, onBack = { navController.closeEditor(viewModel) })
            }
        }
        composable<TimeZoneRoute> { entry ->
            SettingsDestination(entry, navController, onBondEnded) { viewModel ->
                val state by viewModel.state.collectAsStateWithLifecycle()
                TimeZoneScreen(state = state, onAction = viewModel::onAction, onBack = { navController.closeEditor(viewModel) })
            }
        }
        composable<ZonePickerRoute> { entry ->
            SettingsDestination(entry, navController, onBondEnded) { viewModel ->
                val state by viewModel.state.collectAsStateWithLifecycle()
                ZonePickerScreen(state = state, onAction = viewModel::onAction, onBack = { navController.popBackStack() })
            }
        }
        composable<InviteRoute> { entry ->
            SettingsDestination(entry, navController, onBondEnded) { viewModel ->
                val state by viewModel.state.collectAsStateWithLifecycle()
                InviteScreen(state = state, onAction = viewModel::onAction, onBack = { navController.closeEditor(viewModel) })
            }
        }
        composable<LeaveRoute> { entry ->
            SettingsDestination(entry, navController, onBondEnded) { viewModel ->
                val state by viewModel.state.collectAsStateWithLifecycle()
                LeaveScreen(state = state, onAction = viewModel::onAction, onBack = { navController.closeEditor(viewModel) })
            }
        }
        composable<BlockRoute> { entry ->
            SettingsDestination(entry, navController, onBondEnded) { viewModel ->
                val state by viewModel.state.collectAsStateWithLifecycle()
                BlockScreen(state = state, onAction = viewModel::onAction, onBack = { navController.closeEditor(viewModel) })
            }
        }
        composable<CloseBoxRoute> { entry ->
            SettingsDestination(entry, navController, onBondEnded) { viewModel ->
                val state by viewModel.state.collectAsStateWithLifecycle()
                CloseBoxScreen(state = state, onAction = viewModel::onAction, onBack = { navController.closeEditor(viewModel) })
            }
        }
        composable<BondTypeRoute> { entry ->
            SettingsDestination(entry, navController, onBondEnded) { viewModel ->
                val state by viewModel.state.collectAsStateWithLifecycle()
                BondTypeScreen(state = state, onAction = viewModel::onAction, onBack = { navController.closeEditor(viewModel) })
            }
        }
    }
}

/**
 * The graph's ViewModel, from the graph's back stack entry, so all three screens see one
 * state; and its events, answered by whichever screen is showing (a covered screen is
 * stopped, so it does not collect).
 */
@Composable
private fun SettingsDestination(
    entry: NavBackStackEntry,
    navController: NavController,
    onBondEnded: () -> Unit,
    content: @Composable (BondSettingsViewModel) -> Unit,
) {
    val graphEntry = remember(entry) { navController.getBackStackEntry<BondSettingsGraph>() }
    val bondId = remember(graphEntry) { graphEntry.toRoute<BondSettingsGraph>().bondId }
    val viewModel: BondSettingsViewModel = koinViewModel(viewModelStoreOwner = graphEntry) { parametersOf(bondId) }
    val clipboard = rememberTextClipboard()
    val shareSheet = rememberShareSheet()
    val scope = rememberCoroutineScope()
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            BondSettingsEvent.OpenName -> navController.navigate(BondNameRoute) { launchSingleTop = true }
            BondSettingsEvent.OpenType -> navController.navigate(BondTypeRoute) { launchSingleTop = true }
            BondSettingsEvent.Saved -> navController.popBackStack(BondSettingsRoute, inclusive = false)
            BondSettingsEvent.Ended -> onBondEnded()
            BondSettingsEvent.OpenTimeZone -> navController.navigate(TimeZoneRoute) { launchSingleTop = true }
            BondSettingsEvent.OpenZonePicker -> navController.navigate(ZonePickerRoute) { launchSingleTop = true }
            BondSettingsEvent.ZonePicked -> navController.popBackStack(TimeZoneRoute, inclusive = false)
            BondSettingsEvent.OpenInvite -> navController.navigate(InviteRoute) { launchSingleTop = true }
            BondSettingsEvent.OpenLeave -> navController.navigate(LeaveRoute) { launchSingleTop = true }
            BondSettingsEvent.OpenBlock -> navController.navigate(BlockRoute) { launchSingleTop = true }
            BondSettingsEvent.OpenCloseBox -> navController.navigate(CloseBoxRoute) { launchSingleTop = true }
            is BondSettingsEvent.CopyInviteCode -> clipboard.copy(event.code)
            // One whole sentence with both placeholders, never concatenated (copy.md); onboarding's words.
            is BondSettingsEvent.ShareInvite -> scope.launch {
                shareSheet.share(getString(OnboardingRes.string.invite_share_text, event.link, event.code))
            }
        }
    }
    content(viewModel)
}

private fun NavController.closeEditor(viewModel: BondSettingsViewModel) {
    viewModel.onAction(BondSettingsAction.OnEditorClosed)
    popBackStack(BondSettingsRoute, inclusive = false)
}

/**
 * Bond · archived ([BondArchivedRoute]). A root with no back (SKILL.md); the shell makes it
 * the only screen. [onStartNewBond] pushes onboarding over it, so back from there returns.
 */
fun NavGraphBuilder.bondArchivedDestination(onStartNewBond: () -> Unit) {
    composable<BondArchivedRoute> { entry ->
        val route = entry.toRoute<BondArchivedRoute>()
        BondArchivedScreen(partnerName = route.partnerName, onStartNewBond = onStartNewBond)
    }
}
