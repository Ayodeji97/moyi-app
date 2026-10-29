package com.moyi.app.feature.onboarding.relationship

import androidx.compose.runtime.Immutable
import com.moyi.app.feature.onboarding.common.RelationshipType

/**
 * Onboarding 4 · relationship type. CHOOSE (341:24) has nothing selected;
 * SELECTED (359:79) is the same screen on a return visit, with a check on
 * the chosen card. No pre-selection: choosing is the point of the screen.
 */
@Immutable
data class RelationshipTypeState(
    val selected: RelationshipType? = null,
)

sealed interface RelationshipTypeAction {
    /** One tap selects and advances. There is no Continue (states.md §1a). */
    data class OnSelect(val type: RelationshipType) : RelationshipTypeAction
    data object OnBackClick : RelationshipTypeAction

    /** "I have an invite code" (not in Figma): manual code entry. */
    data object OnHaveCodeClick : RelationshipTypeAction
}

sealed interface RelationshipTypeEvent {
    data class NavigateToCreateBond(val type: RelationshipType) : RelationshipTypeEvent
    data object NavigateBack : RelationshipTypeEvent
    data object NavigateToJoin : RelationshipTypeEvent
}
