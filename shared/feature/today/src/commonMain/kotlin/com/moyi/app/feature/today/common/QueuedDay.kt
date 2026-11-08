package com.moyi.app.feature.today.common

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * Which Bond-day a queued entry will count for, as far as this device can tell (BR-3a).
 * The server decides, from `intendedAt` and the day it is sent; this only chooses what
 * Today says while the entry waits, and Today shows the server's answer once it is sent.
 *
 * BR-3a uses `intendedAt` only when it is (a) not in the future, (b) at most 36 hours
 * old, and (c) on a Bond-day that has not closed. Every Bond-day closes at its own
 * midnight (the nightly close job settles it as SOLO, EMPTY or REVEALED), and the entry
 * cannot have been revealed with the day because the server never had it. So an entry
 * written on a day that has since ended counts for the day it is sent, whichever limit
 * the server names.
 */
sealed interface QueuedDay {
    /** Written on the Bond-day that is still today: it counts for [date] (states.md §10's line). */
    data class ThisDay(val date: LocalDate) : QueuedDay

    /** Written on [writtenOn], an earlier Bond-day that has closed: it counts for the day it sends. */
    data class EarlierDayClosed(val writtenOn: LocalDate) : QueuedDay

    /** Written more than 36 hours ago (BR-3a's window): it counts for the day it sends. */
    data object DayItSends : QueuedDay
}

/** BR-3a's window: an `intendedAt` older than this is not used at all. */
val OfflineWindow = 36.hours

/**
 * The [QueuedDay] for an entry tapped at [intendedAt], seen at [now] while the bond's
 * current Bond-day is [today].
 *
 * @param zone the bond's anchor time zone; null when unknown, and then [writtenOn] (the
 *   Bond-day Today showed at the tap) stands in for the day [intendedAt] fell on.
 */
fun queuedDay(intendedAt: Instant, now: Instant, today: LocalDate, zone: TimeZone?, writtenOn: LocalDate?): QueuedDay {
    if (now - intendedAt > OfflineWindow) return QueuedDay.DayItSends
    val day = zone?.let { intendedAt.toLocalDateTime(it).date } ?: writtenOn ?: return QueuedDay.ThisDay(today)
    return if (day < today) QueuedDay.EarlierDayClosed(day) else QueuedDay.ThisDay(today)
}
