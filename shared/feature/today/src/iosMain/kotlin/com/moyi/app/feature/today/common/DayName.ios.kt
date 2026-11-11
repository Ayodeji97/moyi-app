package com.moyi.app.feature.today.common

import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarIdentifierGregorian
import platform.Foundation.NSDate
import platform.Foundation.NSDateComponents
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.NSTimeZone
import platform.Foundation.currentLocale
import platform.Foundation.timeZoneForSecondsFromGMT

// A fixed day-first pattern, not a locale template: the order is the design's
// (Figma 159:3), the names are the device's. Both sides use UTC so the calendar
// date can never shift by a zone.

internal actual fun weekdayName(date: LocalDate): String = format("EEEE", date)

internal actual fun dayAndMonth(date: LocalDate): String = format("d MMMM", date)

internal actual fun weekdayShort(date: LocalDate): String = format("EEE", date)

internal actual fun weekdayAndDay(date: LocalDate): String = format("EEEE d", date)

// LLLL: the standalone month name, the form a month takes on its own (Polish, Russian).
internal actual fun monthAndYear(date: LocalDate): String = format("LLLL yyyy", date)

private val utc: NSTimeZone = NSTimeZone.timeZoneForSecondsFromGMT(0)

private val gregorian: NSCalendar = NSCalendar(calendarIdentifier = NSCalendarIdentifierGregorian).apply { timeZone = utc }

private fun format(pattern: String, date: LocalDate): String {
    val formatter = NSDateFormatter().apply {
        locale = NSLocale.currentLocale
        calendar = gregorian
        timeZone = utc
        dateFormat = pattern
    }
    return formatter.stringFromDate(date.toNsDate())
}

private fun LocalDate.toNsDate(): NSDate {
    val components = NSDateComponents().apply {
        year = this@toNsDate.year.toLong()
        month = this@toNsDate.month.number.toLong()
        day = this@toNsDate.day.toLong()
    }
    return checkNotNull(gregorian.dateFromComponents(components)) { "not a Gregorian date" }
}
