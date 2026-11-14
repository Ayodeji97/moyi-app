package com.moyi.app.feature.auth.reset

import androidx.compose.ui.platform.LocalAutofillManager
import com.moyi.app.feature.auth.common.credentialAutofill
import com.moyi.app.feature.auth.common.settle
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
import com.moyi.app.designsystem.component.ScreenHeader
import com.moyi.app.designsystem.platform.LayerBackHandler
import com.moyi.app.designsystem.presentation.ObserveAsEvents
import com.moyi.app.designsystem.presentation.UiText
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
import com.moyi.app.feature.auth.resources.code_error_invalid
import com.moyi.app.feature.auth.resources.code_helper
import com.moyi.app.feature.auth.resources.reset_link_ready
import com.moyi.app.feature.auth.resources.reset_code_action
import com.moyi.app.feature.auth.resources.reset_code_heading
import com.moyi.app.feature.auth.resources.verify_action_sending
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
    val autofillManager = LocalAutofillManager.current
    ObserveAsEvents(viewModel.events) { event ->
        autofillManager.settle(event.credentialAutofill)
        when (event) {
            ResetPasswordEvent.NavigateToHome -> onNavigateToHome()
            is ResetPasswordEvent.NavigateToLogin -> onNavigateToLogin(event.email)
            ResetPasswordEvent.NavigateBack -> onNavigateBack()
        }
    }
    // System back from the password returns to the code, as the arrow does. Where there is no
    // code to return to (the link opened the app) it steps aside and the NavHost pops.
    LayerBackHandler(enabled = state.step == ResetStep.Password && state.canReturnToCode) {
        viewModel.onAction(ResetPasswordAction.OnBackClick)
    }
    ResetPasswordScreen(state = state, onAction = viewModel::onAction)
}

/**
 * Two steps on one route (backend-contract-gaps.md row 39).
 *
 * **Code** (NEW, docs/missing-components.md §72): the code field alone, 24 under a heading,
 * as on verify, and "Check the code" in the action block. The sixth digit checks it too, and
 * a refusal shows on the field.
 *
 * **Password**: placed per Figma 345:30 (auth-placement-spec §3.4): heading, then the new
 * password field (with its helper, 115 tall), then the consequence as body text, then the
 * PrimaryAction alone in the action block. The consequence comes before the action in the
 * reading order as well as on screen (states.md §1b: announce the consequence before the
 * action, not after). The code field is gone from this step: it was already accepted.
 */
@Composable
fun ResetPasswordScreen(
    state: ResetPasswordState,
    onAction: (ResetPasswordAction) -> Unit,
) {
    val spacing = MoyiTheme.spacing
    val failed = state.submit as? SubmitStatus.Failed
    val onCodeStep = state.step == ResetStep.Code
    val codeFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }
    // Each step opens with the keyboard on its own field.
    LaunchedEffect(state.step) {
        if (state.step == ResetStep.Code) codeFocus.requestFocus() else passwordFocus.requestFocus()
    }
    MoyiScaffold(
        onBack = { onAction(ResetPasswordAction.OnBackClick) },
        action = {
            if (failed != null) {
                InlineError(failed.message.asString())
                Spacer(Modifier.height(spacing.lg))
            }
            val idleLabel = if (onCodeStep) Res.string.reset_code_action else Res.string.reset_action
            val sendingLabel = if (onCodeStep) Res.string.verify_action_sending else Res.string.reset_action_sending
            PrimaryAction(
                text = stringResource(if (failed?.offerRetry == true) Res.string.auth_try_again else idleLabel),
                onClick = { onAction(ResetPasswordAction.OnSubmit) },
                state = state.submit.toActionState(sendingLabel = stringResource(sendingLabel)),
            )
        },
    ) {
        ScreenHeader(
            stringResource(Res.string.login_eyebrow),
            stringResource(if (onCodeStep) Res.string.reset_code_heading else Res.string.reset_new_heading),
        )
        Spacer(Modifier.height(spacing.lg))
        if (onCodeStep) {
            // NEW, not in Figma (docs/missing-components.md §58): six digits, or the link pasted.
            EmailCodeField(
                modifier = Modifier.focusRequester(codeFocus),
                value = state.code.value,
                onValueChange = { onAction(ResetPasswordAction.OnCodeChange(it)) },
                helper = stringResource(Res.string.code_helper),
                error = state.code.error?.asString(),
                imeAction = ImeAction.Done,
                onImeAction = { onAction(ResetPasswordAction.OnSubmit) },
            )
        } else {
            if (state.linkToken != null) {
                MoyiText(stringResource(Res.string.reset_link_ready))
                Spacer(Modifier.height(spacing.lg))
            }
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
                autofill = FieldAutofill.NewPassword,
                modifier = Modifier
                    .focusRequester(passwordFocus)
                    .onBlur { onAction(ResetPasswordAction.OnPasswordBlur) },
            )
            Spacer(Modifier.height(spacing.lg))
            MoyiText(stringResource(Res.string.reset_consequence))
        }
    }
}

@Preview
@Composable
private fun ResetPasswordScreenCodePreview() {
    MoyiTheme(darkTheme = true) {
        ResetPasswordScreen(state = ResetPasswordState(email = "amara@example.com"), onAction = {})
    }
}

@Preview
@Composable
private fun ResetPasswordScreenCodeRefusedPreview() {
    MoyiTheme(darkTheme = true) {
        ResetPasswordScreen(
            state = ResetPasswordState(
                email = "amara@example.com",
                code = FieldInput("123456", UiText.Resource(Res.string.code_error_invalid), showErrors = true),
            ),
            onAction = {},
        )
    }
}

@Preview
@Composable
private fun ResetPasswordScreenPasswordPreview() {
    MoyiTheme(darkTheme = true) {
        ResetPasswordScreen(
            state = ResetPasswordState(email = "amara@example.com", code = FieldInput("123456"), step = ResetStep.Password),
            onAction = {},
        )
    }
}
