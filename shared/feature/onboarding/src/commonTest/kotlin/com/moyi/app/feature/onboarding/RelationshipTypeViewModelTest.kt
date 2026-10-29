package com.moyi.app.feature.onboarding

import app.cash.turbine.test
import com.moyi.app.feature.onboarding.common.RelationshipType
import com.moyi.app.feature.onboarding.relationship.RelationshipTypeAction
import com.moyi.app.feature.onboarding.relationship.RelationshipTypeEvent
import com.moyi.app.feature.onboarding.relationship.RelationshipTypeViewModel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RelationshipTypeViewModelTest : MainDispatcherTest() {
    private val viewModel = RelationshipTypeViewModel()

    @Test
    fun nothingIsPreselected() {
        assertNull(viewModel.state.value.selected)
    }

    @Test
    fun oneTapSelectsAndAdvancesWithThatType() = runTest {
        viewModel.events.test {
            viewModel.onAction(RelationshipTypeAction.OnSelect(RelationshipType.Family))
            assertEquals(RelationshipTypeEvent.NavigateToCreateBond(RelationshipType.Family), awaitItem())
        }
        // Kept, so the return visit shows SELECTED (359:79).
        assertEquals(RelationshipType.Family, viewModel.state.value.selected)
    }

    @Test
    fun theCardsAreTheThreeDrawnInOrder() {
        assertEquals(listOf(RelationshipType.Couple, RelationshipType.Friends, RelationshipType.Family), RelationshipType.entries)
    }
}
