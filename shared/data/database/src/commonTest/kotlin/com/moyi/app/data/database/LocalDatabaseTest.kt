package com.moyi.app.data.database

import com.moyi.app.data.testing.inMemoryDatabase
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

    @Test
    fun wipeEmptiesEveryTableItFindsSoATableAddedLaterIsWipedToo() = runTest {
        database.io { cachedDayQueries.insert(row("ada")) }
        assertTrue(database.userTables().contains("cached_day"))

        database.wipe()

        assertNull(database.io { cachedDayQueries.forUser("ada").executeAsOneOrNull() })
        database.userTables().forEach { table -> assertEquals(0L, database.rowCount(table), table) }
    }

    @Test
    fun aRowIsReadOnlyForItsOwnPerson() = runTest {
        database.io { cachedDayQueries.insert(row("ada")) }
        assertNull(database.io { cachedDayQueries.forUser("ben").executeAsOneOrNull() })
        assertEquals("bond-1", database.io { cachedDayQueries.forUser("ada").executeAsOne().bond_id })
    }

    @Test
    fun wipingAnEmptyDatabaseIsFine() = runTest {
        database.wipe()
        database.wipe()
    }
}
