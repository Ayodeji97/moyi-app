package com.moyi.app.feature.auth.verify

import com.moyi.app.designsystem.component.rememberAutoFocus
import androidx.compose.ui.focus.focusRequester
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moyi.app.designsystem.MoyiTheme
import com.moyi.app.designsystem.component.InlineError
import com.moyi.app.designsystem.component.MoyiScaffold
import com.moyi.app.designsystem.component.MoyiText
import com.moyi.app.designsystem.component.PrimaryAction
import com.moyi.app.designsystem.component.TextLink
import com.moyi.app.designsystem.component.TextTone
import com.moyi.app.designsystem.presentation.ObserveAsEvents
import com.moyi.app.designsystem.presentation.UiText
import com.moyi.app.designsystem.component.ScreenHeader
import com.moyi.app.feature.auth.common.EmailCodeField
import com.moyi.app.feature.auth.common.FieldInput
import com.moyi.app.feature.auth.common.SubmitStatus
import com.moyi.app.feature.auth.common.toActionState
import com.moyi.app.feature.auth.resources.Res
import com.moyi.app.feature.auth.resources.code_error_invalid
import com.moyi.app.feature.auth.resources.code_helper
import com.moyi.app.feature.auth.resources.login_action
import com.moyi.app.feature.auth.resources.verify_action
import com.moyi.app.feature.auth.resources.verify_action_sending
import com.moyi.app.feature.auth.resources.verify_change_email
import com.moyi.app.feature.auth.resources.verify_expired_action
import com.moyi.app.feature.auth.resources.verify_expired_action_sending
import com.moyi.app.feature.auth.resources.verify_expired_heading
import com.moyi.app.feature.auth.resources.verify_expired_line
import com.moyi.app.feature.auth.resources.verify_expired_line_no_address
import com.moyi.app.feature.auth.resources.verify_eyebrow
import com.moyi.app.feature.auth.resources.verify_heading
import com.moyi.app.feature.auth.resources.verify_line
import com.moyi.app.feature.auth.resources.verify_line_no_address
import com.moyi.app.feature.auth.resources.verify_resend
import com.moyi.app.feature.auth.resources.verify_resent
import com.moyi.app.designsystem.theme.MoyiDimens
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun VerifyRoot(
    email: String,
    onNavigateToLogin: (email: String) -> Unit,
    onNavigateToSignIn: () -> Unit,
    onNavigateBack: () -> Unit,
    linkToken: String? = null,
    viewModel: VerifyViewModel = koinViewModel { parametersOf(email, linkToken) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is VerifyEvent.NavigateToLogin -> onNavigateToLogin(event.email)
            VerifyEvent.NavigateToSignIn -> onNavigateToSignIn()
            VerifyEvent.NavigateBack -> onNavigateBack()
        }
    }
    VerifyScreen(state = state, onAction = viewModel::onAction)
}

/**
 * Placed per Figma 341:17 (auth-placement-spec §3.2): heading and line as one
 * block 16 apart, then the code field (NEW, §58: 24 below the line), then
 * "Send it again" 24 below. The action block holds the PrimaryAction and
 * "Use a different address" 24 apart. Expired (362:107) swaps the copy, hides
 * the resend link and, here, the paste field: the old code can no longer work.
 */
@Composable
fun VerifyScreen(
    state: VerifyState,
    onAction: (VerifyAction) -> Unit,
) {
    val spacing = MoyiTheme.spacing
    val expired = state.mode == VerifyMode.Expired
    val failed = state.submit as? SubmitStatus.Failed
    val actionError = failed?.message ?: state.resendFailure
    MoyiScaffold(
        onBack = { onAction(VerifyAction.OnBackClick) },
        action = {
            if (actionError != null) {
                InlineError(actionError.asString())
                Spacer(Modifier.height(spacing.lg))
            }
            // Expired with no address to resend to: sign in, where the gate sends a new link.
            val signInInstead = expired && !state.addressKnown
            PrimaryAction(
                text = stringResource(
                    when {
                        signInInstead -> Res.string.login_action
                        expired -> Res.string.verify_expired_action
                        else -> Res.string.verify_action
                    },
                ),
                onClick = {
                    onAction(
                        when {
                            signInInstead -> VerifyAction.OnSignInClick
                            expired -> VerifyAction.OnResendClick
                            else -> VerifyAction.OnSubmit
                        },
                    )
                },
                state = state.submit.toActionState(
                    sendingLabel = stringResource(
                        if (expired) Res.string.verify_expired_action_sending else Res.string.verify_action_sending,
                    ),
                    // A link sent in the last minute holds "Send a new link" too (the 1/min limit).
                    enabled = !(expired && state.resend == ResendStatus.Sent),
                ),
            )
            if (state.addressKnown) {
                Spacer(Modifier.height(spacing.lg))
                TextLink(
                    stringResource(Res.string.verify_change_email),
                    onClick = { onAction(VerifyAction.OnChangeEmailClick) },
                    centered = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    ) {
        ScreenHeader(
            eyebrow = stringResource(Res.string.verify_eyebrow),
            heading = stringResource(if (expired) Res.string.verify_expired_heading else Res.string.verify_heading),
        )
        Spacer(Modifier.height(spacing.md))
        MoyiText(
            text = when {
                expired && state.addressKnown -> stringResource(Res.string.verify_expired_line)
                expired -> stringResource(Res.string.verify_expired_line_no_address)
                state.addressKnown -> stringResource(Res.string.verify_line, state.email)
                else -> stringResource(Res.string.verify_line_no_address)
            },
            tone = TextTone.Muted,
        )
        if (expired && state.resend == ResendStatus.Sent) {
            // Says why "Send a new link" is waiting, so the held button is not a silent no-op.
            Spacer(Modifier.height(spacing.lg))
            ResentAcknowledgement()
        }
        if (!expired) {
            Spacer(Modifier.height(spacing.lg))
            // NEW, not in Figma (docs/missing-components.md §58): six digits, or the whole link pasted.
            val autoFocus = rememberAutoFocus()
            EmailCodeField(
                modifier = Modifier.focusRequester(autoFocus),
                value = state.code.value,
                onValueChange = { onAction(VerifyAction.OnCodeChange(it)) },
                helper = stringResource(Res.string.code_helper),
                error = state.code.error?.asString(),
                imeAction = ImeAction.Done,
                onImeAction = { onAction(VerifyAction.OnSubmit) },
            )
            if (state.addressKnown) Spacer(Modifier.height(spacing.lg))
            if (state.addressKnown) when (state.resend) {
                ResendStatus.Sent -> ResentAcknowledgement()
                ResendStatus.Ready, ResendStatus.Sending -> TextLink(
                    stringResource(Res.string.verify_resend),
                    onClick = { onAction(VerifyAction.OnResendClick) },
                    // Held while a check is in flight (VerifyViewModel.resend).
                    enabled = state.resend == ResendStatus.Ready && state.submit != SubmitStatus.Sending,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * "Sent again. You can ask for another in a minute." It replaces a control the
 * person just used, so a screen reader announces it politely instead of
 * leaving focus on nothing.
 */
@Composable
private fun ResentAcknowledgement() {
    MoyiText(
        text = stringResource(Res.string.verify_resent),
        tone = TextTone.Muted,
        // The acknowledgement takes the link's 48 so nothing below jumps.
        modifier = Modifier
            .heightIn(min = MoyiDimens.TouchTarget)
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Preview
@Composable
private fun VerifyScreenWaitingPreview() {
    MoyiTheme(darkTheme = true) {
        VerifyScreen(state = VerifyState(email = "amara@example.com"), onAction = {})
    }
}

@Preview
@Composable
private fun VerifyScreenCodeRefusedPreview() {
    MoyiTheme(darkTheme = true) {
        VerifyScreen(
            state = VerifyState(
                email = "amara@example.com",
                code = FieldInput("482913", UiText.Resource(Res.string.code_error_invalid), showErrors = true),
            ),
            onAction = {},
        )
    }
}

@Preview
@Composable
private fun VerifyScreenExpiredPreview() {
    MoyiTheme(darkTheme = true) {
        VerifyScreen(state = VerifyState(email = "amara@example.com", mode = VerifyMode.Expired), onAction = {})
    }
}

/** Opened by the emailed link on a phone that does not know the address, and the link had expired. */
@Preview
@Composable
private fun VerifyScreenExpiredFromLinkPreview() {
    MoyiTheme(darkTheme = true) {
        VerifyScreen(state = VerifyState(email = "", mode = VerifyMode.Expired), onAction = {})
    }
}
