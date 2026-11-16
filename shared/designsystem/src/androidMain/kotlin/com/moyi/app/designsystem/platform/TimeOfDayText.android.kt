package com.moyi.app.designsystem.platform

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.util.Calendar
import java.util.TimeZone

// android.text.format.DateFormat.getTimeFormat follows the phone's own "Use 24-hour format"
// switch, which the locale alone does not. Both sides in UTC, so no zone moves the time.
// LocalConfiguration is read so a change of language recomposes the text.
@Composable
actual fun timeOfDayText(hour: Int, minute: Int): String {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(hour, minute, context, configuration) {
        val utc = TimeZone.getTimeZone("UTC")
        val format = DateFormat.getTimeFormat(context).apply { timeZone = utc }
        val at = Calendar.getInstance(utc).apply {
            clear()
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
        }.time
        format.format(at)
    }
}
