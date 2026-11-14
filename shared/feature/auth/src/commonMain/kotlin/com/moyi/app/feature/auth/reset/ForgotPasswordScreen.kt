package com.moyi.app.feature.auth.reset

import com.moyi.app.designsystem.component.rememberAutoFocus
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moyi.app.designsystem.MoyiTheme
import com.moyi.app.designsystem.component.Field
import com.moyi.app.designsystem.component.FieldAutofill
import com.moyi.app.designsystem.component.InlineError
import com.moyi.app.designsystem.component.MoyiScaffold
import com.moyi.app.designsystem.component.MoyiText
import com.moyi.app.designsystem.component.PrimaryAction
import com.moyi.app.designsystem.component.ScreenHeader
import com.moyi.app.designsystem.component.TextTone
import com.moyi.app.designsystem.presentation.ObserveAsEvents
import com.moyi.app.designsystem.presentation.UiText
import com.moyi.app.feature.auth.common.FieldInput
import com.moyi.app.feature.auth.common.SubmitStatus
import com.moyi.app.feature.auth.common.onBlur
import com.moyi.app.feature.auth.common.toActionState
import com.moyi.app.feature.auth.resources.Res
import com.moyi.app.feature.auth.resources.auth_email_label
import com.moyi.app.feature.auth.resources.auth_error_offline
import com.moyi.app.feature.auth.resources.auth_try_again
import com.moyi.app.feature.auth.resources.login_eyebrow
import com.moyi.app.feature.auth.resources.reset_request_action
import com.moyi.app.feature.auth.resources.reset_request_action_sending
import com.moyi.app.feature.auth.resources.reset_request_heading
import com.moyi.app.feature.auth.resources.reset_request_line
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun ForgotPasswordRoot(
    initialEmail: String,
    onNavigateToLinkSent: (email: String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: ForgotPasswordViewModel = koinViewModel { parametersOf(initialEmail) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is ForgotPasswordEvent.NavigateToLinkSent -> onNavigateToLinkSent(event.email)
            ForgotPasswordEvent.NavigateBack -> onNavigateBack()
        }
    }
    ForgotPasswordScreen(state = state, onAction = viewModel::onAction)
}

/**
 * Placed per Figma 344:41 (auth-placement-spec §3.4): heading and a two-line
 * body as one block, 16 apart like verify's; the email field with its helper
 * hidden, 24 under the body; the PrimaryAction alone in the action block. A
 * failure is an InlineError 24 above the action, as on sign in.
 */
@Composable
fun ForgotPasswordScreen(
    state: ForgotPasswordState,
    onAction: (ForgotPasswordAction) -> Unit,
) {
    val spacing = MoyiTheme.spacing
    val failed = state.submit as? SubmitStatus.Failed
    MoyiScaffold(
        onBack = { onAction(ForgotPasswordAction.OnBackClick) },
        action = {
            if (failed != null) {
                InlineError(failed.message.asString())
                Spacer(Modifier.height(spacing.lg))
            }
            PrimaryAction(
                text = stringResource(if (failed?.offerRetry == true) Res.string.auth_try_again else Res.string.reset_request_action),
                onClick = { onAction(ForgotPasswordAction.OnSubmit) },
                state = state.submit.toActionState(sendingLabel = stringResource(Res.string.reset_request_action_sending)),
            )
        },
    ) {
        ScreenHeader(stringResource(Res.string.login_eyebrow), stringResource(Res.string.reset_request_heading))
        Spacer(Modifier.height(spacing.md))
        MoyiText(stringResource(Res.string.reset_request_line), tone = TextTone.Muted)
        Spacer(Modifier.height(spacing.lg))
        val autoFocus = rememberAutoFocus()
        Field(
            value = state.email.value,
            onValueChange = { onAction(ForgotPasswordAction.OnEmailChange(it)) },
            label = stringResource(Res.string.auth_email_label),
            error = state.email.error?.asString(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Send,
            ),
            keyboardActions = KeyboardActions(onSend = { onAction(ForgotPasswordAction.OnSubmit) }),
            autofill = FieldAutofill.AccountEmail,
            modifier = Modifier.focusRequester(autoFocus)
                .onBlur { onAction(ForgotPasswordAction.OnEmailBlur) },
        )
    }
}

@Preview
@Composable
private fun ForgotPasswordScreenIdlePreview() {
    MoyiTheme(darkTheme = true) {
        ForgotPasswordScreen(state = ForgotPasswordState(email = FieldInput("amara@example.com")), onAction = {})
    }
}

@Preview
@Composable
private fun ForgotPasswordScreenOfflinePreview() {
    MoyiTheme(darkTheme = true) {
        ForgotPasswordScreen(
            state = ForgotPasswordState(
                email = FieldInput("amara@example.com"),
                submit = SubmitStatus.Failed(UiText.Resource(Res.string.auth_error_offline), offerRetry = true),
            ),
            onAction = {},
        )
    }
}
