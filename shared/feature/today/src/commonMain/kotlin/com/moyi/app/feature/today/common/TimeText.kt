package com.moyi.app.feature.today.common

import androidx.compose.runtime.Composable
import com.moyi.app.domain.bond.TimeOfDay

/**
 * [time] as the phone writes a time of day: twelve or twenty-four hours as the person set
 * their phone (owner ruling 2026-11-15), in the device's language. The API's `HH:mm` is
 * never shown as is.
 */
@Composable
internal expect fun timeOfDayText(time: TimeOfDay): String
