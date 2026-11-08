package com.moyi.app.feature.today

import com.moyi.app.feature.today.common.RevealLedger
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RevealLedgerTest {
    private val day = LocalDate(2026, 11, 8)

    @Test
    fun playsOncePerPersonPerDay() {
        val ledger = RevealLedger { "amara" }
        assertTrue(ledger.firstShowing("b1", day))
        assertFalse(ledger.firstShowing("b1", day), "never a second time for the same day")
        assertTrue(ledger.firstShowing("b1", day.plusDays(1)), "a new day plays")
    }

    @Test
    fun twoMembersSharingAPhoneEachGetTheirReveal() {
        var signedIn: String? = "amara"
        val ledger = RevealLedger { signedIn }
        assertTrue(ledger.firstShowing("b1", day))
        signedIn = "tunde"
        assertTrue(ledger.firstShowing("b1", day), "the second person's reveal is theirs, not already played")
        signedIn = "amara"
        assertFalse(ledger.firstShowing("b1", day), "the first person's is still remembered")
    }

    @Test
    fun nobodySignedInRecordsNothing() {
        var signedIn: String? = null
        val ledger = RevealLedger { signedIn }
        assertFalse(ledger.firstShowing("b1", day))
        signedIn = "amara"
        assertTrue(ledger.firstShowing("b1", day), "a question asked while signed out did not use up the reveal")
    }

    private fun LocalDate.plusDays(n: Int) = LocalDate.fromEpochDays(toEpochDays() + n)
}
