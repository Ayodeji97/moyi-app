package com.moyi.app.feature.bond.settings

import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetAt
import kotlin.math.abs
import kotlin.time.Instant

/**
 * The signed offset from GMT that [zoneId] has at [at], as the text after "GMT": `+0`,
 * `+5:30`, `-3`. Hours without a leading zero, minutes only when there are some. Null for
 * an id the platform does not know, so the row falls back to the city alone.
 *
 * Uses the offset at [at], not a fixed one: London is `+1` in summer.
 */
fun gmtOffsetLabel(zoneId: String, at: Instant): String? {
    val zone = runCatching { TimeZone.of(zoneId) }.getOrNull() ?: return null
    return formatGmtOffset(zone.offsetAt(at).totalSeconds)
}

internal fun formatGmtOffset(totalSeconds: Int): String {
    val sign = if (totalSeconds < 0) "-" else "+"
    val minutes = abs(totalSeconds) / 60
    val hours = minutes / 60
    val rest = minutes % 60
    return if (rest == 0) "$sign$hours" else "$sign$hours:${rest.toString().padStart(2, '0')}"
}
