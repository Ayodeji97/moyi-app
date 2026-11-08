package com.moyi.app.data.database

import app.cash.sqldelight.db.SqlDriver
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The one [LocalDatabase], over the platform's driver. The driver is resolved on the
 * database's first use (off the main thread), not here: see [LocalDatabase].
 */
val databaseModule = module {
    single {
        val scope = this
        LocalDatabase { scope.get<SqlDriver>() }
    }
}

/**
 * The platform's `SqlDriver`, encrypted with SQLCipher ([EncryptedDatabaseOpener]):
 * Android's over the app's private databases directory (needs `androidContext`), with
 * its key wrapped by the Android Keystore; iOS's in a directory excluded from iCloud
 * and Finder backups, with its key in the Keychain.
 */
expect val platformDatabaseModule: Module

val databaseModules: List<Module> = listOf(databaseModule, platformDatabaseModule)

/** The file name on both platforms. */
internal const val DATABASE_NAME = "moyi.db"
