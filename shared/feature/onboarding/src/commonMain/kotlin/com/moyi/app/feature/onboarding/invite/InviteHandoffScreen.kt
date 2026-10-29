package com.moyi.app.feature.onboarding.invite

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moyi.app.designsystem.MoyiTheme
import com.moyi.app.designsystem.component.InviteCode
import com.moyi.app.designsystem.component.MoyiScaffold
import com.moyi.app.designsystem.component.MoyiText
import com.moyi.app.designsystem.component.PrimaryAction
import com.moyi.app.designsystem.component.TextLink
import com.moyi.app.designsystem.component.TextTone
import com.moyi.app.designsystem.presentation.ObserveAsEvents
import com.moyi.app.designsystem.component.ScreenHeader
import com.moyi.app.feature.onboarding.resources.Res
import com.moyi.app.feature.onboarding.resources.invite_action
import com.moyi.app.feature.onboarding.resources.invite_heading
import com.moyi.app.feature.onboarding.resources.invite_later
import com.moyi.app.feature.onboarding.resources.invite_line
import com.moyi.app.feature.onboarding.resources.onboarding_eyebrow_new_bond
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun InviteHandoffRoot(
    code: String,
    onFinish: () -> Unit,
    viewModel: InviteHandoffViewModel = koinViewModel { parametersOf(code) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            InviteHandoffEvent.Finish -> onFinish()
        }
    }
    InviteHandoffScreen(state = state, onAction = viewModel::onAction)
}

/**
 * Onboarding 6 (Figma 342:48; spec §3.4). A root screen: **no back**, since
 * the bond exists and back would read as un-creating it. Eyebrow at 24,
 * heading at 107, the line 16 below, the InviteCode 24 below that (228).
 * The action block: "Share invite" (opens the OS sheet, goes nowhere) and
 * "Continue", the single exit, 24 apart.
 */
@Composable
fun InviteHandoffScreen(
    state: InviteHandoffState,
    onAction: (InviteHandoffAction) -> Unit,
) {
    val spacing = MoyiTheme.spacing
    MoyiScaffold(
        action = {
            PrimaryAction(stringResource(Res.string.invite_action), onClick = { onAction(InviteHandoffAction.OnShareClick) })
            Spacer(Modifier.height(spacing.lg))
            TextLink(
                stringResource(Res.string.invite_later),
                onClick = { onAction(InviteHandoffAction.OnContinueClick) },
                centered = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) {
        ScreenHeader(
            eyebrow = stringResource(Res.string.onboarding_eyebrow_new_bond),
            heading = stringResource(Res.string.invite_heading),
        )
        Spacer(Modifier.height(spacing.md))
        MoyiText(stringResource(Res.string.invite_line), tone = TextTone.Muted)
        Spacer(Modifier.height(spacing.lg))
        InviteCode(code = state.code, copied = state.copied, onCopy = { onAction(InviteHandoffAction.OnCopyClick) })
    }
}

@Preview
@Composable
private fun InviteHandoffSentPreview() {
    MoyiTheme(darkTheme = true) { InviteHandoffScreen(state = InviteHandoffState(code = "K7Q2PD"), onAction = {}) }
}

@Preview
@Composable
private fun InviteHandoffCopiedPreview() {
    MoyiTheme(darkTheme = true) {
        InviteHandoffScreen(state = InviteHandoffState(code = "K7Q2PD", copied = true), onAction = {})
    }
}

@Preview
@Composable
private fun InviteHandoffLightPreview() {
    MoyiTheme(darkTheme = false) { InviteHandoffScreen(state = InviteHandoffState(code = "K7Q2PD"), onAction = {}) }
}
