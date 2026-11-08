package com.moyi.app.data.database

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyStore
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/**
 * On a device or emulator: SQLCipher for Android is a native library, and the key
 * store is the real Android Keystore. Each test uses its own database name, key
 * alias and key file, and deletes them after.
 */
@RunWith(AndroidJUnit4::class)
class EncryptedDatabaseAndroidTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val id = System.nanoTime()
    private val name = "test-$id.db"
    private val alias = "moyi.database.test.$id"
    private val keyFile = "test-key-$id.bin"
    private val keys = AndroidKeystoreDatabaseKeyStore(context, alias, keyFile)
    private val opened = mutableListOf<SqlDriver>()

    @After
    fun cleanUp() {
        opened.forEach { runCatching { it.close() } }
        listOf("", "-journal", "-wal", "-shm", ".encrypting").forEach { context.getDatabasePath(name + it).delete() }
        keys.delete()
    }

    private fun open(): LocalDatabase = LocalDatabase(androidEncryptedDriver(context, keys, name).also { opened += it })

    private fun close() {
        opened.forEach { it.close() }
        opened.clear()
    }

    private fun file(): File = context.getDatabasePath(name)

    private fun header(): ByteArray = file().readBytes().copyOf(16)

    @Test
    fun theKeyRoundTripsThroughTheKeystore() {
        assertEquals(DatabaseKeyRead.Missing, keys.read())
        val key = keys.create()
        assertEquals(DATABASE_KEY_BYTES, key.size)
        assertContentEquals(key, assertIs<DatabaseKeyRead.Found>(keys.read()).key)
        assertFalse(File(context.noBackupFilesDir, keyFile).readBytes().containsSlice(key), "the key file holds the key wrapped")
        keys.delete()
        assertEquals(DatabaseKeyRead.Missing, keys.read())
    }

    @Test
    fun aKeyWhoseWrappingKeyIsGoneIsLost() {
        keys.create()
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)
        assertEquals(DatabaseKeyRead.Lost, keys.read())
    }

    @Test
    fun aTamperedKeyFileIsLost() {
        keys.create()
        val file = File(context.noBackupFilesDir, keyFile)
        file.writeBytes(file.readBytes().also { it[it.size - 1] = (it[it.size - 1] + 1).toByte() })
        assertEquals(DatabaseKeyRead.Lost, keys.read())
    }

    @Test
    fun theFileIsEncryptedAndSqlCipherIsTheSqlite(): Unit = runBlocking {
        val database = open()
        database.io { cachedDayQueries.insert(Rows.day("ada")) }
        assertNotNull(database.cipherVersion())
        assertEquals(1L, database.secureDelete())
        close()

        assertFalse(header().contentEquals(EncryptedDatabaseOpener.PLAINTEXT_HEADER))
        assertFalse(file().readBytes().containsSlice(Rows.SECRET.encodeToByteArray()))
    }

    @Test
    fun aPlaintextDatabaseFromAnEarlierBuildIsMigratedWithEveryRow(): Unit = runBlocking {
        // Written the way every build before this one wrote it: the framework's SQLite, no key.
        val plain = AndroidSqliteDriver(MoyiDatabase.Schema, context, name)
        LocalDatabase(plain).io {
            cachedDayQueries.insert(Rows.day("ada"))
            draftQueries.upsert(Rows.draft)
            outboxQueries.insert(Rows.queued)
            archiveDayQueries.upsert(Rows.archived)
        }
        plain.close()
        assertContentEquals(EncryptedDatabaseOpener.PLAINTEXT_HEADER, header())

        val database = open()

        database.io {
            assertEquals(Rows.day("ada"), cachedDayQueries.forUser("ada").executeAsOneOrNull())
            assertEquals(listOf(Rows.queued), outboxQueries.queuedForUser("ada").executeAsList())
        }
        listOf("cached_day", "draft", "outbox_entry", "archive_day").forEach { assertEquals(1L, database.rowCount(it), it) }
        close()
        assertFalse(header().contentEquals(EncryptedDatabaseOpener.PLAINTEXT_HEADER))
        assertFalse(file().readBytes().containsSlice(Rows.SECRET.encodeToByteArray()))
        listOf("-journal", "-wal", "-shm", ".encrypting").forEach { assertFalse(context.getDatabasePath(name + it).exists(), it) }
    }

    @Test
    fun theDatabaseOpensAgainWithItsStoredKey(): Unit = runBlocking {
        open().io { outboxQueries.insert(Rows.queued) }
        close()
        assertEquals(1L, open().rowCount("outbox_entry"))
    }

    @Test
    fun aLostKeyStartsAnEmptyDatabaseInsteadOfCrashing(): Unit = runBlocking {
        open().io { outboxQueries.insert(Rows.queued) }
        close()
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)

        val database = open()

        assertEquals(0L, database.rowCount("outbox_entry"))
        assertIs<DatabaseKeyRead.Found>(keys.read())
    }

    @Test
    fun wipeEmptiesEveryTable(): Unit = runBlocking {
        val database = open()
        database.io {
            cachedDayQueries.insert(Rows.day("ada"))
            draftQueries.upsert(Rows.draft)
            outboxQueries.insert(Rows.queued)
            archiveDayQueries.upsert(Rows.archived)
        }

        database.wipe()

        database.userTables().forEach { assertEquals(0L, database.rowCount(it), it) }
        close()
        assertFalse(file().readBytes().containsSlice(Rows.SECRET.encodeToByteArray()))
    }

    private fun ByteArray.containsSlice(slice: ByteArray): Boolean =
        (0..size - slice.size).any { start -> slice.indices.all { this[start + it] == slice[it] } }
}
