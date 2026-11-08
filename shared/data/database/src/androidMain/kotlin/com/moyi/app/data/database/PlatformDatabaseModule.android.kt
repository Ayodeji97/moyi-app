package com.moyi.app.data.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The app's private databases directory. It never leaves the device: the app
 * disables backup and device-to-device transfer altogether (`allowBackup="false"`
 * and `data_extraction_rules.xml` in :androidApp).
 */
actual val platformDatabaseModule: Module = module {
    single<SqlDriver> { AndroidSqliteDriver(MoyiDatabase.Schema, androidContext(), DATABASE_NAME) }
}
