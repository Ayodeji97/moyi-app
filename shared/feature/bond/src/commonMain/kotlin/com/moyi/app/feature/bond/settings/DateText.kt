package com.moyi.app.feature.bond.settings

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Day first, then the month, in the device's language, no year ("13 November"). The
 * same rule as Today's `dayAndMonth` (feature/today), which is internal to that module.
 */
internal expect fun dayAndMonth(date: LocalDate): String

/** Day, month and year, in the device's language ("10 November 2026"): when the bond began. */
internal expect fun dayMonthYear(date: LocalDate): String

/** The calendar day [instant] falls on where the device is: when an invite stops working, for the person reading it. */
internal fun localDayAndMonth(instant: Instant): String =
    dayAndMonth(instant.toLocalDateTime(TimeZone.currentSystemDefault()).date)
