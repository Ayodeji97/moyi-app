package com.moyi.app.feature.bond.settings

import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// java.text, not java.time: minSdk is 24 and there is no core library desugaring.
// Both sides use UTC so the calendar date can never shift by a zone.


internal actual fun dayAndMonth(date: LocalDate): String = format("d MMMM", date)

internal actual fun dayMonthYear(date: LocalDate): String = format("d MMMM yyyy", date)

private val utc: TimeZone = TimeZone.getTimeZone("UTC")

private fun format(pattern: String, date: LocalDate): String =
    SimpleDateFormat(pattern, Locale.getDefault()).apply { timeZone = utc }.format(date.toUtcDate())

private fun LocalDate.toUtcDate(): Date = Calendar.getInstance(utc).apply {
    clear()
    set(year, month.number - 1, day)
}.time
