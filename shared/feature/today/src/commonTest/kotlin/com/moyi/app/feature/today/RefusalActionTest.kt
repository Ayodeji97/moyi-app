package com.moyi.app.feature.today

import com.moyi.app.domain.today.OutboxRefusal
import com.moyi.app.feature.today.common.TodayStatus
import com.moyi.app.feature.today.today.offersOpenWrite
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** When a refused queued entry offers "Open Write" (owner ruling 2026-11-08, strings-proposed §12). */
class RefusalActionTest {

    @Test
    fun alreadyWrittenNeverOffersOpenWrite() {
        // The day has its entry: Send could only fail again with 409.
        TodayStatus.entries.forEach { status ->
            assertFalse(offersOpenWrite(OutboxRefusal.AlreadyWritten, status), "AlreadyWritten in $status")
        }
    }

    @Test
    fun notAcceptedDoesNotDuplicateTodaysOwnWrite() {
        assertFalse(offersOpenWrite(OutboxRefusal.NotAccepted, TodayStatus.NotWritten))
    }

    @Test
    fun notAcceptedOffersOpenWriteWhereTodayHasNoWrite() {
        TodayStatus.entries.filter { it != TodayStatus.NotWritten }.forEach { status ->
            assertTrue(offersOpenWrite(OutboxRefusal.NotAccepted, status), "NotAccepted in $status")
        }
    }
}
