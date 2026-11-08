@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.moyi.app.data.database

import kotlinx.coroutines.test.runTest
import platform.Foundation.NSFileManager
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * On the simulator, through the real file system: the database's directory, and so
 * the database file in it, is excluded from iCloud and Finder backups.
 */
class BackupExclusionTest {
    private val directory = databaseDirectory()!!

    @AfterTest
    fun removeTheFile() {
        listOf("", "-wal", "-shm", "-journal").forEach { NSFileManager.defaultManager.removeItemAtPath("$directory/$DATABASE_NAME$it", null) }
    }

    @Test
    fun theDatabaseLivesInADirectoryExcludedFromBackup() = runTest {
        val driver = iosDatabaseDriver(directory)
        try {
            // Touch the database so the file exists on disk.
            LocalDatabase(driver).wipe()
            val file = "$directory/$DATABASE_NAME"
            assertTrue(NSFileManager.defaultManager.fileExistsAtPath(file), "the database should be a file in $directory")
            assertTrue(isExcludedFromBackup(directory), "the database directory must be excluded from backup")
        } finally {
            driver.close()
        }
    }
}
