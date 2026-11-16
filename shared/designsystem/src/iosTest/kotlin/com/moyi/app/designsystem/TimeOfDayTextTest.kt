package com.moyi.app.designsystem

import com.moyi.app.designsystem.platform.formatTimeOfDay
import platform.Foundation.NSLocale
import platform.Foundation.NSTimeZone
import platform.Foundation.defaultTimeZone
import platform.Foundation.setDefaultTimeZone
import platform.Foundation.timeZoneWithName
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The phone's 12 or 24 hour setting reaches the formatter through its locale, so a fixed
 * locale stands in for each setting here: en_US writes twelve hours, en_GB twenty-four.
 */
class TimeOfDayTextTest {

    private val original = NSTimeZone.defaultTimeZone

    // A zone far from UTC: if the formatter read the device's zone, every hour would move.
    @BeforeTest
    fun farFromUtc() = NSTimeZone.setDefaultTimeZone(checkNotNull(NSTimeZone.timeZoneWithName("Pacific/Kiritimati")))

    @AfterTest
    fun restore() = NSTimeZone.setDefaultTimeZone(original)

    private fun format(hour: Int, minute: Int, locale: String): String =
        // iOS puts a narrow no-break space before AM/PM; compare with a plain one.
        formatTimeOfDay(hour, minute, NSLocale(localeIdentifier = locale)).replace(' ', ' ').replace(' ', ' ')

    @Test
    fun twelveHourPhonesGetAmAndPm() {
        assertEquals("9:00 PM", format(21, 0, "en_US"))
        assertEquals("7:05 AM", format(7, 5, "en_US"))
    }

    @Test
    fun twentyFourHourPhonesGetTheHourAsIs() {
        assertEquals("21:00", format(21, 0, "en_GB"))
        assertEquals("07:05", format(7, 5, "en_GB"))
    }

    @Test
    fun midnightAndNoonAreNotShiftedByTheDevicesZone() {
        assertEquals("12:00 AM", format(0, 0, "en_US"))
        assertEquals("12:00 PM", format(12, 0, "en_US"))
        assertEquals("00:00", format(0, 0, "en_GB"))
        assertEquals("23:59", format(23, 59, "en_GB"))
    }
}
