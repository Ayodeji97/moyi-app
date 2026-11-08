@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.moyi.app.data.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * On the simulator, against the SQLCipher the app links (the test executable is
 * linked with the same xcframework, never the system SQLite), through the real file
 * system. The key store is a fake: unsigned simulator tests cannot reach the
 * Keychain (-34018); its status mapping is tested in [KeychainKeyReadTest].
 */
class EncryptedDatabaseIosTest {
    private val directory = "${NSTemporaryDirectory()}moyi-db-${NSUUID().UUIDString}".also {
        NSFileManager.defaultManager.createDirectoryAtPath(it, withIntermediateDirectories = true, attributes = null, error = null)
    }
    private val files = IosDatabaseFiles(directory)
    private val keys = FakeKeyStore()
    private val opened = mutableListOf<SqlDriver>()

    @AfterTest
    fun cleanUp() {
        opened.forEach { runCatching { it.close() } }
        NSFileManager.defaultManager.removeItemAtPath(directory, null)
    }

    private fun open(): LocalDatabase = LocalDatabase(iosDatabaseDriver(directory, keys).also { opened += it })

    private fun close() {
        opened.forEach { it.close() }
        opened.clear()
    }

    /** A database written the way every build before encryption wrote it: no key at all. */
    private fun plaintextDatabase(): LocalDatabase {
        val driver = NativeSqliteDriver(
            schema = MoyiDatabase.Schema,
            name = DATABASE_NAME,
            onConfiguration = { it.copy(extendedConfig = it.extendedConfig.copy(basePath = directory)) },
        )
        opened += driver
        return LocalDatabase(driver)
    }

    private fun header(): ByteArray = files.header(DATABASE_NAME, 16)!!

    /** The whole file as text, to search for an entry in the clear. */
    private fun fileText(): String =
        NSFileManager.defaultManager.contentsAtPath(files.path(DATABASE_NAME))!!.toByteArray().decodeToString()

    @Test
    fun theLinkedSqliteIsSqlCipherAndTheFileIsNotReadableAsPlainSqlite() = runTest {
        val database = open()
        database.io { cachedDayQueries.insert(Rows.day("ada")) }
        assertNotNull(database.cipherVersion(), "PRAGMA cipher_version must answer: SQLCipher, not the system SQLite")
        close()

        assertFalse(header().contentEquals(EncryptedDatabaseOpener.PLAINTEXT_HEADER), "the header must not be 'SQLite format 3'")
        assertFalse(fileText().contains(Rows.SECRET), "entry text must not be in the file in the clear")
    }

    @Test
    fun secureDeleteIsOn() = runTest {
        val database = open()
        database.io { cachedDayQueries.insert(Rows.day("ada")) } // Opens the writer and a reader.
        assertEquals(1L, database.secureDelete())
    }

    @Test
    fun aPlaintextDatabaseIsMigratedWithEveryRowAndThePlaintextIsGone() = runTest {
        plaintextDatabase().io {
            cachedDayQueries.insert(Rows.day("ada"))
            draftQueries.upsert(Rows.draft)
            outboxQueries.insert(Rows.queued)
        }
        close()
        assertContentEquals(EncryptedDatabaseOpener.PLAINTEXT_HEADER, header())

        val database = open()

        assertFalse(header().contentEquals(EncryptedDatabaseOpener.PLAINTEXT_HEADER))
        database.io {
            assertEquals(Rows.day("ada"), cachedDayQueries.forUser("ada").executeAsOneOrNull())
            assertEquals(listOf(Rows.queued), outboxQueries.queuedForUser("ada").executeAsList())
        }
        listOf("cached_day", "draft", "outbox_entry").forEach { assertEquals(1L, database.rowCount(it), it) }
        assertFalse(files.exists(DATABASE_NAME + EncryptedDatabaseOpener.ENCRYPTING_SUFFIX))
        close()
        assertFalse(fileText().contains(Rows.SECRET))
    }

    @Test
    fun theDatabaseOpensAgainWithItsStoredKey() = runTest {
        open().io { outboxQueries.insert(Rows.queued) }
        close()
        assertEquals(1L, open().rowCount("outbox_entry"))
    }

    @Test
    fun aLostKeyStartsAnEmptyDatabaseWithANewKeyInsteadOfCrashing() = runTest {
        open().io { outboxQueries.insert(Rows.queued) }
        close()
        val oldKey = keys.key!!.copyOf()
        keys.forget(DatabaseKeyRead.Lost)

        val database = open()

        assertEquals(0L, database.rowCount("outbox_entry"))
        assertFalse(oldKey.contentEquals(keys.key!!), "a new database gets a new key")
        assertNotNull(database.cipherVersion())
    }

    @Test
    fun aMissingKeyForAnEncryptedFileStartsOver() = runTest {
        open().io { outboxQueries.insert(Rows.queued) }
        close()
        keys.forget(DatabaseKeyRead.Missing)

        assertEquals(0L, open().rowCount("outbox_entry"))
    }

    @Test
    fun theWrongKeyStartsOverInsteadOfCrashing() = runTest {
        open().io { outboxQueries.insert(Rows.queued) }
        close()
        keys.key = ByteArray(DATABASE_KEY_BYTES) { 7 }

        assertEquals(0L, open().rowCount("outbox_entry"))
    }

    @Test
    fun anUnavailableKeyKeepsTheFileAndUsesMemoryThisRun() = runTest {
        open().io { outboxQueries.insert(Rows.queued) }
        close()
        val before = NSFileManager.defaultManager.contentsAtPath(files.path(DATABASE_NAME))!!.toByteArray()
        val key = keys.key!!.copyOf()
        keys.unavailable = true

        val database = open()
        assertEquals(0L, database.rowCount("outbox_entry"))
        close()

        assertContentEquals(before, NSFileManager.defaultManager.contentsAtPath(files.path(DATABASE_NAME))!!.toByteArray())
        keys.unavailable = false
        assertContentEquals(key, keys.key)
        assertEquals(1L, open().rowCount("outbox_entry"), "the next run reads the kept file")
    }

    @Test
    fun aMigrationInterruptedAfterThePlaintextWasDeletedIsFinished() = runTest {
        open().io { outboxQueries.insert(Rows.queued) }
        close()
        files.rename(DATABASE_NAME, DATABASE_NAME + EncryptedDatabaseOpener.ENCRYPTING_SUFFIX)

        assertEquals(1L, open().rowCount("outbox_entry"))
    }

    @Test
    fun wipeEmptiesEveryTableOfTheEncryptedDatabase() = runTest {
        val database = open()
        database.io {
            cachedDayQueries.insert(Rows.day("ada"))
            draftQueries.upsert(Rows.draft)
            outboxQueries.insert(Rows.queued)
        }

        database.wipe()

        database.userTables().forEach { assertEquals(0L, database.rowCount(it), it) }
        close()
        assertFalse(fileText().contains(Rows.SECRET))
    }

    @Test
    fun theRawKeyPassphraseIsSqlCiphersHexForm() {
        val passphrase = ByteArray(DATABASE_KEY_BYTES) { it.toByte() }.toRawKeyPassphrase()
        assertTrue(passphrase.matches(Regex("x'[0-9A-F]{64}'")))
        assertTrue(passphrase.startsWith("x'000102"))
    }
}

/** The Keychain stand-in: one key in memory, which a test can lose or make unavailable. */
internal class FakeKeyStore : DatabaseKeyStore {
    var key: ByteArray? = null
    var unavailable = false
    private var gone: DatabaseKeyRead? = null

    fun forget(reason: DatabaseKeyRead) {
        key = null
        gone = reason
    }

    override fun read(): DatabaseKeyRead = when {
        unavailable -> DatabaseKeyRead.Unavailable("test")
        key != null -> DatabaseKeyRead.Found(key!!.copyOf())
        else -> gone ?: DatabaseKeyRead.Missing
    }

    override fun create(): ByteArray {
        check(!unavailable) { "Key store unavailable" }
        gone = null
        return kotlin.random.Random.nextBytes(DATABASE_KEY_BYTES).also { key = it.copyOf() }
    }

    override fun delete() {
        key = null
    }
}
