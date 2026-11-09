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
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moyi.app.designsystem.MoyiTheme
import com.moyi.app.designsystem.component.Field
import com.moyi.app.designsystem.component.FieldType
import com.moyi.app.designsystem.component.InlineError
import com.moyi.app.designsystem.component.MoyiScaffold
import com.moyi.app.designsystem.component.MoyiText
import com.moyi.app.designsystem.component.PrimaryAction
import com.moyi.app.designsystem.component.ScreenHeader
import com.moyi.app.designsystem.presentation.ObserveAsEvents
import com.moyi.app.designsystem.presentation.UiText
import com.moyi.app.feature.auth.common.FieldInput
import com.moyi.app.feature.auth.common.SubmitStatus
import com.moyi.app.feature.auth.common.onBlur
import com.moyi.app.feature.auth.common.toActionState
import com.moyi.app.feature.auth.resources.Res
import com.moyi.app.feature.auth.resources.auth_password_label
import com.moyi.app.feature.auth.resources.auth_try_again
import com.moyi.app.feature.auth.resources.login_eyebrow
import com.moyi.app.feature.auth.resources.register_password_helper
import com.moyi.app.feature.auth.resources.reset_action
import com.moyi.app.feature.auth.resources.reset_action_sending
import com.moyi.app.feature.auth.resources.reset_consequence
import com.moyi.app.feature.auth.resources.reset_new_heading
import com.moyi.app.feature.auth.resources.reset_token_error_expired
import com.moyi.app.feature.auth.resources.verify_token_helper
import com.moyi.app.feature.auth.resources.verify_token_label
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun ResetPasswordRoot(
    email: String,
    onNavigateToHome: () -> Unit,
    onNavigateToLogin: (email: String) -> Unit,
    onNavigateBack: () -> Unit,
    linkToken: String? = null,
    viewModel: ResetPasswordViewModel = koinViewModel { parametersOf(email, linkToken) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            ResetPasswordEvent.NavigateToHome -> onNavigateToHome()
            is ResetPasswordEvent.NavigateToLogin -> onNavigateToLogin(event.email)
            ResetPasswordEvent.NavigateBack -> onNavigateBack()
        }
    }
    ResetPasswordScreen(state = state, onAction = viewModel::onAction)
}

/**
 * Placed per Figma 345:30 (auth-placement-spec §3.4): heading, then the new
 * password field (with its helper, 115 tall) 24 below it in Figma, then the
 * consequence as body text, then the PrimaryAction alone in the action block.
 *
 * The paste-code field is NEW, not in Figma (docs/missing-components.md §6a):
 * it sits first, 24 under the heading, as on verify, and the password moves
 * down by one field. The consequence comes before the action in the reading
 * order as well as on screen (states.md §1b: announce the consequence before
 * the action, not after).
 */
@Composable
fun ResetPasswordScreen(
    state: ResetPasswordState,
    onAction: (ResetPasswordAction) -> Unit,
) {
    val spacing = MoyiTheme.spacing
    val failed = state.submit as? SubmitStatus.Failed
    MoyiScaffold(
        onBack = { onAction(ResetPasswordAction.OnBackClick) },
        action = {
            if (failed != null) {
                InlineError(failed.message.asString())
                Spacer(Modifier.height(spacing.lg))
            }
            PrimaryAction(
                text = stringResource(if (failed?.offerRetry == true) Res.string.auth_try_again else Res.string.reset_action),
                onClick = { onAction(ResetPasswordAction.OnSubmit) },
                state = state.submit.toActionState(sendingLabel = stringResource(Res.string.reset_action_sending)),
            )
        },
    ) {
        ScreenHeader(stringResource(Res.string.login_eyebrow), stringResource(Res.string.reset_new_heading))
        Spacer(Modifier.height(spacing.lg))
        // NEW, not in Figma (docs/missing-components.md §6a): a stand-in until deep links.
        val autoFocus = rememberAutoFocus()
        Field(
            modifier = Modifier.focusRequester(autoFocus),
            value = state.token.value,
            onValueChange = { onAction(ResetPasswordAction.OnTokenChange(it)) },
            label = stringResource(Res.string.verify_token_label),
            helper = stringResource(Res.string.verify_token_helper),
            error = state.token.error?.asString(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Next,
            ),
        )
        Spacer(Modifier.height(spacing.lg))
        Field(
            value = state.password.value,
            onValueChange = { onAction(ResetPasswordAction.OnPasswordChange(it)) },
            label = stringResource(Res.string.auth_password_label),
            type = FieldType.Secure,
            helper = stringResource(Res.string.register_password_helper),
            error = state.password.error?.asString(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onAction(ResetPasswordAction.OnSubmit) }),
            modifier = Modifier
                .semantics { contentType = ContentType.NewPassword }
                .onBlur { onAction(ResetPasswordAction.OnPasswordBlur) },
        )
        Spacer(Modifier.height(spacing.lg))
        MoyiText(stringResource(Res.string.reset_consequence))
    }
}

@Preview
@Composable
private fun ResetPasswordScreenIdlePreview() {
    MoyiTheme(darkTheme = true) {
        ResetPasswordScreen(state = ResetPasswordState(email = "amara@example.com"), onAction = {})
    }
}

@Preview
@Composable
private fun ResetPasswordScreenExpiredPreview() {
    MoyiTheme(darkTheme = true) {
        ResetPasswordScreen(
            state = ResetPasswordState(
                email = "amara@example.com",
                token = FieldInput("old-code", UiText.Resource(Res.string.reset_token_error_expired), showErrors = true),
                password = FieldInput("correct horse"),
            ),
            onAction = {},
        )
    }
}
