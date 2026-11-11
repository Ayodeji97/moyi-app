package com.moyi.app.feature.bond

import com.moyi.app.designsystem.component.initialOf
import com.moyi.app.domain.bond.BondStatus
import com.moyi.app.domain.bond.BondType
import com.moyi.app.domain.bond.BondSettings
import com.moyi.app.feature.bond.settings.dayMonthYear
import com.moyi.app.feature.bond.settings.toHeader
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class BondHeaderTest {
    private fun bond(
        zone: String = "Europe/London",
        createdAt: Instant? = Instant.parse("2026-11-10T09:00:00Z"),
        myName: String? = "Victor",
        partnerName: String? = "Pax",
    ) = BondSettings(
        bondId = "b",
        name = "Us",
        type = BondType.COUPLE,
        status = BondStatus.ACTIVE,
        anchorTimezone = zone,
        revealTime = null,
        strictMode = false,
        version = "1",
        partnerName = partnerName,
        myName = myName,
        createdAt = createdAt,
    )

    @Test
    fun bothMembersGiveTheirInitials() {
        val header = bond().toHeader()
        assertEquals("V", header.myInitial)
        assertEquals("P", header.partnerInitial)
        assertEquals("Pax", header.partnerName)
    }

    @Test
    fun nobodyJoinedLeavesThePartnerOut() {
        val header = bond(partnerName = null).toHeader()
        assertNull(header.partnerName)
        assertNull(header.partnerInitial)
    }

    @Test
    fun sinceIsTheDayInTheBondsZoneNotInUtc() {
        // 23:30 UTC on the 10th is already the 11th in Lagos (+1).
        val created = Instant.parse("2026-11-10T23:30:00Z")
        assertEquals(LocalDate(2026, 11, 10), bond(zone = "Europe/London", createdAt = created).toHeader().since)
        assertEquals(LocalDate(2026, 11, 11), bond(zone = "Africa/Lagos", createdAt = created).toHeader().since)
    }

    @Test
    fun noStartMeansNoSince() {
        assertNull(bond(createdAt = null).toHeader().since)
    }

    @Test
    fun anUnknownZoneFallsBackToUtcRatherThanFailing() {
        assertEquals(LocalDate(2026, 11, 10), bond(zone = "Not/AZone").toHeader().since)
    }

    @Test
    fun initialsAreUpperCaseAndKeepAnEmojiWhole() {
        assertEquals("P", initialOf(" pax"))
        assertEquals("😀", initialOf("😀 Pax"))
        assertEquals("", initialOf("   "))
    }

    @Test
    fun theFullDateReadsDayMonthYear() {
        val text = dayMonthYear(LocalDate(2026, 11, 10))
        assertTrue(text.startsWith("10"), text)
        assertTrue(text.endsWith("2026"), text)
    }
}
