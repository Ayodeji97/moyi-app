package com.moyi.app.feature.today.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.moyi.app.feature.today.resources.Res
import com.moyi.app.feature.today.resources.today_date_header
import com.moyi.app.feature.today.resources.today_date_in_sentence
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource

/** The weekday in the device's language, standalone ("Tuesday"). */
internal expect fun weekdayName(date: LocalDate): String

/** Day first, then the month, in the device's language, no year ("2 September"). */
internal expect fun dayAndMonth(date: LocalDate): String

/** Day first, then the month and the year, in the device's language ("26 September 2025"): a day outside this year. */
internal expect fun dayMonthAndYear(date: LocalDate): String

/** The weekday in three letters or so, in the device's language ("Wed"): an Archive row's date column. */
internal expect fun weekdayShort(date: LocalDate): String

/** The weekday and the day of the month, in the device's language: "Tuesday 2" (an Archive day card, Figma 223:10). */
internal expect fun weekdayAndDay(date: LocalDate): String

/** The month standing alone, and the year: "September 2026" (an Archive month, Figma 257:16). */
internal expect fun monthAndYear(date: LocalDate): String

/** The Bond-day in Today's nav row and on the reveal: "Tuesday · 2 September" (Figma 159:3). */
@Composable
internal fun dayHeader(date: LocalDate): String {
    val (weekday, dayMonth) = remember(date) { weekdayName(date) to dayAndMonth(date) }
    return stringResource(Res.string.today_date_header, weekday, dayMonth)
}

/**
 * The same day in running text: "Tuesday, 2 September" (states.md §10, Offline). [withYear]
 * adds the year, "Friday, 26 September 2025", for a list with no month headings to say it
 * (Remember when's answer, where a day can be from any year).
 */
@Composable
internal fun dayInSentence(date: LocalDate, withYear: Boolean = false): String {
    val (weekday, dayMonth) = remember(date, withYear) { weekdayName(date) to if (withYear) dayMonthAndYear(date) else dayAndMonth(date) }
    return stringResource(Res.string.today_date_in_sentence, weekday, dayMonth)
}
