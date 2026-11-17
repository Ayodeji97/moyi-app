package com.moyi.app.designsystem

import com.moyi.app.designsystem.component.SnackbarMaxMillis
import com.moyi.app.designsystem.component.SnackbarMinMillis
import com.moyi.app.designsystem.component.snackbarShowMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** How long a note stays (owner ruling 2026-11-17, docs/missing-components.md §87): 6 to 10 s by length. */
class SnackbarShowMillisTest {
    @Test
    fun aShortNoteStaysSixSeconds() {
        assertEquals(SnackbarMinMillis, snackbarShowMillis("Your new password is saved."))
        assertEquals(SnackbarMinMillis, snackbarShowMillis("Your email is already confirmed."))
        assertEquals(6_000L, SnackbarMinMillis)
    }

    @Test
    fun aLongerNoteStaysLongerByItsLength() {
        // 2 s to notice, then 100 ms a character: 50 characters is 7 s.
        assertEquals(7_000L, snackbarShowMillis("x".repeat(50)))
        assertTrue(snackbarShowMillis("x".repeat(60)) > snackbarShowMillis("x".repeat(50)))
    }

    @Test
    fun noNoteStaysLongerThanTenSeconds() {
        assertEquals(SnackbarMaxMillis, snackbarShowMillis("x".repeat(80)))
        assertEquals(SnackbarMaxMillis, snackbarShowMillis("x".repeat(500)))
        assertEquals(10_000L, SnackbarMaxMillis)
    }
}
