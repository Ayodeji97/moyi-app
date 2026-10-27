package com.moyi.app.feature.auth.register

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moyi.app.designsystem.MoyiTheme
import com.moyi.app.designsystem.component.ConsentCheckbox
import com.moyi.app.designsystem.component.Field
import com.moyi.app.designsystem.component.FieldType
import com.moyi.app.designsystem.component.InlineError
import com.moyi.app.designsystem.component.MoyiScaffold
import com.moyi.app.designsystem.component.PrimaryAction
import com.moyi.app.designsystem.component.TextLink
import com.moyi.app.designsystem.component.TextLinkStyle
import com.moyi.app.designsystem.presentation.ObserveAsEvents
import com.moyi.app.designsystem.presentation.UiText
import com.moyi.app.feature.auth.common.AuthHeader
import com.moyi.app.feature.auth.common.FieldInput
import com.moyi.app.feature.auth.common.RegisterField
import com.moyi.app.feature.auth.common.SubmitStatus
import com.moyi.app.feature.auth.common.onBlur
import com.moyi.app.feature.auth.common.toActionState
import com.moyi.app.feature.auth.resources.Res
import com.moyi.app.feature.auth.resources.auth_email_label
import com.moyi.app.feature.auth.resources.auth_error_offline
import com.moyi.app.feature.auth.resources.auth_password_label
import com.moyi.app.feature.auth.resources.auth_try_again
import com.moyi.app.feature.auth.resources.register_action
import com.moyi.app.feature.auth.resources.register_action_sending
import com.moyi.app.feature.auth.resources.register_consent
import com.moyi.app.feature.auth.resources.register_email_helper
import com.moyi.app.feature.auth.resources.register_eyebrow
import com.moyi.app.feature.auth.resources.register_have_account
import com.moyi.app.feature.auth.resources.register_heading
import com.moyi.app.feature.auth.resources.register_name_helper
import com.moyi.app.feature.auth.resources.register_name_label
import com.moyi.app.feature.auth.resources.register_password_helper
import com.moyi.app.feature.auth.resources.register_privacy
import com.moyi.app.feature.auth.resources.register_terms
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun RegisterRoot(
    onNavigateToVerify: (email: String) -> Unit,
    onNavigateToLogin: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: RegisterViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is RegisterEvent.NavigateToVerify -> onNavigateToVerify(event.email)
            RegisterEvent.NavigateToLogin -> onNavigateToLogin()
            RegisterEvent.NavigateBack -> onNavigateBack()
        }
    }
    RegisterScreen(state = state, onAction = viewModel::onAction)
}

/**
 * Placed per Figma 340:9 (auth-placement-spec §3.1), with one deliberate
 * deviation (auth-screens-check.md, "Sign up: consent scrolls"). The Name
 * field is new (not in Figma; §7), and with it the consent row and the legal
 * links no longer fit in a pinned block: at rest on an iPhone 17 they hid the
 * password, and at 200 % font they squeezed the scrolling content to nothing.
 * So consent and the links scroll with the fields, 24 after the password, and
 * only the InlineError (when shown), the action and the secondary link stay
 * pinned. Gaps inside the action block stay 16 on this screen (D3).
 */
@Composable
fun RegisterScreen(
    state: RegisterState,
    onAction: (RegisterAction) -> Unit,
) {
    val spacing = MoyiTheme.spacing
    val failed = state.submit as? SubmitStatus.Failed
    MoyiScaffold(
        onBack = { onAction(RegisterAction.OnBackClick) },
        action = {
            if (failed != null) {
                InlineError(failed.message.asString())
                Spacer(Modifier.height(spacing.md))
            }
            PrimaryAction(
                text = stringResource(if (failed?.offerRetry == true) Res.string.auth_try_again else Res.string.register_action),
                onClick = { onAction(RegisterAction.OnSubmit) },
                state = state.submit.toActionState(
                    sendingLabel = stringResource(Res.string.register_action_sending),
                    enabled = state.consent,
                ),
            )
            Spacer(Modifier.height(spacing.md))
            TextLink(
                stringResource(Res.string.register_have_account),
                onClick = { onAction(RegisterAction.OnHaveAccountClick) },
                centered = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) {
        AuthHeader(stringResource(Res.string.register_eyebrow), stringResource(Res.string.register_heading))
        Spacer(Modifier.height(spacing.lg))
        // NEW, not in Figma (docs/missing-components.md §5): label and helper are proposed copy.
        Field(
            value = state.name.value,
            onValueChange = { onAction(RegisterAction.OnNameChange(it)) },
            label = stringResource(Res.string.register_name_label),
            helper = stringResource(Res.string.register_name_helper),
            error = state.name.error?.asString(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            modifier = Modifier
                .semantics { contentType = ContentType.PersonFullName }
                .onBlur { onAction(RegisterAction.OnFieldBlur(RegisterField.Name)) },
        )
        Spacer(Modifier.height(spacing.lg))
        Field(
            value = state.email.value,
            onValueChange = { onAction(RegisterAction.OnEmailChange(it)) },
            label = stringResource(Res.string.auth_email_label),
            helper = stringResource(Res.string.register_email_helper),
            error = state.email.error?.asString(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Next,
            ),
            modifier = Modifier
                .semantics { contentType = ContentType.EmailAddress }
                .onBlur { onAction(RegisterAction.OnFieldBlur(RegisterField.Email)) },
        )
        Spacer(Modifier.height(spacing.lg))
        Field(
            value = state.password.value,
            onValueChange = { onAction(RegisterAction.OnPasswordChange(it)) },
            label = stringResource(Res.string.auth_password_label),
            type = FieldType.Secure,
            helper = stringResource(Res.string.register_password_helper),
            error = state.password.error?.asString(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onAction(RegisterAction.OnSubmit) }),
            modifier = Modifier
                .semantics { contentType = ContentType.NewPassword }
                .onBlur { onAction(RegisterAction.OnFieldBlur(RegisterField.Password)) },
        )
        Spacer(Modifier.height(spacing.lg))
        ConsentCheckbox(
            checked = state.consent,
            onCheckedChange = { onAction(RegisterAction.OnConsentChange(it)) },
            text = stringResource(Res.string.register_consent),
        )
        // The links the consent text points at ("the terms below"), right under it.
        Spacer(Modifier.height(spacing.md))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            TextLink(
                stringResource(Res.string.register_terms),
                onClick = { onAction(RegisterAction.OnLegalLinkClick) },
                style = TextLinkStyle.Body,
            )
            TextLink(
                stringResource(Res.string.register_privacy),
                onClick = { onAction(RegisterAction.OnLegalLinkClick) },
                style = TextLinkStyle.Body,
            )
        }
    }
}

@Preview
@Composable
private fun RegisterScreenIdlePreview() {
    MoyiTheme(darkTheme = true) {
        RegisterScreen(
            state = RegisterState(
                name = FieldInput("Amara"),
                email = FieldInput("amara@example.com"),
                password = FieldInput("correct horse"),
            ),
            onAction = {},
        )
    }
}

@Preview
@Composable
private fun RegisterScreenOfflinePreview() {
    MoyiTheme(darkTheme = true) {
        RegisterScreen(
            state = RegisterState(
                name = FieldInput("Amara"),
                email = FieldInput("amara@example.com"),
                password = FieldInput("correct horse"),
                consent = true,
                submit = SubmitStatus.Failed(UiText.Resource(Res.string.auth_error_offline), offerRetry = true),
            ),
            onAction = {},
        )
    }
}
