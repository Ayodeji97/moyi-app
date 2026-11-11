package com.moyi.app.feature.bond

import com.moyi.app.feature.bond.settings.formatGmtOffset
import com.moyi.app.feature.bond.settings.gmtOffsetLabel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

/** The "GMT+0" half of the shared time zone row ("London · GMT+0"). */
class ZoneLabelTest {
    private val winter = Instant.parse("2026-11-11T12:00:00Z")
    private val summer = Instant.parse("2026-07-11T12:00:00Z")

    @Test
    fun londonIsGmtPlusZeroInWinterAndPlusOneInSummer() {
        assertEquals("+0", gmtOffsetLabel("Europe/London", winter))
        assertEquals("+1", gmtOffsetLabel("Europe/London", summer))
    }

    @Test
    fun lagosIsPlusOneAllYear() {
        assertEquals("+1", gmtOffsetLabel("Africa/Lagos", winter))
        assertEquals("+1", gmtOffsetLabel("Africa/Lagos", summer))
    }

    @Test
    fun minutesShowOnlyWhenThereAreSome() {
        assertEquals("+5:30", gmtOffsetLabel("Asia/Kolkata", winter))
        assertEquals("-3", gmtOffsetLabel("America/Sao_Paulo", winter))
    }

    @Test
    fun aNegativeOffsetWithMinutesKeepsItsSign() {
        assertEquals("-3:30", formatGmtOffset(-(3 * 3600 + 30 * 60)))
        assertEquals("+0", formatGmtOffset(0))
    }

    @Test
    fun anUnknownZoneGivesNoLabelSoTheRowShowsTheCityAlone() {
        assertNull(gmtOffsetLabel("Not/AZone", winter))
    }
}
