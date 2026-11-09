package com.moyi.app.feature.bond.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.toRoute
import com.moyi.app.designsystem.presentation.ObserveAsEvents
import com.moyi.app.feature.bond.report.ReportArgs
import com.moyi.app.feature.bond.report.ReportComposeScreen
import com.moyi.app.feature.bond.report.ReportEvent
import com.moyi.app.feature.bond.report.ReportReasonScreen
import com.moyi.app.feature.bond.report.ReportSentScreen
import com.moyi.app.feature.bond.report.ReportSubject
import com.moyi.app.feature.bond.report.ReportTargetScreen
import com.moyi.app.feature.bond.report.ReportViewModel
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * A report (FR-100): of the partner's entry [entryId] or of the member, in [bondId].
 * [entryId] set: the flow asks which first. Null: the member, straight to the form.
 */
@Serializable
data class ReportGraph(
    val bondId: String,
    val entryId: String? = null,
    val partnerName: String? = null,
    val fromSettings: Boolean = false,
)

@Serializable
internal data object ReportTargetRoute

@Serializable
internal data object ReportComposeRoute

@Serializable
internal data object ReportReasonRoute

@Serializable
internal data object ReportSentRoute

/** Opens a report over the current screen; leaving it, by back or by its last action, returns here. */
fun NavController.openReport(bondId: String, entryId: String?, partnerName: String?, fromSettings: Boolean = false) {
    navigate(ReportGraph(bondId, entryId, partnerName, fromSettings)) { launchSingleTop = true }
}

/**
 * The report flow. Back stack, from the Archive day view (or Bond settings):
 *
 * - Day view → choose target (push), when the day shows the partner's entry; else, and
 *   from Bond settings, → the form (push) about the member.
 * - Choose target → the form (push). Back returns to choose target.
 * - The form → the reason picker (push); choosing pops back to the form.
 * - Sending → sent, replacing the flow, so back from sent (or its one action) leaves the
 *   flow and lands where the report started. Nothing above the flow is touched.
 */
fun NavGraphBuilder.reportGraph(navController: NavController) {
    navigation<ReportGraph>(startDestination = ReportTargetRoute) {
        composable<ReportTargetRoute> { entry ->
            ReportDestination(entry, navController) { viewModel, args ->
                val state by viewModel.state.collectAsStateWithLifecycle()
                // Only a day that shows the partner's entry asks which; otherwise straight to the form.
                if (args.entryId == null) {
                    ReportComposeScreen(state = state, onAction = viewModel::onAction, onBack = { navController.leaveReport() })
                } else {
                    ReportTargetScreen(state = state, onAction = viewModel::onAction, onBack = { navController.leaveReport() })
                }
            }
        }
        composable<ReportComposeRoute> { entry ->
            ReportDestination(entry, navController) { viewModel, _ ->
                val state by viewModel.state.collectAsStateWithLifecycle()
                ReportComposeScreen(state = state, onAction = viewModel::onAction, onBack = { navController.popBackStack() })
            }
        }
        composable<ReportReasonRoute> { entry ->
            ReportDestination(entry, navController) { viewModel, _ ->
                val state by viewModel.state.collectAsStateWithLifecycle()
                ReportReasonScreen(state = state, onAction = viewModel::onAction, onBack = { navController.popBackStack() })
            }
        }
        composable<ReportSentRoute> { entry ->
            ReportDestination(entry, navController) { viewModel, _ ->
                val state by viewModel.state.collectAsStateWithLifecycle()
                ReportSentScreen(state = state, reportedEntry = state.subject == ReportSubject.Entry, onAction = viewModel::onAction)
            }
        }
    }
}

/** The flow's one ViewModel, from the graph's entry, and its events, answered by whichever screen shows. */
@Composable
private fun ReportDestination(
    entry: NavBackStackEntry,
    navController: NavController,
    content: @Composable (ReportViewModel, ReportArgs) -> Unit,
) {
    val graphEntry = remember(entry) { navController.getBackStackEntry<ReportGraph>() }
    val args = remember(graphEntry) {
        graphEntry.toRoute<ReportGraph>().let { ReportArgs(it.bondId, it.entryId, it.partnerName, it.fromSettings) }
    }
    val viewModel: ReportViewModel = koinViewModel(viewModelStoreOwner = graphEntry) { parametersOf(args) }
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            ReportEvent.OpenCompose -> navController.navigate(ReportComposeRoute) { launchSingleTop = true }
            ReportEvent.OpenReasonPicker -> navController.navigate(ReportReasonRoute) { launchSingleTop = true }
            ReportEvent.ReasonPicked -> navController.popBackStack<ReportReasonRoute>(inclusive = true)
            // The flow's own screens go; the graph's entry stays until sent is left.
            ReportEvent.Sent -> navController.navigate(ReportSentRoute) {
                popUpTo<ReportGraph> { inclusive = false }
            }
            ReportEvent.Done -> navController.leaveReport()
        }
    }
    content(viewModel, args)
}

/** Leaves the whole flow, back to the screen that opened it. */
private fun NavController.leaveReport() {
    popBackStack<ReportGraph>(inclusive = true)
}
