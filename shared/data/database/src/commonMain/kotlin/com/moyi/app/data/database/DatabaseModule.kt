package com.moyi.app.data.database

import org.koin.core.module.Module
import org.koin.dsl.module

/** The one [LocalDatabase], over the platform's driver. */
val databaseModule = module {
    single { LocalDatabase(get()) }
}

/**
 * The platform's `SqlDriver`: Android's over the app's private databases directory
 * (needs `androidContext`), iOS's in a directory excluded from iCloud and Finder
 * backups.
 */
expect val platformDatabaseModule: Module

val databaseModules: List<Module> = listOf(databaseModule, platformDatabaseModule)

/** The file name on both platforms. */
internal const val DATABASE_NAME = "moyi.db"
