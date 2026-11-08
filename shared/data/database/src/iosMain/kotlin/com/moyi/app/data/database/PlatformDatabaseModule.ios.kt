package com.moyi.app.data.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import app.cash.sqldelight.driver.native.inMemoryDriver
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUserDomainMask

actual val platformDatabaseModule: Module = module {
    single<SqlDriver> { iosDatabaseDriver(databaseDirectory()) }
}

/**
 * The database in [directory], which is first excluded from iCloud and Finder
 * backups (`NSURLIsExcludedFromBackupKey`). Excluding the directory excludes
 * everything in it, so the `-wal` and `-shm` files SQLite adds are covered too.
 *
 * Android keeps its database out of every backup by switching backup off for the
 * whole app; this is the iOS half of the same decision. If the exclusion cannot be
 * set, the database is kept in memory instead: losing the cache at the next launch
 * is better than an entry leaving the device in a backup.
 */
internal fun iosDatabaseDriver(directory: String?): SqlDriver =
    if (directory != null && excludeFromBackup(directory)) {
        NativeSqliteDriver(
            schema = MoyiDatabase.Schema,
            name = DATABASE_NAME,
            onConfiguration = { it.copy(extendedConfig = it.extendedConfig.copy(basePath = directory)) },
        )
    } else {
        inMemoryDriver(MoyiDatabase.Schema)
    }

/** `Library/Application Support/LocalDatabase`, created if missing; null if it cannot be. */
@OptIn(ExperimentalForeignApi::class)
internal fun databaseDirectory(): String? {
    val files = NSFileManager.defaultManager
    val support = files.URLForDirectory(NSApplicationSupportDirectory, NSUserDomainMask, null, true, null) ?: return null
    val directory = support.URLByAppendingPathComponent("LocalDatabase", isDirectory = true) ?: return null
    if (!files.createDirectoryAtURL(directory, withIntermediateDirectories = true, attributes = null, error = null)) return null
    return directory.path
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal fun excludeFromBackup(directory: String): Boolean =
    NSURL.fileURLWithPath(directory, isDirectory = true)
        .setResourceValue(NSNumber(bool = true), forKey = NSURLIsExcludedFromBackupKey, error = null)

/** Read back from a fresh URL (an NSURL caches the values it has read), for the test. */
@OptIn(ExperimentalForeignApi::class)
internal fun isExcludedFromBackup(path: String): Boolean = memScoped {
    val value = alloc<ObjCObjectVar<Any?>>()
    val read = NSURL.fileURLWithPath(path).getResourceValue(value.ptr, forKey = NSURLIsExcludedFromBackupKey, error = null)
    read && (value.value as? NSNumber)?.boolValue == true
}
