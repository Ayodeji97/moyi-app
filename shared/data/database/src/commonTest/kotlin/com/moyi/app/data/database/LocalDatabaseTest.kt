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
        my_entry_reaction = "HEART",
        partner_entry_reaction = null,
        my_photo_id = "m1",
        my_photo_status = "Ready",
        my_photo_width = 1080L,
        my_photo_height = 1440L,
        my_photo_sensitive = 0L,
        my_voice_id = "v1",
        my_voice_status = "Ready",
        my_voice_duration_ms = 12_000L,
        my_voice_sensitive = 0L,
        partner_photo_id = null,
        partner_photo_status = null,
        partner_photo_width = null,
        partner_photo_height = null,
        partner_photo_sensitive = null,
        partner_voice_id = null,
        partner_voice_status = null,
        partner_voice_duration_ms = null,
        partner_voice_sensitive = null,
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

    private val archived = Archive_day(
        user_id = "ada",
        bond_id = "bond-1",
        day_date = "2026-09-13",
        day_status = "REVEALED",
        my_entry_id = "e3",
        my_entry_text = "You carried the shopping.",
        my_entry_status = "REVEALED",
        my_entry_marked = 1L,
        partner_entry_shape = "REVEALED",
        partner_entry_id = "e4",
        partner_entry_text = "You waited up.",
        partner_entry_marked = 0L,
        page_cursor = "cursor-1",
        my_entry_reaction = null,
        partner_entry_reaction = "ME_TOO",
    )

    /**
     * The tables as they were before version 6 (5.sqm), which added each day's reactions:
     * the rows already in them keep everything else. [archive] when archive_day exists.
     */
    private fun app.cash.sqldelight.db.SqlDriver.withoutReactions(archive: Boolean) {
        // Every version before 6 also predates 6.sqm's media columns.
        withoutMedia()
        val tables = if (archive) listOf("cached_day", "archive_day") else listOf("cached_day")
        tables.forEach { table ->
            execute(null, "ALTER TABLE $table DROP COLUMN my_entry_reaction", 0)
            execute(null, "ALTER TABLE $table DROP COLUMN partner_entry_reaction", 0)
        }
    }

    /** cached_day as it was before version 7 (6.sqm), which added the shape of each entry's photo and voice note. */
    private fun app.cash.sqldelight.db.SqlDriver.withoutMedia() {
        listOf("my", "partner").forEach { who ->
            listOf("photo_id", "photo_status", "photo_width", "photo_height", "photo_sensitive", "voice_id", "voice_status", "voice_duration_ms", "voice_sensitive")
                .forEach { execute(null, "ALTER TABLE cached_day DROP COLUMN ${who}_$it", 0) }
        }
    }

    @Test
    fun wipeEmptiesEveryTableItFindsSoATableAddedLaterIsWipedToo() = runTest {
        database.io {
            cachedDayQueries.insert(row("ada"))
            draftQueries.upsert(draft)
            outboxQueries.insert(queued)
            archiveDayQueries.upsert(archived)
            archiveEmptyQueries.markEmpty("ada", "bond-2")
        }
        assertTrue(database.userTables().containsAll(listOf("cached_day", "draft", "outbox_entry", "archive_day", "archive_empty")))

        database.wipe()

        assertNull(database.io { cachedDayQueries.forUser("ada").executeAsOneOrNull() })
        assertEquals(0L, database.rowCount("draft"), "the drafts go with the day")
        assertEquals(0L, database.rowCount("outbox_entry"), "an unsent entry goes too: the session wipe is total")
        assertEquals(0L, database.rowCount("archive_day"), "the Archive's cached days go too")
        assertEquals(0L, database.rowCount("archive_empty"), "and its known-empty marker")
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
        driver.execute(null, "DROP TABLE archive_day", 0)
        driver.execute(null, "DROP TABLE archive_empty", 0)
        val old = LocalDatabase(driver)
        old.io { cachedDayQueries.insert(row("ada")) }
        assertEquals(listOf("cached_day"), old.userTables())
        driver.withoutReactions(archive = false)

        MoyiDatabase.Schema.migrate(driver, oldVersion = 1, newVersion = MoyiDatabase.Schema.version)

        assertEquals(7L, MoyiDatabase.Schema.version)
        old.io { draftQueries.upsert(draft) }
        assertEquals(draft, old.io { draftQueries.forKey("ada", "bond-1", "NEW_ENTRY").executeAsOne() })
        assertEquals(1L, old.rowCount("cached_day"), "the migration keeps what was there")
    }

    @Test
    fun aVersionTwoDatabaseIsMigratedToHoldTheOutboxAndKeepsItsDraft() = runTest {
        // Version 2 (#38) had cached_day and draft: build that, then migrate it as a phone would.
        val driver = inMemoryDriver()
        driver.execute(null, "DROP TABLE outbox_entry", 0)
        driver.execute(null, "DROP TABLE archive_day", 0)
        driver.execute(null, "DROP TABLE archive_empty", 0)
        val old = LocalDatabase(driver)
        old.io {
            cachedDayQueries.insert(row("ada"))
            draftQueries.upsert(draft)
        }
        assertEquals(setOf("cached_day", "draft"), old.userTables().toSet())
        driver.withoutReactions(archive = false)

        MoyiDatabase.Schema.migrate(driver, oldVersion = 2, newVersion = MoyiDatabase.Schema.version)

        old.io { outboxQueries.insert(queued) }
        assertEquals(queued, old.io { outboxQueries.queuedForUser("ada").executeAsOne() })
        assertEquals(draft, old.io { draftQueries.forKey("ada", "bond-1", "NEW_ENTRY").executeAsOne() }, "the draft survives")
        assertEquals(1L, old.rowCount("cached_day"))
    }

    @Test
    fun aVersionThreeDatabaseIsMigratedToHoldTheArchiveAndKeepsEverythingElse() = runTest {
        // Version 3 (#39, #40) had cached_day, draft and outbox_entry: build that, then migrate it.
        val driver = inMemoryDriver()
        driver.execute(null, "DROP TABLE archive_day", 0)
        driver.execute(null, "DROP TABLE archive_empty", 0)
        val old = LocalDatabase(driver)
        old.io {
            cachedDayQueries.insert(row("ada"))
            draftQueries.upsert(draft)
            outboxQueries.insert(queued)
        }
        assertEquals(setOf("cached_day", "draft", "outbox_entry"), old.userTables().toSet())
        driver.withoutReactions(archive = false)

        MoyiDatabase.Schema.migrate(driver, oldVersion = 3, newVersion = MoyiDatabase.Schema.version)

        old.io { archiveDayQueries.upsert(archived) }
        assertEquals(archived, old.io { archiveDayQueries.forDate("ada", "bond-1", "2026-09-13").executeAsOne() })
        listOf("cached_day", "draft", "outbox_entry").forEach { assertEquals(1L, old.rowCount(it), "$it survives") }
    }

    @Test
    fun aVersionFourDatabaseIsMigratedToHoldTheEmptyArchiveMarkerAndKeepsEverythingElse() = runTest {
        // Version 4 (#43) had cached_day, draft, outbox_entry and archive_day: build that, then migrate it.
        val driver = inMemoryDriver()
        driver.execute(null, "DROP TABLE archive_empty", 0)
        val old = LocalDatabase(driver)
        old.io {
            cachedDayQueries.insert(row("ada"))
            draftQueries.upsert(draft)
            outboxQueries.insert(queued)
            archiveDayQueries.upsert(archived)
        }
        assertEquals(setOf("cached_day", "draft", "outbox_entry", "archive_day"), old.userTables().toSet())
        driver.withoutReactions(archive = true)

        MoyiDatabase.Schema.migrate(driver, oldVersion = 4, newVersion = MoyiDatabase.Schema.version)

        old.io { archiveEmptyQueries.markEmpty("ada", "bond-2") }
        assertEquals(1L, old.io { archiveEmptyQueries.isEmpty("ada", "bond-2").executeAsOne() })
        listOf("cached_day", "draft", "outbox_entry", "archive_day").forEach { assertEquals(1L, old.rowCount(it), "$it survives") }
    }

    @Test
    fun aVersionFiveDatabaseIsMigratedToHoldReactionsAndKeepsEveryRow() = runTest {
        // Version 5 (#45) had every table, without the reaction columns: build that, then migrate it.
        val driver = inMemoryDriver()
        val old = LocalDatabase(driver)
        old.io {
            cachedDayQueries.insert(row("ada"))
            draftQueries.upsert(draft)
            outboxQueries.insert(queued)
            archiveDayQueries.upsert(archived)
            archiveEmptyQueries.markEmpty("ada", "bond-2")
        }
        driver.withoutReactions(archive = true)

        MoyiDatabase.Schema.migrate(driver, oldVersion = 5, newVersion = MoyiDatabase.Schema.version)

        listOf("cached_day", "draft", "outbox_entry", "archive_day", "archive_empty").forEach {
            assertEquals(1L, old.rowCount(it), "$it survives")
        }
        // A row cached before has no reaction; the server's next answer fills it in.
        assertNull(old.io { cachedDayQueries.forUser("ada").executeAsOne().my_entry_reaction })
        old.io { archiveDayQueries.setPartnerReaction("MOVED", "ada", "bond-1", "2026-09-13", "e4") }
        assertEquals("MOVED", old.io { archiveDayQueries.forDate("ada", "bond-1", "2026-09-13").executeAsOne().partner_entry_reaction })
        old.io { cachedDayQueries.deleteAll(); cachedDayQueries.insert(row("ada")) }
        assertEquals("HEART", old.io { cachedDayQueries.forUser("ada").executeAsOne().my_entry_reaction })
    }

    @Test
    fun aVersionSixDatabaseIsMigratedToHoldEachEntrysMediaShapeAndKeepsEveryRow() = runTest {
        // Version 6 (#50) had every table and the reactions, without the media columns: build that, then migrate it.
        val driver = inMemoryDriver()
        val old = LocalDatabase(driver)
        old.io {
            cachedDayQueries.insert(row("ada"))
            draftQueries.upsert(draft)
            outboxQueries.insert(queued)
            archiveDayQueries.upsert(archived)
            archiveEmptyQueries.markEmpty("ada", "bond-2")
        }
        driver.withoutMedia()

        MoyiDatabase.Schema.migrate(driver, oldVersion = 6, newVersion = MoyiDatabase.Schema.version)

        listOf("cached_day", "draft", "outbox_entry", "archive_day", "archive_empty").forEach {
            assertEquals(1L, old.rowCount(it), "$it survives")
        }
        // The row cached before keeps its words and reaction, and has no media; the next answer fills it in.
        val migrated = old.io { cachedDayQueries.forUser("ada").executeAsOne() }
        assertEquals("You made the tea.", migrated.my_entry_text)
        assertEquals("HEART", migrated.my_entry_reaction)
        assertNull(migrated.my_photo_id)
        assertNull(migrated.my_voice_duration_ms)
        // And the new columns hold what is written next.
        old.io { cachedDayQueries.deleteAll(); cachedDayQueries.insert(row("ada")) }
        assertEquals(row("ada"), old.io { cachedDayQueries.forUser("ada").executeAsOne() })
    }

    @Test
    fun anArchiveDayIsReadOnlyForItsOwnPersonAndBond() = runTest {
        database.io { archiveDayQueries.upsert(archived) }
        assertNull(database.io { archiveDayQueries.forDate("ben", "bond-1", "2026-09-13").executeAsOneOrNull() })
        assertNull(database.io { archiveDayQueries.forDate("ada", "bond-2", "2026-09-13").executeAsOneOrNull() })
        assertEquals(1, database.io { archiveDayQueries.run("ada", "bond-1").executeAsList() }.size)
    }

    @Test
    fun wipingAnEmptyDatabaseIsFine() = runTest {
        database.wipe()
        database.wipe()
    }
}
