package com.moyi.app.feature.auth.reset

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
import com.moyi.app.designsystem.component.MoyiText
import com.moyi.app.designsystem.component.PrimaryAction
import com.moyi.app.designsystem.component.ScreenHeader
import com.moyi.app.designsystem.component.TextTone
import com.moyi.app.designsystem.presentation.ObserveAsEvents
import com.moyi.app.designsystem.presentation.UiText
import com.moyi.app.feature.auth.common.SubmitStatus
import com.moyi.app.feature.auth.common.toActionState
import com.moyi.app.feature.auth.resources.Res
import com.moyi.app.feature.auth.resources.auth_error_offline
import com.moyi.app.feature.auth.resources.auth_try_again
import com.moyi.app.feature.auth.resources.change_password_consequence
import com.moyi.app.feature.auth.resources.change_password_heading
import com.moyi.app.feature.auth.resources.change_password_line
import com.moyi.app.feature.auth.resources.login_eyebrow
import com.moyi.app.feature.auth.resources.reset_request_action
import com.moyi.app.feature.auth.resources.reset_request_action_sending
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ChangePasswordRoot(
    onNavigateToCode: (email: String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: ChangePasswordViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is ChangePasswordEvent.NavigateToCode -> onNavigateToCode(event.email)
            ChangePasswordEvent.NavigateBack -> onNavigateBack()
        }
    }
    ChangePasswordScreen(state = state, onAction = viewModel::onAction)
}

/**
 * Change password (NEW, not in Figma: docs/missing-components.md §83), laid out as Forgot
 * password (Figma 344:41) without its field, since the address is known: the eyebrow and
 * heading, then the line saying where the code goes, 16 under the heading, then the
 * consequence 24 under that, then the PrimaryAction alone in the action block. The
 * consequence (every other device is signed out, this one stays) comes before the action,
 * on screen and in reading order (states.md §1b). A failure is an InlineError 24 above the
 * action, as on Forgot password.
 */
@Composable
fun ChangePasswordScreen(
    state: ChangePasswordState,
    onAction: (ChangePasswordAction) -> Unit,
) {
    val spacing = MoyiTheme.spacing
    val failed = state.submit as? SubmitStatus.Failed
    MoyiScaffold(
        onBack = { onAction(ChangePasswordAction.OnBackClick) },
        action = {
            if (failed != null) {
                InlineError(failed.message.asString())
                Spacer(Modifier.height(spacing.lg))
            }
            PrimaryAction(
                text = stringResource(if (failed?.offerRetry == true) Res.string.auth_try_again else Res.string.reset_request_action),
                onClick = { onAction(ChangePasswordAction.OnSubmit) },
                state = state.submit.toActionState(sendingLabel = stringResource(Res.string.reset_request_action_sending)),
            )
        },
    ) {
        ScreenHeader(stringResource(Res.string.login_eyebrow), stringResource(Res.string.change_password_heading))
        if (state.email != null) {
            Spacer(Modifier.height(spacing.md))
            MoyiText(stringResource(Res.string.change_password_line, state.email), tone = TextTone.Muted)
        }
        Spacer(Modifier.height(spacing.lg))
        MoyiText(stringResource(Res.string.change_password_consequence))
    }
}

@Preview
@Composable
private fun ChangePasswordScreenPreview() {
    MoyiTheme(darkTheme = true) {
        ChangePasswordScreen(state = ChangePasswordState(email = "amara@example.com"), onAction = {})
    }
}

@Preview
@Composable
private fun ChangePasswordScreenOfflinePreview() {
    MoyiTheme(darkTheme = true) {
        ChangePasswordScreen(
            state = ChangePasswordState(
                email = "amara@example.com",
                submit = SubmitStatus.Failed(UiText.Resource(Res.string.auth_error_offline), offerRetry = true),
            ),
            onAction = {},
        )
    }
}
