package com.moyi.app.feature.bond.di

import com.moyi.app.feature.bond.account.AccountViewModel
import com.moyi.app.feature.bond.account.ExportViewModel
import com.moyi.app.feature.bond.notifications.NotificationSettingsViewModel
import com.moyi.app.feature.bond.report.ReportArgs
import com.moyi.app.feature.bond.report.ReportViewModel
import com.moyi.app.feature.bond.settings.BondSettingsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Bond settings' ViewModel and Notifications' (over NotificationSettingsRepository and the member's settings), over the domain's BondSettingsRepository, AuthRepository and BondRepository (invites)
 * (bound by the data layer, assembled in :shared). The bond's id comes from the route.
 */
val bondFeatureModule = module {
    viewModel { (bondId: String) -> BondSettingsViewModel(bondId, repository = get(), bonds = get()) }
    viewModel { (bondId: String) -> NotificationSettingsViewModel(bondId, notifications = get(), settings = get()) }
    // A report (FR-100), from the Archive day view or Bond settings, over ReportRepository.
    viewModel { (args: ReportArgs) -> ReportViewModel(args, reports = get()) }
    viewModel { AccountViewModel(deletion = get(), auth = get(), outbox = get(), sender = get(), emptyBonds = get()) }
    viewModel { ExportViewModel(exports = get()) }
}
