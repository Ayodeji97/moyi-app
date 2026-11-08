package com.moyi.app.data.database

import com.moyi.app.data.testing.inMemoryDatabase
import com.moyi.app.data.testing.inMemoryDriver
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocalDatabaseTest {
    private val database = inMemoryDatabase()

    private fun row(userId: String) = Cached_day(
        user_id = userId,
        bond_id = "bond-1",
        bond_name = "Amara and Tunde",
        bond_type = "COUPLE",
        bond_status = "ACTIVE",
        partner_name = "Tunde",
        anchor_timezone = "Africa/Lagos",
        day_date = "2026-09-14",
        day_status = "REVEALED",
        my_entry_id = "e1",
        my_entry_text = "You made the tea.",
        my_entry_status = "REVEALED",
        partner_entry_shape = "REVEALED",
        partner_entry_id = "e2",
        partner_entry_text = "You listened.",
        prompt_id = null,
        prompt_text = null,
    )

    private val draft = Draft(
        user_id = "ada",
        bond_id = "bond-1",
        kind = "NEW_ENTRY",
        entry_id = null,
        day_date = "2026-09-14",
        text = "You fixed the gate.",
    )

    @Test
    fun wipeEmptiesEveryTableItFindsSoATableAddedLaterIsWipedToo() = runTest {
        database.io {
            cachedDayQueries.insert(row("ada"))
            draftQueries.upsert(draft)
        }
        assertTrue(database.userTables().containsAll(listOf("cached_day", "draft")))

        database.wipe()

        assertNull(database.io { cachedDayQueries.forUser("ada").executeAsOneOrNull() })
        assertEquals(0L, database.rowCount("draft"), "the drafts go with the day")
        database.userTables().forEach { table -> assertEquals(0L, database.rowCount(table), table) }
    }

    @Test
    fun aRowIsReadOnlyForItsOwnPerson() = runTest {
        database.io { cachedDayQueries.insert(row("ada")) }
        assertNull(database.io { cachedDayQueries.forUser("ben").executeAsOneOrNull() })
        assertEquals("bond-1", database.io { cachedDayQueries.forUser("ada").executeAsOne().bond_id })
    }

    @Test
    fun aVersionOneDatabaseIsMigratedToHoldDrafts() = runTest {
        // Version 1 (#37) had only cached_day: build that, then migrate it as a phone would.
        val driver = inMemoryDriver()
        driver.execute(null, "DROP TABLE draft", 0)
        val old = LocalDatabase(driver)
        old.io { cachedDayQueries.insert(row("ada")) }
        assertEquals(listOf("cached_day"), old.userTables())

        MoyiDatabase.Schema.migrate(driver, oldVersion = 1, newVersion = MoyiDatabase.Schema.version)

        assertEquals(2L, MoyiDatabase.Schema.version)
        old.io { draftQueries.upsert(draft) }
        assertEquals(draft, old.io { draftQueries.forKey("ada", "bond-1", "NEW_ENTRY").executeAsOne() })
        assertEquals(1L, old.rowCount("cached_day"), "the migration keeps what was there")
    }

    @Test
    fun wipingAnEmptyDatabaseIsFine() = runTest {
        database.wipe()
        database.wipe()
    }
}
