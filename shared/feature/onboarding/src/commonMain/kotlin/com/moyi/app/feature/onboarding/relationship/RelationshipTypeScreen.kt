package com.moyi.app.feature.onboarding.relationship

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moyi.app.designsystem.MoyiTheme
import com.moyi.app.designsystem.component.MoyiScaffold
import com.moyi.app.designsystem.component.MoyiText
import com.moyi.app.designsystem.component.TextLink
import com.moyi.app.designsystem.component.TextRole
import com.moyi.app.designsystem.component.TextTone
import com.moyi.app.designsystem.icon.MoyiIcons
import com.moyi.app.designsystem.presentation.ObserveAsEvents
import com.moyi.app.designsystem.theme.MoyiDimens
import com.moyi.app.designsystem.component.ScreenHeader
import com.moyi.app.feature.onboarding.common.RelationshipType
import com.moyi.app.feature.onboarding.resources.Res
import com.moyi.app.feature.onboarding.resources.bond_type_card_hint
import com.moyi.app.feature.onboarding.resources.bond_type_have_code
import com.moyi.app.feature.onboarding.resources.bond_type_heading
import com.moyi.app.feature.onboarding.resources.bond_type_line
import com.moyi.app.feature.onboarding.resources.onboarding_eyebrow_new_bond
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun RelationshipTypeRoot(
    onNavigateToCreateBond: (RelationshipType) -> Unit,
    onNavigateToJoin: () -> Unit,
    onNavigateBack: (() -> Unit)?,
    viewModel: RelationshipTypeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is RelationshipTypeEvent.NavigateToCreateBond -> onNavigateToCreateBond(event.type)
            RelationshipTypeEvent.NavigateBack -> onNavigateBack?.invoke()
            RelationshipTypeEvent.NavigateToJoin -> onNavigateToJoin()
        }
    }
    RelationshipTypeScreen(state = state, onAction = viewModel::onAction, showBack = onNavigateBack != null)
}

/**
 * Onboarding 4 (Figma 341:24 / 359:79; spec §3.2). Under the back target the
 * heading at 96 with no eyebrow (figma-deviations.md D1), the line 16 below, then three cards
 * 358×81 starting 48 under the line (the card group's 24 plus its 24 top
 * padding), 16 apart. **No PrimaryAction**: a tap selects and advances.
 *
 * NOT IN FIGMA: "I have an invite code", a secondary link in the action block
 * at 772, for someone who signed up to join rather than to create. Figma draws
 * manual entry (309:42) with a back, but nothing that leads to it; without
 * this, a joiner with no link could only create a bond. Owner ruling owed.
 *
 * [showBack] is false when this is the first screen after sign in (nothing
 * behind it); the eyebrow returns and the heading sits at 107, as on every root form.
 */
@Composable
fun RelationshipTypeScreen(
    state: RelationshipTypeState,
    onAction: (RelationshipTypeAction) -> Unit,
    showBack: Boolean = true,
) {
    val spacing = MoyiTheme.spacing
    MoyiScaffold(
        onBack = if (showBack) ({ onAction(RelationshipTypeAction.OnBackClick) }) else null,
        action = {
            TextLink(
                stringResource(Res.string.bond_type_have_code),
                onClick = { onAction(RelationshipTypeAction.OnHaveCodeClick) },
                centered = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) {
        ScreenHeader(
            eyebrow = stringResource(Res.string.onboarding_eyebrow_new_bond),
            heading = stringResource(Res.string.bond_type_heading),
        )
        Spacer(Modifier.height(spacing.md))
        MoyiText(stringResource(Res.string.bond_type_line), tone = TextTone.Muted)
        Spacer(Modifier.height(spacing.xxl))
        // One radio group: screen readers announce "1 of 3" and treat the cards as a set.
        Column(
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            RelationshipType.entries.forEach { type ->
                RelationshipCard(
                    label = stringResource(type.label),
                    selected = state.selected == type,
                    onClick = { onAction(RelationshipTypeAction.OnSelect(type)) },
                )
            }
        }
    }
}

/**
 * One relationship card: 358 wide, padding 24, the label in `title`; when
 * selected, a check at the end, 24 in and centred (359:88 at 326, 344.5).
 *
 * Local, not a designsystem `SelectableCard`: states.md §1a says these cards
 * recur exactly twice (here and Bond settings) and are promoted only if a
 * third use appears.
 *
 * Selection is a radio group's, announced with the hint "Selects and
 * continues" (states.md §1: say that tapping both selects and continues).
 * The selected state is carried by the check's shape and the spoken state,
 * not by colour (NFR-104). `outlineStrong`: the border is what identifies
 * the control (tokens.md §1).
 */
@Composable
private fun RelationshipCard(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MoyiTheme.colors
    val shapes = MoyiTheme.shapes
    val hint = stringResource(Res.string.bond_type_card_hint)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shapes.radius)
            // Before the fill, so the press feedback covers the whole card (D5).
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .background(colors.surfaceRaised)
            .border(shapes.hairline, colors.outlineStrong, shapes.radius)
            .semantics { onClick(label = hint) { onClick(); true } }
            .padding(MoyiTheme.spacing.lg),
        // No gap: the selected label narrows from 310 to 286, the check's 24 (359:79).
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MoyiText(label, role = TextRole.Title, modifier = Modifier.weight(1f))
        if (selected) {
            Icon(
                imageVector = MoyiIcons.Check,
                contentDescription = null,
                tint = colors.ink,
                modifier = Modifier.size(MoyiDimens.IconGlyph),
            )
        }
    }
}

@Preview
@Composable
private fun RelationshipTypeChoosePreview() {
    MoyiTheme(darkTheme = true) { RelationshipTypeScreen(state = RelationshipTypeState(), onAction = {}) }
}

@Preview
@Composable
private fun RelationshipTypeSelectedPreview() {
    MoyiTheme(darkTheme = true) {
        RelationshipTypeScreen(state = RelationshipTypeState(selected = RelationshipType.Couple), onAction = {})
    }
}

@Preview
@Composable
private fun RelationshipTypeLightPreview() {
    MoyiTheme(darkTheme = false) { RelationshipTypeScreen(state = RelationshipTypeState(), onAction = {}) }
}
