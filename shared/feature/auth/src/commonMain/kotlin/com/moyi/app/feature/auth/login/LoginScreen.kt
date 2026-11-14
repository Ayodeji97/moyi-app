package com.moyi.app.feature.auth.login

import androidx.compose.ui.platform.LocalAutofillManager
import com.moyi.app.feature.auth.common.credentialAutofill
import com.moyi.app.feature.auth.common.settle
import com.moyi.app.designsystem.component.rememberAutoFocus
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moyi.app.designsystem.MoyiTheme
import com.moyi.app.designsystem.component.Field
import com.moyi.app.designsystem.component.FieldAutofill
import com.moyi.app.designsystem.component.FieldType
import com.moyi.app.designsystem.component.InlineError
import com.moyi.app.designsystem.component.MoyiScaffold
import com.moyi.app.designsystem.component.MoyiText
import com.moyi.app.designsystem.component.PrimaryAction
import com.moyi.app.designsystem.component.TextLink
import com.moyi.app.designsystem.component.TextTone
import com.moyi.app.designsystem.presentation.ObserveAsEvents
import com.moyi.app.designsystem.presentation.UiText
import com.moyi.app.designsystem.component.ScreenHeader
import com.moyi.app.feature.auth.common.FieldInput
import com.moyi.app.feature.auth.common.SubmitStatus
import com.moyi.app.feature.auth.common.onBlur
import com.moyi.app.feature.auth.common.toActionState
import com.moyi.app.feature.auth.resources.Res
import com.moyi.app.feature.auth.resources.auth_email_label
import com.moyi.app.feature.auth.resources.auth_password_label
import com.moyi.app.feature.auth.resources.auth_try_again
import com.moyi.app.feature.auth.resources.login_action
import com.moyi.app.feature.auth.resources.login_action_sending
import com.moyi.app.feature.auth.resources.login_create_account
import com.moyi.app.feature.auth.resources.login_error_credentials
import com.moyi.app.feature.auth.resources.login_eyebrow
import com.moyi.app.feature.auth.resources.login_forgot
import com.moyi.app.feature.auth.resources.login_heading
import com.moyi.app.feature.auth.resources.login_session_ended
import com.moyi.app.feature.auth.resources.login_password_changed
import com.moyi.app.feature.auth.resources.login_verified
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun LoginRoot(
    initialEmail: String,
    sessionEnded: Boolean,
    verified: Boolean = false,
    passwordChanged: Boolean = false,
    onNavigateToHome: () -> Unit,
    onNavigateToRegister: () -> Unit,
    onNavigateToForgotPassword: (email: String) -> Unit,
    onBack: (() -> Unit)?,
    viewModel: LoginViewModel = koinViewModel { parametersOf(initialEmail, sessionEnded) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val autofillManager = LocalAutofillManager.current
    ObserveAsEvents(viewModel.events) { event ->
        autofillManager.settle(event.credentialAutofill)
        when (event) {
            LoginEvent.NavigateToHome -> onNavigateToHome()
            LoginEvent.NavigateToRegister -> onNavigateToRegister()
            is LoginEvent.NavigateToForgotPassword -> onNavigateToForgotPassword(event.email)
        }
    }
    LoginScreen(state = state, onAction = viewModel::onAction, onBack = onBack, verified = verified, passwordChanged = passwordChanged)
}

/**
 * Placed per Figma 344:18 (auth-placement-spec §3.3): fields with helpers
 * hidden, 24 apart; "I forgot my password" 24 under the password, in the
 * content; the PrimaryAction and "Create an account" 24 apart in the action
 * block. A failure is an InlineError, the action block's first child, 24 above
 * the button (the derived placement in §3.3, since no frame draws it).
 *
 * [onBack] is null while sign in is the root (the promise screen it backs
 * onto in Figma is not built), so no header is drawn then.
 */
@Composable
fun LoginScreen(
    state: LoginState,
    onAction: (LoginAction) -> Unit,
    onBack: (() -> Unit)? = null,
    verified: Boolean = false,
    passwordChanged: Boolean = false,
) {
    val spacing = MoyiTheme.spacing
    val failed = state.submit as? SubmitStatus.Failed
    MoyiScaffold(
        onBack = onBack,
        action = {
            if (failed != null) {
                InlineError(failed.message.asString())
                Spacer(Modifier.height(spacing.lg))
            }
            PrimaryAction(
                text = stringResource(if (failed?.offerRetry == true) Res.string.auth_try_again else Res.string.login_action),
                onClick = { onAction(LoginAction.OnSubmit) },
                state = state.submit.toActionState(sendingLabel = stringResource(Res.string.login_action_sending)),
            )
            Spacer(Modifier.height(spacing.lg))
            TextLink(
                stringResource(Res.string.login_create_account),
                onClick = { onAction(LoginAction.OnCreateAccountClick) },
                centered = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) {
        ScreenHeader(stringResource(Res.string.login_eyebrow), stringResource(Res.string.login_heading))
        if (state.sessionEnded) {
            // No frame exists for this (auth-placement-spec §7); placed like verify's line, 16 under the heading.
            Spacer(Modifier.height(spacing.md))
            // Announced politely: the person did not ask to be here, so a screen reader says why.
            MoyiText(
                stringResource(Res.string.login_session_ended),
                tone = TextTone.Muted,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        } else if (verified) {
            // Placed as the session-ended line. Without it, sign in after the link looks
            // like the link failed and sent the person back to the start.
            Spacer(Modifier.height(spacing.md))
            MoyiText(
                stringResource(Res.string.login_verified),
                tone = TextTone.Muted,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        } else if (passwordChanged) {
            // Placed as the verified line. Without it, sign in after "Save and sign in" reads
            // as if the new password was not kept.
            Spacer(Modifier.height(spacing.md))
            MoyiText(
                stringResource(Res.string.login_password_changed),
                tone = TextTone.Muted,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        Spacer(Modifier.height(spacing.lg))
        Field(
            value = state.email.value,
            onValueChange = { onAction(LoginAction.OnEmailChange(it)) },
            label = stringResource(Res.string.auth_email_label),
            error = state.email.error?.asString(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Next,
            ),
            autofill = FieldAutofill.AccountEmail,
            modifier = Modifier.onBlur { onAction(LoginAction.OnFieldBlur(LoginField.Email)) },
        )
        Spacer(Modifier.height(spacing.lg))
        // Arriving with the email already filled (after verifying, or a reset), the next
        // thing to type is the password. Decided once, on arrival: typing an email
        // must never pull focus away mid-word.
        val arrivedWithEmail = remember { state.email.value.isNotEmpty() && state.password.value.isEmpty() }
        val autoFocus = rememberAutoFocus(enabled = arrivedWithEmail)
        Field(
            value = state.password.value,
            onValueChange = { onAction(LoginAction.OnPasswordChange(it)) },
            label = stringResource(Res.string.auth_password_label),
            type = FieldType.Secure,
            error = state.password.error?.asString(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onAction(LoginAction.OnSubmit) }),
            autofill = FieldAutofill.Password,
            modifier = Modifier.focusRequester(autoFocus),
        )
        Spacer(Modifier.height(spacing.lg))
        TextLink(
            stringResource(Res.string.login_forgot),
            onClick = { onAction(LoginAction.OnForgotPasswordClick) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Preview
@Composable
private fun LoginScreenIdlePreview() {
    MoyiTheme(darkTheme = true) {
        LoginScreen(state = LoginState(email = FieldInput("amara@example.com"), password = FieldInput("correct horse")), onAction = {})
    }
}

@Preview
@Composable
private fun LoginScreenWrongCredentialsPreview() {
    MoyiTheme(darkTheme = true) {
        LoginScreen(
            state = LoginState(
                email = FieldInput("amara@example.com"),
                password = FieldInput("correct horse"),
                submit = SubmitStatus.Failed(UiText.Resource(Res.string.login_error_credentials), offerRetry = false),
            ),
            onAction = {},
        )
    }
}
