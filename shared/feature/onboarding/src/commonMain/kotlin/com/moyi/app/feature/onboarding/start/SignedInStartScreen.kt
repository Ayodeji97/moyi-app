package com.moyi.app.feature.onboarding.start

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moyi.app.designsystem.MoyiTheme
import com.moyi.app.designsystem.component.MoyiScaffold
import com.moyi.app.designsystem.component.StateBlock
import com.moyi.app.designsystem.presentation.ObserveAsEvents
import com.moyi.app.feature.onboarding.resources.Res
import com.moyi.app.feature.onboarding.resources.generic_error_action
import com.moyi.app.feature.onboarding.resources.generic_error_heading
import com.moyi.app.feature.onboarding.resources.generic_error_line
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SignedInStartRoot(
    onNavigateToToday: () -> Unit,
    onNavigateToOnboarding: () -> Unit,
    onNavigateToGate: () -> Unit,
    viewModel: SignedInStartViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            SignedInStartEvent.NavigateToToday -> onNavigateToToday()
            SignedInStartEvent.NavigateToOnboarding -> onNavigateToOnboarding()
            SignedInStartEvent.NavigateToGate -> onNavigateToGate()
        }
    }
    SignedInStartScreen(state = state, onAction = viewModel::onAction)
}

/**
 * Checking: the blank `surface`, as at launch (states.md §0). Most answers
 * come back before a frame is worth drawing, and a spinner is never invented.
 *
 * Failed: the Generic error (Figma 384:341): a StateBlock centred in the
 * content, no header and no back, its action "Try again".
 */
@Composable
fun SignedInStartScreen(
    state: SignedInStartState,
    onAction: (SignedInStartAction) -> Unit,
) {
    when (state) {
        SignedInStartState.Checking -> Box(Modifier.fillMaxSize().background(MoyiTheme.colors.surface))
        SignedInStartState.Failed -> MoyiScaffold(contentArrangement = Arrangement.Center) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                StateBlock(
                    heading = stringResource(Res.string.generic_error_heading),
                    line = stringResource(Res.string.generic_error_line),
                    actionLabel = stringResource(Res.string.generic_error_action),
                    onAction = { onAction(SignedInStartAction.OnRetryClick) },
                )
            }
        }
    }
}

@Preview
@Composable
private fun SignedInStartFailedPreview() {
    MoyiTheme(darkTheme = true) { SignedInStartScreen(state = SignedInStartState.Failed, onAction = {}) }
}

@Preview
@Composable
private fun SignedInStartFailedLightPreview() {
    MoyiTheme(darkTheme = false) { SignedInStartScreen(state = SignedInStartState.Failed, onAction = {}) }
}
