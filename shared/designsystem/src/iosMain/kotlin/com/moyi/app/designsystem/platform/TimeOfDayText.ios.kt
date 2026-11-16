package com.moyi.app.designsystem.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarIdentifierGregorian
import platform.Foundation.NSDateComponents
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterNoStyle
import platform.Foundation.NSDateFormatterShortStyle
import platform.Foundation.NSLocale
import platform.Foundation.NSTimeZone
import platform.Foundation.currentLocale
import platform.Foundation.timeZoneForSecondsFromGMT

// NSLocale.currentLocale carries the iPhone's "24-Hour Time" switch as well as its region,
// so the short time style follows both.
@Composable
actual fun timeOfDayText(hour: Int, minute: Int): String =
    remember(hour, minute) { formatTimeOfDay(hour, minute, NSLocale.currentLocale) }

/** [hour]:[minute] in [locale]'s short time style. Both sides in UTC, so no zone moves the time. */
internal fun formatTimeOfDay(hour: Int, minute: Int, locale: NSLocale): String {
    val utc = NSTimeZone.timeZoneForSecondsFromGMT(0)
    val calendar = NSCalendar(calendarIdentifier = NSCalendarIdentifierGregorian).apply { timeZone = utc }
    val components = NSDateComponents().apply {
        year = 2001
        month = 1
        day = 1
        this.hour = hour.toLong()
        this.minute = minute.toLong()
    }
    val date = checkNotNull(calendar.dateFromComponents(components)) { "Not a time of day: $hour:$minute" }
    return NSDateFormatter().apply {
        this.locale = locale
        timeZone = utc
        dateStyle = NSDateFormatterNoStyle
        timeStyle = NSDateFormatterShortStyle
    }.stringFromDate(date)
}
