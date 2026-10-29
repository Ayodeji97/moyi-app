package com.moyi.app.feature.onboarding.gate

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moyi.app.designsystem.MoyiTheme
import com.moyi.app.designsystem.component.InlineError
import com.moyi.app.designsystem.component.MoyiScaffold
import com.moyi.app.designsystem.component.MoyiText
import com.moyi.app.designsystem.component.ScreenHeader
import com.moyi.app.designsystem.component.TextLink
import com.moyi.app.designsystem.component.TextTone
import com.moyi.app.designsystem.presentation.ObserveAsEvents
import com.moyi.app.designsystem.theme.MoyiDimens
import com.moyi.app.feature.onboarding.resources.Res
import com.moyi.app.feature.onboarding.resources.gate_eyebrow
import com.moyi.app.feature.onboarding.resources.gate_heading
import com.moyi.app.feature.onboarding.resources.gate_line
import com.moyi.app.feature.onboarding.resources.gate_resend
import com.moyi.app.feature.onboarding.resources.gate_resent
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun UnverifiedGateRoot(
    onNavigateToOnboarding: () -> Unit,
    viewModel: UnverifiedGateViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onAction(UnverifiedGateAction.OnResume) }
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            UnverifiedGateEvent.NavigateToOnboarding -> onNavigateToOnboarding()
        }
    }
    UnverifiedGateScreen(state = state, onAction = viewModel::onAction)
}

/**
 * Account 5 · unverified gate (Figma 345:46; states.md §1b). A root: the
 * eyebrow at 24, the heading 64 below, the line 16 below that. The action
 * block holds one centred secondary affordance at 772, "Send it again",
 * which becomes the muted acknowledgement once sent (the verify screen's
 * Resent sub-state, 362:95). Drawn as a plain screen, not a StateBlock.
 */
@Composable
fun UnverifiedGateScreen(
    state: UnverifiedGateState,
    onAction: (UnverifiedGateAction) -> Unit,
) {
    val spacing = MoyiTheme.spacing
    MoyiScaffold(
        action = {
            state.resendFailure?.let {
                InlineError(it.asString())
                Spacer(Modifier.height(spacing.sm))
            }
            if (state.resend == GateResendStatus.Sent) {
                MoyiText(
                    stringResource(Res.string.gate_resent),
                    tone = TextTone.Muted,
                    textAlign = TextAlign.Center,
                    // The acknowledgement takes the link's 48 so nothing jumps.
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = MoyiDimens.TouchTarget)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                )
            } else {
                TextLink(
                    stringResource(Res.string.gate_resend),
                    onClick = { onAction(UnverifiedGateAction.OnResendClick) },
                    centered = true,
                    enabled = state.resend == GateResendStatus.Ready,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    ) {
        ScreenHeader(
            eyebrow = stringResource(Res.string.gate_eyebrow),
            heading = stringResource(Res.string.gate_heading),
        )
        Spacer(Modifier.height(spacing.md))
        MoyiText(stringResource(Res.string.gate_line, state.email), tone = TextTone.Muted)
    }
}

private val previewState = UnverifiedGateState(email = "amara@example.com")

@Preview
@Composable
private fun UnverifiedGatePreview() {
    MoyiTheme(darkTheme = true) { UnverifiedGateScreen(state = previewState, onAction = {}) }
}

@Preview
@Composable
private fun UnverifiedGateResentPreview() {
    MoyiTheme(darkTheme = true) { UnverifiedGateScreen(state = previewState.copy(resend = GateResendStatus.Sent), onAction = {}) }
}

@Preview
@Composable
private fun UnverifiedGateLightPreview() {
    MoyiTheme(darkTheme = false) { UnverifiedGateScreen(state = previewState, onAction = {}) }
}
