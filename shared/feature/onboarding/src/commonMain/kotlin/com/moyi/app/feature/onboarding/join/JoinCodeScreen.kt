package com.moyi.app.feature.onboarding.join

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moyi.app.designsystem.MoyiTheme
import com.moyi.app.designsystem.component.Field
import com.moyi.app.designsystem.component.FieldType
import com.moyi.app.designsystem.component.MoyiScaffold
import com.moyi.app.designsystem.component.MoyiText
import com.moyi.app.designsystem.component.PrimaryAction
import com.moyi.app.designsystem.component.TextTone
import com.moyi.app.designsystem.presentation.ObserveAsEvents
import com.moyi.app.designsystem.presentation.UiText
import com.moyi.app.feature.onboarding.common.OnboardingHeader
import com.moyi.app.feature.onboarding.resources.Res
import com.moyi.app.feature.onboarding.resources.field_code_error_character
import com.moyi.app.feature.onboarding.resources.field_code_helper
import com.moyi.app.feature.onboarding.resources.field_code_label
import com.moyi.app.feature.onboarding.resources.join_code_action
import com.moyi.app.feature.onboarding.resources.join_code_heading_manual
import com.moyi.app.feature.onboarding.resources.join_code_heading_prefilled
import com.moyi.app.feature.onboarding.resources.join_code_line_manual
import com.moyi.app.feature.onboarding.resources.join_code_line_prefilled
import com.moyi.app.feature.onboarding.resources.join_eyebrow
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun JoinCodeRoot(
    prefilledCode: String?,
    onNavigateToConfirm: (code: String) -> Unit,
    onNavigateToCannotBeUsed: () -> Unit,
    onNavigateBack: (() -> Unit)?,
    viewModel: JoinCodeViewModel = koinViewModel { parametersOf(prefilledCode) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is JoinCodeEvent.NavigateToConfirm -> onNavigateToConfirm(event.code)
            JoinCodeEvent.NavigateToCannotBeUsed -> onNavigateToCannotBeUsed()
            JoinCodeEvent.NavigateBack -> onNavigateBack?.invoke()
        }
    }
    JoinCodeScreen(state = state, onAction = viewModel::onAction, showBack = onNavigateBack != null)
}

/**
 * Join's code screen (spec §3.5, §3.6).
 *
 * - **Prefilled (308:4)**, a root: eyebrow at 24, heading at 107 (64 below),
 *   then the line and the Code field as siblings 16 apart. No back: it is
 *   reached by deep link from a cold install.
 * - **Manual (309:42)**, drawn keyboard-up: back, then eyebrow, heading,
 *   line and field 16 apart throughout. The field takes focus on arrival, so
 *   the keyboard is up as drawn.
 *
 * The action block holds only "Continue" (772, or on the keyboard).
 */
@Composable
fun JoinCodeScreen(
    state: JoinCodeState,
    onAction: (JoinCodeAction) -> Unit,
    showBack: Boolean = !state.prefilled,
) {
    val spacing = MoyiTheme.spacing
    val focus = remember { FocusRequester() }
    if (!state.prefilled) {
        LaunchedEffect(Unit) { focus.requestFocus() }
    }
    MoyiScaffold(
        onBack = if (showBack) ({ onAction(JoinCodeAction.OnBackClick) }) else null,
        action = {
            PrimaryAction(stringResource(Res.string.join_code_action), onClick = { onAction(JoinCodeAction.OnContinueClick) })
        },
    ) {
        OnboardingHeader(
            eyebrow = stringResource(Res.string.join_eyebrow),
            heading = stringResource(
                if (state.prefilled) Res.string.join_code_heading_prefilled else Res.string.join_code_heading_manual,
            ),
            gap = if (state.prefilled) spacing.xxxl else spacing.md,
        )
        Spacer(Modifier.height(spacing.md))
        MoyiText(
            stringResource(if (state.prefilled) Res.string.join_code_line_prefilled else Res.string.join_code_line_manual),
            tone = TextTone.Muted,
        )
        Spacer(Modifier.height(spacing.md))
        Field(
            value = state.code,
            onValueChange = { onAction(JoinCodeAction.OnCodeChange(it)) },
            label = stringResource(Res.string.field_code_label),
            type = FieldType.Code,
            helper = stringResource(Res.string.field_code_helper),
            error = state.error?.asString(),
            modifier = Modifier.focusRequester(focus),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onAction(JoinCodeAction.OnContinueClick) }),
        )
    }
}

@Preview
@Composable
private fun JoinCodePrefilledPreview() {
    MoyiTheme(darkTheme = true) { JoinCodeScreen(state = JoinCodeState(prefilled = true, code = "K7Q2PD"), onAction = {}) }
}

@Preview(heightDp = 544)
@Composable
private fun JoinCodeManualKeyboardUpPreview() {
    MoyiTheme(darkTheme = true) { JoinCodeScreen(state = JoinCodeState(prefilled = false, code = "K7Q"), onAction = {}) }
}

@Preview
@Composable
private fun JoinCodeCharacterErrorPreview() {
    MoyiTheme(darkTheme = true) {
        JoinCodeScreen(
            state = JoinCodeState(
                prefilled = false,
                code = "K7Q20",
                error = UiText.Resource(Res.string.field_code_error_character),
            ),
            onAction = {},
        )
    }
}

@Preview
@Composable
private fun JoinCodeLightPreview() {
    MoyiTheme(darkTheme = false) { JoinCodeScreen(state = JoinCodeState(prefilled = true, code = "K7Q2PD"), onAction = {}) }
}
