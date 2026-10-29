package com.moyi.app.feature.onboarding.di

import com.moyi.app.domain.bond.InvitePreview
import com.moyi.app.feature.onboarding.common.RelationshipType
import com.moyi.app.feature.onboarding.createbond.CreateBondViewModel
import com.moyi.app.feature.onboarding.gate.UnverifiedGateViewModel
import com.moyi.app.feature.onboarding.invite.InviteHandoffViewModel
import com.moyi.app.feature.onboarding.join.JoinCodeViewModel
import com.moyi.app.feature.onboarding.join.JoinConfirmViewModel
import com.moyi.app.feature.onboarding.relationship.RelationshipTypeViewModel
import com.moyi.app.feature.onboarding.start.SignedInStartViewModel
import kotlinx.datetime.TimeZone
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Onboarding's ViewModels, over the domain's bond, account and auth
 * repositories (bound in the data layer). Navigation arguments arrive as Koin
 * parameters, as in auth.
 */
val onboardingFeatureModule = module {
    viewModelOf(::SignedInStartViewModel)
    viewModel { UnverifiedGateViewModel(auth = get(), account = get()) }
    viewModelOf(::RelationshipTypeViewModel)
    viewModel { (type: RelationshipType) ->
        // Read when the screen opens, so a zone changed in system settings since launch is the one sent.
        CreateBondViewModel(type, bonds = get(), deviceTimeZone = TimeZone.currentSystemDefault().id)
    }
    viewModel { (code: String, link: String) -> InviteHandoffViewModel(code, link) }
    viewModel { params -> JoinCodeViewModel(params.getOrNull<String>(), bonds = get()) }
    viewModel { (code: String, preview: InvitePreview) -> JoinConfirmViewModel(code, preview, bonds = get()) }
}
