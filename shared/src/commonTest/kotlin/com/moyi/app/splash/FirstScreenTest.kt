package com.moyi.app.splash

import com.moyi.app.feature.today.common.DailyLoopState
import com.moyi.app.feature.today.common.DayView
import com.moyi.app.feature.today.common.TodayStatus
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FirstScreenTest {
    private val day = DailyLoopState.Ready(
        DayView(
            bondId = "bond-1",
            date = LocalDate(2026, 11, 9),
            status = TodayStatus.NotWritten,
            partnerName = "Amara",
            prompt = null,
            myEntryId = null,
            myEntry = null,
            partnerEntry = null,
        ),
    )
    private val everyDay = listOf(DailyLoopState.Loading, DailyLoopState.Failed, DailyLoopState.NoOpenBond, day)

    @Test
    fun `nothing is drawn before the NavHost has a screen`() {
        everyDay.forEach { assertFalse(firstScreenDrawn(StartScreen.None, it, startFailed = false), it.toString()) }
    }

    @Test
    fun `the signed-in start is a bare surface while it asks the server`() {
        everyDay.forEach { assertFalse(firstScreenDrawn(StartScreen.SignedInStart, it, startFailed = false), it.toString()) }
    }

    @Test
    fun `the signed-in start error is a real frame`() {
        assertTrue(firstScreenDrawn(StartScreen.SignedInStart, DailyLoopState.Loading, startFailed = true))
    }

    @Test
    fun `Today waits while its day loads and hands over once a day or its error is drawn`() {
        assertFalse(firstScreenDrawn(StartScreen.Today, DailyLoopState.Loading, startFailed = false))
        assertFalse(firstScreenDrawn(StartScreen.Today, DailyLoopState.NoOpenBond, startFailed = false))
        // A cached day is Ready in the first moments (owner ruling 2026-11-08); a fetched one later.
        assertTrue(firstScreenDrawn(StartScreen.Today, day, startFailed = false))
        assertTrue(firstScreenDrawn(StartScreen.Today, DailyLoopState.Failed, startFailed = false))
    }

    @Test
    fun `every other screen draws on arrival`() {
        everyDay.forEach { assertTrue(firstScreenDrawn(StartScreen.Other, it, startFailed = false), it.toString()) }
    }
}
