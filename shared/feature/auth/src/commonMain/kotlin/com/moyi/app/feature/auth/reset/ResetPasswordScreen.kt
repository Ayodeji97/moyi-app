package com.moyi.app.feature.auth.reset

import com.moyi.app.designsystem.component.rememberAutoFocus
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
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
import com.moyi.app.feature.auth.common.EMAIL_CODE_LENGTH
import com.moyi.app.feature.auth.common.EmailCodeField
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
import com.moyi.app.feature.auth.resources.code_helper
import com.moyi.app.feature.auth.resources.reset_link_ready
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
 * The code field is NEW, not in Figma (docs/missing-components.md §58): it
 * sits first, 24 under the heading, as on verify, and the password moves down
 * by one field. The consequence comes before the action in the reading
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
        // NEW, not in Figma (docs/missing-components.md §58): six digits, or the link pasted.
        // An opened link is the proof already, so the keyboard goes to the password instead.
        val passwordFocus = remember { FocusRequester() }
        val autoFocus = rememberAutoFocus(enabled = state.linkToken == null)
        EmailCodeField(
            modifier = Modifier.focusRequester(autoFocus),
            value = state.code.value,
            onValueChange = { onAction(ResetPasswordAction.OnCodeChange(it)) },
            helper = stringResource(if (state.linkToken != null) Res.string.reset_link_ready else Res.string.code_helper),
            error = state.code.error?.asString(),
            imeAction = ImeAction.Next,
            onImeAction = { passwordFocus.requestFocus() },
        )
        // The sixth digit moves on to the password: there is nothing to check yet (the server
        // checks a reset code only with the new password), and the next step is typing it.
        val codeComplete = state.code.value.length == EMAIL_CODE_LENGTH && state.code.error == null
        LaunchedEffect(codeComplete) {
            if (codeComplete) passwordFocus.requestFocus()
        }
        LaunchedEffect(state.linkToken != null) {
            if (state.linkToken != null && state.password.value.isEmpty()) passwordFocus.requestFocus()
        }
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
                .focusRequester(passwordFocus)
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
                code = FieldInput("", UiText.Resource(Res.string.reset_token_error_expired), showErrors = true),
                password = FieldInput("correct horse"),
            ),
            onAction = {},
        )
    }
}
