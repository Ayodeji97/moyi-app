package com.moyi.app.feature.bond.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.moyi.app.designsystem.presentation.ObserveAsEvents
import com.moyi.app.feature.bond.account.AccountAction
import com.moyi.app.feature.bond.account.AccountEvent
import com.moyi.app.feature.bond.account.AccountScreen
import com.moyi.app.feature.bond.account.AccountViewModel
import com.moyi.app.feature.bond.account.DeleteAccountScreen
import com.moyi.app.feature.bond.account.ExportAction
import com.moyi.app.feature.bond.account.ExportEvent
import com.moyi.app.feature.bond.account.ExportScreen
import com.moyi.app.feature.bond.account.ExportViewModel
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel

@Serializable
internal data object AccountRoute

@Serializable
internal data object DeleteAccountRoute

@Serializable
internal data object ExportRoute

/**
 * Account and what it opens, inside the Bond settings graph. Back stack:
 *
 * - Bond settings → Account (push). Back returns to Bond settings.
 * - Account → Delete your account (push), sharing Account's ViewModel (from Account's entry),
 *   so the row shows the pending day as soon as it is asked for. Asking stays on the screen,
 *   which turns to the pending state; keeping the account returns to Account. Back clears the
 *   typed password.
 * - Account, or Delete your account's "Export your archive first" → Export (push), with a
 *   ViewModel of its own, keyed to its entry: polling runs only while it is started (on
 *   screen, app in front), and stops for good when it is popped. Download leaves the app for
 *   the phone's browser.
 */
internal fun NavGraphBuilder.accountDestinations(navController: NavController) {
    composable<AccountRoute> { entry ->
        AccountDestination(entry, navController) { viewModel ->
            val state by viewModel.state.collectAsStateWithLifecycle()
            AccountScreen(state = state, onAction = viewModel::onAction, onBack = { navController.popBackStack() })
        }
    }
    composable<DeleteAccountRoute> { entry ->
        val owner = remember(entry) { navController.getBackStackEntry<AccountRoute>() }
        AccountDestination(owner, navController) { viewModel ->
            val state by viewModel.state.collectAsStateWithLifecycle()
            DeleteAccountScreen(
                state = state,
                onAction = viewModel::onAction,
                onBack = {
                    viewModel.onAction(AccountAction.OnDeleteClosed)
                    navController.popBackStack(AccountRoute, inclusive = false)
                },
            )
        }
    }
    composable<ExportRoute> { ExportDestination(navController) }
}

@Composable
private fun AccountDestination(
    /** Account's own entry: its ViewModel lives there, shared with the delete screen. */
    owner: NavBackStackEntry,
    navController: NavController,
    content: @Composable (AccountViewModel) -> Unit,
) {
    val viewModel: AccountViewModel = koinViewModel(viewModelStoreOwner = owner)
    // Collected while this destination is started, so a covered Account does not also answer.
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            AccountEvent.OpenExport -> navController.navigate(ExportRoute) { launchSingleTop = true }
            AccountEvent.OpenDelete -> navController.navigate(DeleteAccountRoute) { launchSingleTop = true }
            AccountEvent.DeletionCancelled -> navController.popBackStack(AccountRoute, inclusive = false)
        }
    }
    content(viewModel)
}

@Composable
private fun ExportDestination(navController: NavController) {
    val viewModel: ExportViewModel = koinViewModel()
    val uriHandler = LocalUriHandler.current
    LifecycleStartEffect(viewModel) {
        viewModel.onAction(ExportAction.OnScreenShown)
        onStopOrDispose { viewModel.onAction(ExportAction.OnScreenHidden) }
    }
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            // The phone's browser downloads it. The URL is a credential: handed over, never logged.
            is ExportEvent.OpenDownload -> runCatching { uriHandler.openUri(event.url) }
        }
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    ExportScreen(state = state, onAction = viewModel::onAction, onBack = { navController.popBackStack() })
}
