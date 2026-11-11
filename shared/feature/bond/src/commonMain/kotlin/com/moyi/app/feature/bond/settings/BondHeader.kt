package com.moyi.app.feature.bond.settings

import com.moyi.app.designsystem.component.initialOf
import com.moyi.app.domain.bond.BondSettings
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * What the Bond settings header card shows, before it is turned into words: the two
 * initials, the other member's name, and the day the bond began.
 *
 * [since] is the calendar day in the bond's shared zone, not the device's: the day the bond
 * began is the same for both members wherever they are, as every other date of the bond is.
 * Null when the bond carries no start (or an unknown zone falls back to UTC rather than
 * guessing).
 */
data class BondHeader(
    val myInitial: String,
    val partnerName: String?,
    val partnerInitial: String?,
    val since: LocalDate?,
)

fun BondSettings.toHeader(): BondHeader {
    val zone = runCatching { TimeZone.of(anchorTimezone) }.getOrDefault(TimeZone.UTC)
    return BondHeader(
        myInitial = myName?.let(::initialOf).orEmpty(),
        partnerName = partnerName,
        partnerInitial = partnerName?.let(::initialOf),
        since = createdAt?.toLocalDateTime(zone)?.date,
    )
}
