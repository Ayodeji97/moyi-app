package com.moyi.app.feature.today

import com.moyi.app.feature.today.common.LateEntryGrace
import com.moyi.app.feature.today.common.QueuedDay
import com.moyi.app.feature.today.common.queuedDay
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/** BR-3a as amended (FR-045, 12 h late-entry grace): which day a queued entry counts for if it sends now. */
class QueuedDayTest {
    private val lagos = TimeZone.of("Africa/Lagos")
    private val the13th = LocalDate(2026, 9, 13)
    private val the14th = LocalDate(2026, 9, 14)

    /** 23:30 in Lagos on the 13th. */
    private val lateOnThe13th = Instant.parse("2026-09-13T22:30:00Z")

    @Test
    fun theGraceIsTwelveHours() {
        assertEquals(12.hours, LateEntryGrace)
    }

    @Test
    fun writtenTodayCountsForToday() {
        val tap = Instant.parse("2026-09-14T07:00:00Z")
        assertEquals(QueuedDay.ThisDay(the14th), queuedDay(tap, tap + 1.hours, the14th, lagos, the14th))
    }

    @Test
    fun writtenTodayStillCountsForTodayMoreThanTwelveHoursLater() {
        // 00:30 on the 14th, seen at 22:30 the same day: too old for intendedAt, but the day it sends is the same day.
        val tap = Instant.parse("2026-09-13T23:30:00Z")
        assertEquals(QueuedDay.ThisDay(the14th), queuedDay(tap, tap + 22.hours, the14th, lagos, the14th))
    }

    @Test
    fun anEarlierDayIsNamedUpToTheEndOfTheGraceAndNotAfter() {
        assertEquals(QueuedDay.EarlierDay(the13th), queuedDay(lateOnThe13th, lateOnThe13th + 1.hours, the14th, lagos, the13th))
        assertEquals(QueuedDay.EarlierDay(the13th), queuedDay(lateOnThe13th, lateOnThe13th + 12.hours, the14th, lagos, the13th))
        assertEquals(QueuedDay.DayItSends, queuedDay(lateOnThe13th, lateOnThe13th + 12.hours + 1.milliseconds, the14th, lagos, the13th))
    }

    @Test
    fun aDayShownSinceBeforeMidnightIsNotTakenForToday() {
        // Offline overnight: Today still shows the 13th, but in Lagos it is 01:30 on the 14th.
        assertEquals(QueuedDay.EarlierDay(the13th), queuedDay(lateOnThe13th, lateOnThe13th + 2.hours, the13th, lagos, the13th))
    }

    @Test
    fun withoutAZoneTheShownDayAndTheDayOfTheTapStandIn() {
        assertEquals(QueuedDay.EarlierDay(the13th), queuedDay(lateOnThe13th, lateOnThe13th + 2.hours, the14th, null, the13th))
        assertEquals(QueuedDay.DayItSends, queuedDay(lateOnThe13th, lateOnThe13th + 13.hours, the14th, null, the13th))
        assertEquals(QueuedDay.ThisDay(the14th), queuedDay(lateOnThe13th, lateOnThe13th + 2.hours, the14th, null, null))
    }
}
