package com.moyi.app.data.database

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

/**
 * The app's one SQLite database, over the platform's [SqlDriver]
 * ([platformDatabaseModule]). Every read and write goes through here, off the main
 * thread.
 *
 * Nothing is ever logged: no SQL logging driver is installed, and nothing here
 * prints a row. The rows hold entry text.
 */
class LocalDatabase(private val driver: SqlDriver) {
    private val database = MoyiDatabase(driver)

    /** Runs [block] on the IO dispatcher with the generated queries; SQLDelight's drivers are blocking. */
    suspend fun <T> io(block: MoyiDatabase.() -> T): T = withContext(Dispatchers.IO) { database.block() }

    /**
     * Deletes every row of every table: everything this device keeps about the
     * person. The session calls it when a session ends (any reason) and when a new
     * one starts (FR-124: nothing of one person's may be shown to the next).
     *
     * The tables are read from `sqlite_master`, not listed by hand, so a table added
     * by a later PR (drafts, the outbox, the Archive) is wiped without anyone
     * remembering to add it here. All in one transaction: it all goes, or none.
     */
    suspend fun wipe() = withContext(Dispatchers.IO) {
        database.transaction {
            userTables().forEach { table -> driver.execute(null, "DELETE FROM \"$table\"", 0) }
        }
    }

    /** Every table SQLDelight created; SQLite's own `sqlite_*` tables are not ours to empty. */
    internal fun userTables(): List<String> = driver.executeQuery(
        identifier = null,
        sql = "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'",
        mapper = { cursor ->
            val names = mutableListOf<String>()
            while (cursor.next().value) names += cursor.getString(0)!!
            QueryResult.Value(names)
        },
        parameters = 0,
    ).value

    /** How many rows [table] holds: a count, never a row. For tests that check a wipe. */
    fun rowCount(table: String): Long = driver.executeQuery(
        identifier = null,
        sql = "SELECT COUNT(*) FROM \"$table\"",
        mapper = { cursor -> QueryResult.Value(if (cursor.next().value) cursor.getLong(0) ?: 0L else 0L) },
        parameters = 0,
    ).value
}
