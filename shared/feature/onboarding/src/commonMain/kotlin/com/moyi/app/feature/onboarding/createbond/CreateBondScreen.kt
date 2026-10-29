package com.moyi.app.feature.onboarding.createbond

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moyi.app.designsystem.MoyiTheme
import com.moyi.app.designsystem.component.Field
import com.moyi.app.designsystem.component.FieldType
import com.moyi.app.designsystem.component.InlineError
import com.moyi.app.designsystem.component.MoyiScaffold
import com.moyi.app.designsystem.component.PrimaryAction
import com.moyi.app.designsystem.component.PrimaryActionState
import com.moyi.app.designsystem.presentation.ObserveAsEvents
import com.moyi.app.designsystem.component.ScreenHeader
import com.moyi.app.feature.onboarding.common.RelationshipType
import com.moyi.app.feature.onboarding.resources.Res
import com.moyi.app.feature.onboarding.resources.bond_create_action
import com.moyi.app.feature.onboarding.resources.bond_create_action_sending
import com.moyi.app.feature.onboarding.resources.bond_create_error
import com.moyi.app.feature.onboarding.resources.bond_create_heading
import com.moyi.app.feature.onboarding.resources.bond_name_helper
import com.moyi.app.feature.onboarding.resources.bond_name_label
import com.moyi.app.feature.onboarding.resources.field_timezone_helper
import com.moyi.app.feature.onboarding.resources.field_timezone_label
import com.moyi.app.feature.onboarding.resources.onboarding_eyebrow_new_bond
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun CreateBondRoot(
    type: RelationshipType,
    onNavigateToInvite: (code: String, link: String) -> Unit,
    onNavigateToGate: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: CreateBondViewModel = koinViewModel { parametersOf(type) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is CreateBondEvent.NavigateToInvite -> onNavigateToInvite(event.code, event.link)
            CreateBondEvent.NavigateToGate -> onNavigateToGate()
            CreateBondEvent.NavigateBack -> onNavigateBack()
        }
    }
    CreateBondScreen(state = state, onAction = viewModel::onAction)
}

/**
 * Onboarding 5 (Figma 342:17; spec §3.3). Back, then the heading with no
 * line under it (at 96: no eyebrow under a back target, figma-deviations.md D1), then the bond name (228) and the shared time zone (367),
 * 24 apart. The action block holds only "Create bond", at 772.
 *
 * Failed (394:1441): the error sits 8 above the button, inside the action
 * block, so the button does not move and the fields keep their values.
 * Keyboard up (362:1196): the scaffold scrolls the heading away and sits the
 * action on the keyboard. No reveal time: it lives in Bond settings.
 */
@Composable
fun CreateBondScreen(
    state: CreateBondState,
    onAction: (CreateBondAction) -> Unit,
) {
    val spacing = MoyiTheme.spacing
    MoyiScaffold(
        onBack = { onAction(CreateBondAction.OnBackClick) },
        action = {
            if (state.status == CreateBondStatus.Failed) {
                InlineError(state.error?.asString() ?: stringResource(Res.string.bond_create_error))
                Spacer(Modifier.height(spacing.sm))
            }
            PrimaryAction(
                text = stringResource(Res.string.bond_create_action),
                onClick = { onAction(CreateBondAction.OnCreateClick) },
                state = if (state.status == CreateBondStatus.Sending) {
                    PrimaryActionState.Sending(stringResource(Res.string.bond_create_action_sending))
                } else {
                    PrimaryActionState.Idle
                },
            )
        },
    ) {
        ScreenHeader(
            eyebrow = stringResource(Res.string.onboarding_eyebrow_new_bond),
            heading = stringResource(Res.string.bond_create_heading),
        )
        Spacer(Modifier.height(spacing.lg))
        Field(
            value = state.name,
            onValueChange = { onAction(CreateBondAction.OnNameChange(it)) },
            label = stringResource(Res.string.bond_name_label),
            helper = stringResource(Res.string.bond_name_helper),
            error = state.nameError?.asString(),
            keyboardOptions = KeyboardOptions(
                // Sentences, not Words: "Amara and Tunde", never "Amara And Tunde".
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onAction(CreateBondAction.OnCreateClick) }),
        )
        Spacer(Modifier.height(spacing.lg))
        Field(
            value = state.timeZoneCity,
            onValueChange = {},
            label = stringResource(Res.string.field_timezone_label),
            type = FieldType.Select,
            helper = stringResource(Res.string.field_timezone_helper, state.timeZoneCity),
            onClick = { onAction(CreateBondAction.OnTimeZoneClick) },
        )
    }
}

private val previewState = CreateBondState(type = RelationshipType.Couple, timeZoneCity = "Lagos")

@Preview
@Composable
private fun CreateBondIdlePreview() {
    MoyiTheme(darkTheme = true) { CreateBondScreen(state = previewState, onAction = {}) }
}

@Preview
@Composable
private fun CreateBondFailedPreview() {
    MoyiTheme(darkTheme = true) {
        CreateBondScreen(state = previewState.copy(name = "Amara and Tunde", status = CreateBondStatus.Failed), onAction = {})
    }
}

@Preview
@Composable
private fun CreateBondSendingPreview() {
    MoyiTheme(darkTheme = true) {
        CreateBondScreen(state = previewState.copy(name = "Amara and Tunde", status = CreateBondStatus.Sending), onAction = {})
    }
}

/**
 * Keyboard up (362:1196) cannot be drawn by a preview: the IME is the OS's.
 * This preview fixes the height to the 544 above Figma's keyboard, which is
 * the space the scaffold gets while it is up, so the scrolled layout shows.
 */
@Preview(heightDp = 544)
@Composable
private fun CreateBondKeyboardUpPreview() {
    MoyiTheme(darkTheme = true) {
        CreateBondScreen(state = previewState.copy(name = "Amara and"), onAction = {})
    }
}

@Preview
@Composable
private fun CreateBondLightPreview() {
    MoyiTheme(darkTheme = false) { CreateBondScreen(state = previewState, onAction = {}) }
}
