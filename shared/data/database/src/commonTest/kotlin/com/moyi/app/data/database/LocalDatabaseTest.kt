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

    private val queued = Outbox_entry(
        idempotency_key = "key-1",
        user_id = "ada",
        bond_id = "bond-1",
        text = "You fixed the gate.",
        prompt_id = null,
        intended_at = 1_789_000_000_123L,
        day_date = "2026-09-14",
        refused = null,
    )

    @Test
    fun wipeEmptiesEveryTableItFindsSoATableAddedLaterIsWipedToo() = runTest {
        database.io {
            cachedDayQueries.insert(row("ada"))
            draftQueries.upsert(draft)
            outboxQueries.insert(queued)
        }
        assertTrue(database.userTables().containsAll(listOf("cached_day", "draft", "outbox_entry")))

        database.wipe()

        assertNull(database.io { cachedDayQueries.forUser("ada").executeAsOneOrNull() })
        assertEquals(0L, database.rowCount("draft"), "the drafts go with the day")
        assertEquals(0L, database.rowCount("outbox_entry"), "an unsent entry goes too: the session wipe is total")
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
        driver.execute(null, "DROP TABLE outbox_entry", 0)
        val old = LocalDatabase(driver)
        old.io { cachedDayQueries.insert(row("ada")) }
        assertEquals(listOf("cached_day"), old.userTables())

        MoyiDatabase.Schema.migrate(driver, oldVersion = 1, newVersion = MoyiDatabase.Schema.version)

        assertEquals(3L, MoyiDatabase.Schema.version)
        old.io { draftQueries.upsert(draft) }
        assertEquals(draft, old.io { draftQueries.forKey("ada", "bond-1", "NEW_ENTRY").executeAsOne() })
        assertEquals(1L, old.rowCount("cached_day"), "the migration keeps what was there")
    }

    @Test
    fun aVersionTwoDatabaseIsMigratedToHoldTheOutboxAndKeepsItsDraft() = runTest {
        // Version 2 (#38) had cached_day and draft: build that, then migrate it as a phone would.
        val driver = inMemoryDriver()
        driver.execute(null, "DROP TABLE outbox_entry", 0)
        val old = LocalDatabase(driver)
        old.io {
            cachedDayQueries.insert(row("ada"))
            draftQueries.upsert(draft)
        }
        assertEquals(setOf("cached_day", "draft"), old.userTables().toSet())

        MoyiDatabase.Schema.migrate(driver, oldVersion = 2, newVersion = MoyiDatabase.Schema.version)

        old.io { outboxQueries.insert(queued) }
        assertEquals(queued, old.io { outboxQueries.queuedForUser("ada").executeAsOne() })
        assertEquals(draft, old.io { draftQueries.forKey("ada", "bond-1", "NEW_ENTRY").executeAsOne() }, "the draft survives")
        assertEquals(1L, old.rowCount("cached_day"))
    }

    @Test
    fun wipingAnEmptyDatabaseIsFine() = runTest {
        database.wipe()
        database.wipe()
    }
}
