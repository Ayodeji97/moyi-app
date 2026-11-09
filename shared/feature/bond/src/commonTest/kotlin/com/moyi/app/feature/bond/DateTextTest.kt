package com.moyi.app.feature.bond

import com.moyi.app.feature.bond.settings.dayAndMonth
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `dayAndMonth`, the settings date (the too-soon refusal's `allowedFrom`, the invite's
 * expiry). The month's name is the device's language, so the test pins only what holds
 * in every language: day first, no year, no ISO form, and the calendar day unmoved.
 */
class DateTextTest {
    @Test
    fun theDayComesFirstWithNoYearAndNoIsoForm() {
        val text = dayAndMonth(LocalDate(2026, 12, 12))
        assertTrue(text.startsWith("12"), text)
        assertFalse(text.contains("2026"), text)
        assertFalse(text.contains("-"), text)
    }

    @Test
    fun theCalendarDayNeverShifts() {
        // A zone shift would turn the 1st into the 31st or the 2nd.
        assertTrue(dayAndMonth(LocalDate(2027, 1, 1)).startsWith("1"))
        assertFalse(dayAndMonth(LocalDate(2027, 1, 1)).startsWith("31"))
        assertTrue(dayAndMonth(LocalDate(2026, 12, 31)).startsWith("31"))
    }

    @Test
    fun differentMonthsReadDifferently() {
        val december = dayAndMonth(LocalDate(2026, 12, 12))
        val january = dayAndMonth(LocalDate(2027, 1, 12))
        assertEquals("12", december.take(2))
        assertFalse(december == january, "$december and $january")
    }
}
