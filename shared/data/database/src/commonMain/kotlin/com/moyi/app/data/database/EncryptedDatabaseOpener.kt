package com.moyi.app.data.database

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import co.touchlab.kermit.Logger

/**
 * The database files, by name, in the platform's database directory.
 * Blocking; called off the main thread.
 */
internal interface DatabaseFiles {
    /** The absolute path of [name], for `ATTACH`. */
    fun path(name: String): String

    fun exists(name: String): Boolean

    /** The first [length] bytes of [name], or null if it is missing or shorter. */
    fun header(name: String, length: Int): ByteArray?

    /** Deletes [name]; nothing happens when it is missing. */
    fun delete(name: String)

    /** Moves [from] over [to], replacing it. */
    fun rename(from: String, to: String)
}

/**
 * One connection to a database file, outside SQLDelight: the plaintext → encrypted
 * migration must run `ATTACH`, `sqlcipher_export` and `DETACH` on the same
 * connection, and SQLDelight's iOS driver sends reads to a separate reader
 * connection. Arguments are bound, never spliced into the SQL.
 */
internal interface RawConnection {
    fun execute(sql: String, vararg args: String)

    fun queryLong(sql: String): Long

    fun queryStrings(sql: String): List<String>

    fun close()
}

/**
 * Opens the app's database encrypted with SQLCipher, migrating a plaintext one first.
 *
 * - **Key**: one random 256-bit key per database file, from [keys]. A new file gets
 *   a new key, so a reinstall (iOS keeps Keychain items across one) or a recovery
 *   never reuses an old key.
 * - **Plaintext upgrade**: a database written before encryption (its header is
 *   `SQLite format 3`) is copied into `moyi.db.encrypting` with `sqlcipher_export`,
 *   every table's row count is checked, the plaintext file and its `-journal`,
 *   `-wal` and `-shm` are deleted, and the encrypted copy is renamed into place. A
 *   crash before the delete leaves the plaintext file, and the next launch starts
 *   over; a crash after it leaves only the complete copy, which the next launch
 *   renames. If the copy fails, the plaintext file is kept for the next launch
 *   and this run uses a database in memory.
 * - **Key gone** ([DatabaseKeyRead.Missing] or [DatabaseKeyRead.Lost] while an
 *   encrypted file exists), or a file that does not open under its key: nothing can
 *   read that file again, so it is deleted and a new, empty one is made. The app
 *   never crashes over it. Only the exception's class is logged.
 * - **Key unavailable for now** ([DatabaseKeyRead.Unavailable], or storing a new key
 *   failing): the file is kept untouched and this run uses a database in memory.
 *
 * SQLCipher must be the SQLite the binary runs: if `PRAGMA cipher_version` answers
 * nothing, the sqlite3 symbols resolved to a plain SQLite and the file would be
 * written in the clear, so [SqlCipherMissingError] is thrown and not recovered from.
 */
internal class EncryptedDatabaseOpener(
    private val files: DatabaseFiles,
    private val keys: DatabaseKeyStore,
    /** A SQLDelight driver over [name] keyed with [passphrase] (`x'…'`), which creates or migrates the schema. */
    private val openDriver: (name: String, passphrase: String) -> SqlDriver,
    /** One unkeyed connection to [name]: on a plaintext file it reads the plaintext. */
    private val openRaw: (name: String) -> RawConnection,
    private val inMemoryDriver: () -> SqlDriver,
    private val name: String = DATABASE_NAME,
) {
    private val log = Logger.withTag("LocalDatabase")
    private val encrypting = "$name$ENCRYPTING_SUFFIX"

    fun open(): SqlDriver {
        finishInterruptedMigration()
        val exists = files.exists(name)
        val plaintext = exists && isPlaintext()
        val key = try {
            keyFor(exists, plaintext)
        } catch (e: Exception) {
            log.w { "Database key unavailable (${e::class.simpleName}); using memory this run" }
            null
        } ?: return inMemoryDriver()

        if (plaintext) {
            try {
                migrate(key.toRawKeyPassphrase())
            } catch (e: Exception) {
                log.w { "Plaintext database not migrated (${e::class.simpleName}); kept for the next launch" }
                deleteFiles(encrypting)
                return inMemoryDriver()
            }
        }

        return try {
            openVerified(key)
        } catch (e: Exception) {
            log.w { "Database not readable with its key (${e::class.simpleName}); starting a new one" }
            startOver()
        }
    }

    /** The key for the file on disk, or null to use memory this run. */
    private fun keyFor(exists: Boolean, plaintext: Boolean): ByteArray? {
        // A new file, or one about to be encrypted for the first time: a new key.
        if (!exists || plaintext) return keys.create()
        return when (val read = keys.read()) {
            is DatabaseKeyRead.Found -> read.key
            DatabaseKeyRead.Missing, DatabaseKeyRead.Lost -> {
                log.w { "Database key gone ($read); deleting the database it encrypted" }
                deleteFiles(name)
                keys.create()
            }
            is DatabaseKeyRead.Unavailable -> {
                log.w { "Database key unavailable (${read.reason}); using memory this run" }
                null
            }
        }
    }

    private fun startOver(): SqlDriver {
        deleteFiles(name)
        return try {
            openVerified(keys.create())
        } catch (e: Exception) {
            log.w { "New database not created (${e::class.simpleName}); using memory this run" }
            inMemoryDriver()
        }
    }

    private fun openVerified(key: ByteArray): SqlDriver {
        val driver = openDriver(name, key.toRawKeyPassphrase())
        try {
            // SQLCipher checks the key on the first read of the file: a wrong key or
            // a file that is not a database fails here, not in some later query.
            driver.queryLong("SELECT COUNT(*) FROM sqlite_master")
        } catch (e: Exception) {
            driver.close()
            throw e
        }
        val version = driver.cipherVersion()
        if (version == null) {
            driver.close()
            throw SqlCipherMissingError()
        }
        // The library's version only: proof in the device log that SQLCipher, not the
        // system SQLite, opened the file.
        log.i { "Database open (SQLCipher $version)" }
        return driver
    }

    private fun isPlaintext(): Boolean = files.header(name, PLAINTEXT_HEADER.size)?.contentEquals(PLAINTEXT_HEADER) == true

    /** A previous launch deleted the plaintext file but did not get to the rename. */
    private fun finishInterruptedMigration() {
        if (!files.exists(encrypting)) return
        if (files.exists(name)) {
            deleteFiles(encrypting) // The plaintext file is still there: start the copy over.
        } else {
            files.rename(encrypting, name)
        }
    }

    private fun migrate(passphrase: String) {
        deleteFiles(encrypting)
        val connection = openRaw(name)
        try {
            exportToEncrypted(connection, files.path(encrypting), passphrase)
        } finally {
            connection.close()
        }
        deleteFiles(name)
        files.rename(encrypting, name)
    }

    private fun deleteFiles(base: String) {
        SIDE_FILE_SUFFIXES.forEach { files.delete(base + it) }
    }

    internal companion object {
        const val ENCRYPTING_SUFFIX = ".encrypting"

        /** The file and the files SQLite keeps beside it. */
        val SIDE_FILE_SUFFIXES = listOf("", "-journal", "-wal", "-shm")

        /** The first 16 bytes of every plaintext SQLite file. An encrypted one starts with its random salt. */
        val PLAINTEXT_HEADER = "SQLite format 3\u0000".encodeToByteArray()
    }
}

/**
 * Copies the plaintext database [connection] is open on into a new encrypted file at
 * [targetPath], keyed with [passphrase], and proves the copy: same `user_version`
 * (`sqlcipher_export` does not copy it, and SQLDelight migrates by it) and the same
 * number of rows in every table. Throws if anything differs.
 */
internal fun exportToEncrypted(connection: RawConnection, targetPath: String, passphrase: String) {
    connection.execute("ATTACH DATABASE ? AS encrypted KEY ?", targetPath, passphrase)
    try {
        connection.queryStrings("SELECT sqlcipher_export('encrypted')")
        val version = connection.queryLong("PRAGMA main.user_version")
        // A PRAGMA takes no bound value; the version is an integer SQLite just returned.
        connection.execute("PRAGMA encrypted.user_version = $version")
        check(connection.queryLong("PRAGMA encrypted.user_version") == version) { "user_version not copied" }
        val tables = connection.queryStrings(
            "SELECT name FROM main.sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'",
        )
        tables.forEach { table ->
            val plain = connection.queryLong("SELECT COUNT(*) FROM main.\"$table\"")
            val copied = connection.queryLong("SELECT COUNT(*) FROM encrypted.\"$table\"")
            check(plain == copied) { "Row count differs after export" }
        }
    } finally {
        connection.execute("DETACH DATABASE encrypted")
    }
}

/** The SQLite in this binary is not SQLCipher: a build that links the wrong library must not run. */
internal class SqlCipherMissingError : Error("SQLCipher is not the linked SQLite (PRAGMA cipher_version is empty)")

/** `PRAGMA cipher_version`: SQLCipher's version, or null when the SQLite is not SQLCipher. */
internal fun SqlDriver.cipherVersion(): String? = executeQuery(
    identifier = null,
    sql = "PRAGMA cipher_version",
    mapper = { cursor -> QueryResult.Value(if (cursor.next().value) cursor.getString(0) else null) },
    parameters = 0,
).value?.takeIf { it.isNotBlank() }

internal fun SqlDriver.queryLong(sql: String): Long = executeQuery(
    identifier = null,
    sql = sql,
    mapper = { cursor -> QueryResult.Value(if (cursor.next().value) cursor.getLong(0) ?: 0L else 0L) },
    parameters = 0,
).value
