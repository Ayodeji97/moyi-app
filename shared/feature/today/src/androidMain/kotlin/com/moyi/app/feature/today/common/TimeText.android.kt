package com.moyi.app.feature.today.common

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.moyi.app.domain.bond.TimeOfDay
import java.util.Calendar
import java.util.TimeZone

// android.text.format.DateFormat.getTimeFormat follows the phone's own "Use 24-hour format"
// switch, which the locale alone does not. Both sides in UTC, so no zone moves the time.
@Composable
internal actual fun timeOfDayText(time: TimeOfDay): String {
    val context = LocalContext.current
    return remember(time, context) {
        val utc = TimeZone.getTimeZone("UTC")
        val format = DateFormat.getTimeFormat(context).apply { timeZone = utc }
        val at = Calendar.getInstance(utc).apply {
            clear()
            set(Calendar.HOUR_OF_DAY, time.hour)
            set(Calendar.MINUTE, time.minute)
        }.time
        format.format(at)
    }
}
