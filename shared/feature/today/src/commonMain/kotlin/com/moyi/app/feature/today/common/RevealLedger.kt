package com.moyi.app.feature.today.common

import kotlinx.datetime.LocalDate

/**
 * Which Bond-days have played their reveal, in this process: the one place that
 * remembers it (a Koin `single`), so the moment plays once per day and never on
 * every refresh of a day already open.
 *
 * In memory only. A cold start forgets, so a day revealed before the app was
 * killed plays again once on the next launch. Persisting it (doc 13 §5's local
 * store) is owed (docs/figma/today-check.md, "Owed").
 */
class RevealLedger {
    private val shown = mutableSetOf<Pair<String, LocalDate>>()

    /** True the first time it is asked about [bondId]'s [date], and records it; false after. */
    fun firstShowing(bondId: String, date: LocalDate): Boolean = shown.add(bondId to date)
}
