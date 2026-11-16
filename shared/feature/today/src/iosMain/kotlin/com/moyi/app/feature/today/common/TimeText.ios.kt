package com.moyi.app.feature.today.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.moyi.app.domain.bond.TimeOfDay
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

// The short time style follows the iPhone's "24-Hour Time" switch as well as its language.
// Both sides in UTC, so no zone moves the time.
@Composable
internal actual fun timeOfDayText(time: TimeOfDay): String = remember(time) {
    val utc = NSTimeZone.timeZoneForSecondsFromGMT(0)
    val calendar = NSCalendar(calendarIdentifier = NSCalendarIdentifierGregorian).apply { timeZone = utc }
    val components = NSDateComponents().apply {
        year = 2001
        month = 1
        day = 1
        hour = time.hour.toLong()
        minute = time.minute.toLong()
    }
    val date = checkNotNull(calendar.dateFromComponents(components)) { "not a time of day" }
    NSDateFormatter().apply {
        locale = NSLocale.currentLocale
        timeZone = utc
        dateStyle = NSDateFormatterNoStyle
        timeStyle = NSDateFormatterShortStyle
    }.stringFromDate(date)
}
