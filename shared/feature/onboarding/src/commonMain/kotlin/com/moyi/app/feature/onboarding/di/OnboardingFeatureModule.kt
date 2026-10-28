package com.moyi.app.feature.onboarding.di

import com.moyi.app.feature.onboarding.common.RelationshipType
import com.moyi.app.feature.onboarding.createbond.CreateBondViewModel
import com.moyi.app.feature.onboarding.invite.InviteHandoffViewModel
import com.moyi.app.feature.onboarding.join.JoinCodeViewModel
import com.moyi.app.feature.onboarding.join.JoinConfirmViewModel
import com.moyi.app.feature.onboarding.relationship.RelationshipTypeViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Onboarding's ViewModels. None has a dependency yet (PR #7 is fake state);
 * the logic PR adds the bond and invite repositories here. Navigation
 * arguments arrive as Koin parameters, as in auth.
 */
val onboardingFeatureModule = module {
    viewModelOf(::RelationshipTypeViewModel)
    viewModel { (type: RelationshipType) -> CreateBondViewModel(type) }
    viewModel { (code: String) -> InviteHandoffViewModel(code) }
    viewModel { params -> JoinCodeViewModel(params.getOrNull<String>()) }
    viewModel { (code: String) -> JoinConfirmViewModel(code) }
}
