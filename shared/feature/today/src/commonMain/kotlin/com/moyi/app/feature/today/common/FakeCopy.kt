package com.moyi.app.feature.today.common

import kotlinx.datetime.LocalDate

/**
 * Stand-in content for the `@Preview` functions and the `-PmoyiUiPreview=today`
 * debug build (`PreviewDailyLoop` in :shared). Never reached by the real app,
 * whose every value comes from the API. Not user-facing copy: stand-ins for the
 * server's data (a member's words, a name, a server prompt, a date), so they are
 * not string resources.
 */
object FakeCopy {
    const val PARTNER_NAME = "Tunde"

    /** "Tuesday · 2 September" once formatted (2 September 2025 was a Tuesday). */
    val DATE: LocalDate = LocalDate(2025, 9, 2)

    /** A solo day is shown after rollover, so it is the day before (188:491). */
    val SOLO_DATE: LocalDate = LocalDate(2025, 9, 1)
    const val PROMPT = "What did Tunde do today that you noticed?"
    const val MY_ENTRY = "The way you sat on the phone with me until I fell asleep."
    const val PARTNER_ENTRY =
        "You stayed up to help me finish it, and never once made me feel slow. I noticed. I always notice."
}
