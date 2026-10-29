package com.moyi.app.feature.onboarding.join

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moyi.app.designsystem.MoyiTheme
import com.moyi.app.designsystem.component.InlineError
import com.moyi.app.designsystem.component.MoyiScaffold
import com.moyi.app.domain.bond.InvitePreview
import com.moyi.app.designsystem.component.MoyiText
import com.moyi.app.designsystem.component.PrimaryAction
import com.moyi.app.designsystem.component.PrimaryActionState
import com.moyi.app.designsystem.component.TextLink
import com.moyi.app.designsystem.component.TextTone
import com.moyi.app.designsystem.presentation.ObserveAsEvents
import com.moyi.app.designsystem.component.ScreenHeader
import com.moyi.app.feature.onboarding.common.RelationshipType
import com.moyi.app.feature.onboarding.resources.Res
import com.moyi.app.feature.onboarding.resources.join_confirm_action
import com.moyi.app.feature.onboarding.resources.join_confirm_action_sending
import com.moyi.app.feature.onboarding.resources.join_confirm_heading
import com.moyi.app.feature.onboarding.resources.join_confirm_line_reciprocity
import com.moyi.app.feature.onboarding.resources.join_confirm_secondary
import com.moyi.app.feature.onboarding.resources.join_eyebrow
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun JoinConfirmRoot(
    code: String,
    preview: InvitePreview,
    onFinish: () -> Unit,
    onNavigateToCannotBeUsed: () -> Unit,
    onNavigateToGate: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: JoinConfirmViewModel = koinViewModel { parametersOf(code, preview) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            JoinConfirmEvent.Finish -> onFinish()
            JoinConfirmEvent.NavigateBack -> onNavigateBack()
            JoinConfirmEvent.NavigateToCannotBeUsed -> onNavigateToCannotBeUsed()
            JoinConfirmEvent.NavigateToGate -> onNavigateToGate()
        }
    }
    JoinConfirmScreen(state = state, onAction = viewModel::onAction)
}

/**
 * Join · confirm (Figma 310:14; spec §3.7). Back, the heading at 96 (no
 * eyebrow under a back target, figma-deviations.md D1) naming the inviter and the bond, the reciprocity line 16 below, the
 * type line 16 below that. "Join" at 700 and "Not now" at 772: a centred
 * link whose target hugs its text (67 wide in Figma), not full width.
 */
@Composable
fun JoinConfirmScreen(
    state: JoinConfirmState,
    onAction: (JoinConfirmAction) -> Unit,
) {
    val spacing = MoyiTheme.spacing
    MoyiScaffold(
        onBack = { onAction(JoinConfirmAction.OnBackClick) },
        action = {
            state.failure?.let {
                InlineError(it.asString())
                Spacer(Modifier.height(spacing.sm))
            }
            PrimaryAction(
                text = stringResource(Res.string.join_confirm_action),
                onClick = { onAction(JoinConfirmAction.OnJoinClick) },
                state = if (state.joining) {
                    PrimaryActionState.Sending(stringResource(Res.string.join_confirm_action_sending))
                } else {
                    PrimaryActionState.Idle
                },
            )
            Spacer(Modifier.height(spacing.lg))
            TextLink(stringResource(Res.string.join_confirm_secondary), onClick = { onAction(JoinConfirmAction.OnNotNowClick) }, centered = true)
        },
    ) {
        ScreenHeader(
            eyebrow = stringResource(Res.string.join_eyebrow),
            heading = stringResource(Res.string.join_confirm_heading, state.inviterName, state.bondName),
        )
        Spacer(Modifier.height(spacing.md))
        MoyiText(stringResource(Res.string.join_confirm_line_reciprocity), tone = TextTone.Muted)
        Spacer(Modifier.height(spacing.md))
        MoyiText(stringResource(state.type.confirmLine), tone = TextTone.Muted)
    }
}

private val previewState = JoinConfirmState(inviterName = "Amara", bondName = "Amara and Tunde", type = RelationshipType.Couple)

@Preview
@Composable
private fun JoinConfirmPreview() {
    MoyiTheme(darkTheme = true) { JoinConfirmScreen(state = previewState, onAction = {}) }
}

@Preview
@Composable
private fun JoinConfirmJoiningPreview() {
    MoyiTheme(darkTheme = true) { JoinConfirmScreen(state = previewState.copy(joining = true), onAction = {}) }
}

@Preview
@Composable
private fun JoinConfirmLightPreview() {
    MoyiTheme(darkTheme = false) { JoinConfirmScreen(state = previewState.copy(type = RelationshipType.Family), onAction = {}) }
}
