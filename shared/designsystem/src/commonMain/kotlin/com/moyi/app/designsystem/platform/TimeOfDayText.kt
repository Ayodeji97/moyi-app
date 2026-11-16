package com.moyi.app.designsystem.platform

import androidx.compose.runtime.Composable

/**
 * [hour]:[minute] as the phone writes a time of day: twelve or twenty-four hours as the
 * person set their phone (owner ruling 2026-11-15), in the device's language ("9:00 PM",
 * "21:00"). The API's `HH:mm` is never shown as is.
 *
 * - **Android**: the system "Use 24-hour format" switch (`DateFormat.getTimeFormat`).
 * - **iOS**: the "24-Hour Time" switch and the region (`NSDateFormatter`'s short time style).
 *
 * A wall-clock time, not an instant: no time zone ever moves it.
 */
@Composable
expect fun timeOfDayText(hour: Int, minute: Int): String
