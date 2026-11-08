package com.moyi.app.data.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import app.cash.sqldelight.driver.native.inMemoryDriver
import co.touchlab.sqliter.DatabaseConfiguration
import co.touchlab.sqliter.DatabaseConnection
import co.touchlab.sqliter.NO_VERSION_CHECK
import co.touchlab.sqliter.createDatabaseManager
import co.touchlab.sqliter.longForQuery
import co.touchlab.sqliter.setCipherKey
import co.touchlab.sqliter.withStatement
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
import platform.Foundation.NSFileHandle
import platform.Foundation.NSFileManager
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUserDomainMask
import platform.Foundation.closeFile
import platform.Foundation.fileHandleForReadingAtPath
import platform.Foundation.readDataOfLength

actual val platformDatabaseModule: Module = module {
    single<SqlDriver> { iosDatabaseDriver(databaseDirectory()) }
}

/**
 * The database in [directory], encrypted with SQLCipher ([EncryptedDatabaseOpener],
 * key in the Keychain), after the directory is excluded from iCloud and Finder
 * backups (`NSURLIsExcludedFromBackupKey`). Excluding the directory excludes
 * everything in it, so the `-wal` and `-shm` files SQLite adds are covered too.
 *
 * Android keeps its database out of every backup by switching backup off for the
 * whole app; this is the iOS half of the same decision. If the exclusion cannot be
 * set, the database is kept in memory instead: losing the cache at the next launch
 * is better than an entry leaving the device in a backup.
 */
internal fun iosDatabaseDriver(
    directory: String?,
    keys: DatabaseKeyStore = KeychainDatabaseKeyStore(),
    name: String = DATABASE_NAME,
): SqlDriver =
    if (directory != null && excludeFromBackup(directory)) {
        EncryptedDatabaseOpener(
            files = IosDatabaseFiles(directory),
            keys = keys,
            openDriver = { file, passphrase -> sqlCipherDriver(directory, file, passphrase) },
            openRaw = { file -> IosRawConnection(directory, file) },
            inMemoryDriver = { inMemoryDriver(MoyiDatabase.Schema) },
            name = name,
        ).open()
    } else {
        inMemoryDriver(MoyiDatabase.Schema)
    }

/**
 * SQLDelight's native driver over [name], every connection keyed first and then
 * given `secure_delete`. The key is set here, in `onCreateConnection`, not through
 * SQLiter's `encryptionConfig`, so that the order is explicit: `PRAGMA key` is the
 * first statement on every connection (the writer and each reader), as SQLCipher
 * requires, and `secure_delete` follows it.
 */
private fun sqlCipherDriver(directory: String, name: String, passphrase: String): SqlDriver = NativeSqliteDriver(
    schema = MoyiDatabase.Schema,
    name = name,
    onConfiguration = { config ->
        val previous = config.lifecycleConfig.onCreateConnection
        config.copy(
            extendedConfig = config.extendedConfig.copy(basePath = directory),
            lifecycleConfig = config.lifecycleConfig.copy(
                onCreateConnection = { connection ->
                    connection.setCipherKey(passphrase)
                    connection.rawExecSql("PRAGMA secure_delete = ON")
                    previous(connection)
                },
            ),
        )
    },
)

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

@OptIn(ExperimentalForeignApi::class)
internal class IosDatabaseFiles(private val directory: String) : DatabaseFiles {
    private val manager = NSFileManager.defaultManager

    override fun path(name: String): String = "$directory/$name"

    override fun exists(name: String): Boolean = manager.fileExistsAtPath(path(name))

    override fun header(name: String, length: Int): ByteArray? {
        val handle = NSFileHandle.fileHandleForReadingAtPath(path(name)) ?: return null
        try {
            return handle.readDataOfLength(length.toULong()).toByteArray().takeIf { it.size == length }
        } finally {
            handle.closeFile()
        }
    }

    override fun delete(name: String) {
        manager.removeItemAtPath(path(name), null)
    }

    override fun rename(from: String, to: String) {
        manager.removeItemAtPath(path(to), null)
        check(manager.moveItemAtPath(path(from), toPath = path(to), error = null)) { "Rename failed" }
    }
}

/** One unkeyed SQLiter connection: on a plaintext file it reads the plaintext. */
internal class IosRawConnection(directory: String, name: String) : RawConnection {
    private val connection: DatabaseConnection = createDatabaseManager(
        DatabaseConfiguration(
            name = name,
            // Skip SQLiter's create and migrate: the file is copied exactly as it is.
            version = NO_VERSION_CHECK,
            create = {},
            extendedConfig = DatabaseConfiguration.Extended(basePath = directory),
        ),
    ).createSingleThreadedConnection()

    override fun execute(sql: String, vararg args: String) {
        connection.withStatement(sql) {
            args.forEachIndexed { index, value -> bindString(index + 1, value) }
            execute()
        }
    }

    override fun queryLong(sql: String): Long = connection.longForQuery(sql)

    override fun queryStrings(sql: String): List<String> = connection.withStatement(sql) {
        val cursor = query()
        buildList { while (cursor.next()) add(cursor.getString(0)) }
    }

    override fun close() = connection.close()
}
