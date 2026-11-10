package com.moyi.app.feature.today.common

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * Which Bond-day a queued entry will count for if it sends now, as far as this device can
 * tell (BR-3a). The server decides, from `intendedAt` and the instant it arrives; this only
 * chooses what Today says while the entry waits, and Today shows the server's answer once
 * it is sent.
 *
 * BR-3a as amended on 2026-11-08 (FR-045, backend `DayAssignment` and `LateEntryGrace`,
 * read): the server files an entry on the day `intendedAt` fell on when `intendedAt` is
 * (a) not ahead of the server's clock, (b) at most [LateEntryGrace] old when the entry
 * arrives, and (c) on a day that has not settled. A day settles no sooner than
 * [LateEntryGrace] after it ends (`BondDayResponse.settlesAt`), and `intendedAt` lies inside
 * its day, so (b) always runs out before (c) can: an entry that passes (b) finds its day
 * still open. The deadline for an earlier day is therefore `intendedAt` plus the grace,
 * and `settlesAt` never comes first. Past it, the entry counts for the day it sends.
 */
sealed interface QueuedDay {
    /** Written on the Bond-day that is still today: it counts for [date] (states.md §10's line). */
    data class ThisDay(val date: LocalDate) : QueuedDay

    /**
     * Written on [date], an earlier Bond-day still inside its late-entry grace: sent before
     * the grace runs out, it counts for [date] (states.md "Queued offline", named and announced).
     */
    data class EarlierDay(val date: LocalDate) : QueuedDay

    /** Written on an earlier Bond-day more than [LateEntryGrace] ago: it counts for the day it sends. */
    data object DayItSends : QueuedDay
}

/**
 * BR-3a's late-entry grace (FR-045, amended from 36 h to 12 h by the owner on 2026-11-08):
 * an `intendedAt` older than this when it reaches the server is not used, and a Bond-day
 * settles this long after it ends. The backend's `moyi.gratitude.days.late-entry-grace`
 * defaults to the same twelve hours; the contract does not carry the length itself, only
 * each day's `settlesAt`.
 */
val LateEntryGrace = 12.hours

/**
 * The [QueuedDay] for an entry tapped at [intendedAt], seen at [now] while the Bond-day
 * shown is [today].
 *
 * @param zone the bond's anchor time zone. When known, the current Bond-day and the day
 *   [intendedAt] fell on are both read from the clock in it, so a day shown since before
 *   midnight is not taken for today. When null, [today] stands for the current Bond-day and
 *   [writtenOn] (the Bond-day Today showed at the tap) for the day [intendedAt] fell on.
 */
fun queuedDay(intendedAt: Instant, now: Instant, today: LocalDate, zone: TimeZone?, writtenOn: LocalDate?): QueuedDay {
    val current = zone?.let { now.toLocalDateTime(it).date } ?: today
    val day = zone?.let { intendedAt.toLocalDateTime(it).date } ?: writtenOn ?: return QueuedDay.ThisDay(current)
    return when {
        // Sent now, it lands on the current day whether the server uses intendedAt or not.
        day >= current -> QueuedDay.ThisDay(current)
        now - intendedAt <= LateEntryGrace -> QueuedDay.EarlierDay(day)
        else -> QueuedDay.DayItSends
    }
}
