package com.moyi.app.feature.onboarding

import app.cash.turbine.test
import com.moyi.app.designsystem.presentation.UiText
import com.moyi.app.feature.onboarding.common.FakeOnboardingData
import com.moyi.app.feature.onboarding.common.RelationshipType
import com.moyi.app.feature.onboarding.createbond.CreateBondAction
import com.moyi.app.feature.onboarding.createbond.CreateBondEvent
import com.moyi.app.feature.onboarding.createbond.CreateBondViewModel
import com.moyi.app.feature.onboarding.resources.Res
import com.moyi.app.feature.onboarding.resources.bond_name_error_blank
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CreateBondViewModelTest : MainDispatcherTest() {
    private val viewModel = CreateBondViewModel(RelationshipType.Couple)

    @Test
    fun aBlankNameIsAFieldErrorAndGoesNowhere() = runTest {
        viewModel.events.test {
            viewModel.onAction(CreateBondAction.OnNameChange("   "))
            viewModel.onAction(CreateBondAction.OnCreateClick)
            expectNoEvents()
        }
        assertEquals(UiText.Resource(Res.string.bond_name_error_blank), viewModel.state.value.nameError)
    }

    @Test
    fun typingClearsTheError() {
        viewModel.onAction(CreateBondAction.OnCreateClick)
        viewModel.onAction(CreateBondAction.OnNameChange("A"))
        assertNull(viewModel.state.value.nameError)
    }

    @Test
    fun aNamedBondHandsOffTheFakeInviteCode() = runTest {
        viewModel.onAction(CreateBondAction.OnNameChange("Amara and Tunde"))
        viewModel.events.test {
            viewModel.onAction(CreateBondAction.OnCreateClick)
            assertEquals(CreateBondEvent.NavigateToInvite(FakeOnboardingData.INVITE_CODE), awaitItem())
        }
    }

    @Test
    fun theTimeZoneDefaultsToTheDevicesCity() {
        assertEquals(FakeOnboardingData.TIME_ZONE_CITY, viewModel.state.value.timeZoneCity)
    }
}
