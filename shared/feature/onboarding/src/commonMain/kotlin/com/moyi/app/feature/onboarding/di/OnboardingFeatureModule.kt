package com.moyi.app.feature.onboarding.di

import com.moyi.app.domain.bond.InvitePreview
import com.moyi.app.feature.onboarding.common.RelationshipType
import com.moyi.app.feature.onboarding.createbond.CreateBondViewModel
import com.moyi.app.feature.onboarding.gate.UnverifiedGateViewModel
import com.moyi.app.feature.onboarding.invite.InviteHandoffViewModel
import com.moyi.app.feature.onboarding.join.JoinCodeViewModel
import com.moyi.app.feature.onboarding.join.JoinConfirmViewModel
import com.moyi.app.feature.onboarding.join.LeavingBond
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
        CreateBondViewModel(
            type,
            bonds = get(),
            deviceTimeZone = TimeZone.currentSystemDefault().id,
            // The platform's tz database (no endpoint): read when the picker opens.
            availableZoneIds = { TimeZone.availableZoneIds },
        )
    }
    viewModel { (code: String, link: String, canJoinInstead: Boolean) -> InviteHandoffViewModel(code, link, canJoinInstead) }
    viewModel { params -> JoinCodeViewModel(params.getOrNull<String>(), bonds = get()) }
    // By type: the third parameter, the empty bond Join also ends, is null when there is none.
    viewModel { params ->
        JoinConfirmViewModel(
            code = params.get<String>(),
            preview = params.get<InvitePreview>(),
            leaving = params.getOrNull<LeavingBond>(),
            bonds = get(),
            emptyBonds = get(),
        )
    }
}
