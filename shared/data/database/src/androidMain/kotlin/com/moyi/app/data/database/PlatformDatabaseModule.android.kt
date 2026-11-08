package com.moyi.app.data.database

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import net.zetetic.database.sqlcipher.SQLiteConnection
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SQLiteDatabaseHook
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module
import java.io.File

/**
 * The app's private databases directory, encrypted with SQLCipher for Android. It
 * never leaves the device: the app disables backup and device-to-device transfer
 * altogether (`allowBackup="false"` and `data_extraction_rules.xml` in :androidApp),
 * and the key's wrapping key could not leave the Keystore anyway.
 */
actual val platformDatabaseModule: Module = module {
    single<SqlDriver> { androidEncryptedDriver(androidContext()) }
}

internal fun androidEncryptedDriver(
    context: Context,
    keys: DatabaseKeyStore = AndroidKeystoreDatabaseKeyStore(context),
    name: String = DATABASE_NAME,
): SqlDriver {
    loadSqlCipher()
    return EncryptedDatabaseOpener(
        files = AndroidDatabaseFiles(context),
        keys = keys,
        openDriver = { file, passphrase -> sqlCipherDriver(context, file, passphrase.encodeToByteArray()) },
        openRaw = { file -> AndroidRawConnection(context.getDatabasePath(file).path) },
        inMemoryDriver = { sqlCipherDriver(context, null, ByteArray(0)) },
        name = name,
    ).open()
}

/** SQLDelight's Android driver over SQLCipher's `SupportSQLiteOpenHelper`, keyed with [passphrase]. */
private fun sqlCipherDriver(context: Context, name: String?, passphrase: ByteArray): SqlDriver = AndroidSqliteDriver(
    schema = MoyiDatabase.Schema,
    context = context,
    name = name,
    factory = SupportOpenHelperFactory(passphrase, SecureDeleteHook, false),
)

/**
 * Runs on every connection right after it is keyed: deleted rows are overwritten
 * with zeros instead of lingering in free pages (a wipe really removes the text).
 */
private object SecureDeleteHook : SQLiteDatabaseHook {
    override fun preKey(connection: SQLiteConnection) = Unit

    override fun postKey(connection: SQLiteConnection) {
        connection.executeForString("PRAGMA secure_delete = ON", null, null)
    }
}

/** The JNI library sqlcipher-android ships; loaded once, before any database opens. */
private val sqlCipherLoaded: Unit by lazy { System.loadLibrary("sqlcipher") }

internal fun loadSqlCipher() = sqlCipherLoaded

private class AndroidDatabaseFiles(private val context: Context) : DatabaseFiles {
    private fun file(name: String): File = context.getDatabasePath(name)

    override fun path(name: String): String = file(name).path

    override fun exists(name: String): Boolean = file(name).exists()

    override fun header(name: String, length: Int): ByteArray? {
        val file = file(name)
        if (!file.isFile || file.length() < length) return null
        return file.inputStream().use { input -> ByteArray(length).takeIf { input.read(it) == length } }
    }

    override fun delete(name: String) {
        file(name).delete()
    }

    override fun rename(from: String, to: String) {
        check(file(from).renameTo(file(to))) { "Rename failed" }
    }
}

/**
 * One plaintext connection through SQLCipher (no key: it reads the file as it is).
 * CREATE_IF_NECESSARY because an attached database is opened with the main
 * connection's flags: without it, `ATTACH` cannot create the encrypted file.
 */
private class AndroidRawConnection(path: String) : RawConnection {
    private val database = SQLiteDatabase.openDatabase(
        path,
        null,
        SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY,
    )

    override fun execute(sql: String, vararg args: String) {
        if (args.isEmpty()) database.execSQL(sql) else database.execSQL(sql, arrayOf<Any>(*args))
    }

    override fun queryLong(sql: String): Long = database.rawQuery(sql, emptyArray<String>()).use { cursor ->
        if (cursor.moveToFirst()) cursor.getLong(0) else 0L
    }

    override fun queryStrings(sql: String): List<String> = database.rawQuery(sql, emptyArray<String>()).use { cursor ->
        buildList { while (cursor.moveToNext()) add(cursor.getString(0).orEmpty()) }
    }

    override fun close() = database.close()
}
