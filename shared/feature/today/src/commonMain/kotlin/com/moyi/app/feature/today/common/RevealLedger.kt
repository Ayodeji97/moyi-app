package com.moyi.app.feature.today.common

import kotlinx.datetime.LocalDate

/**
 * Which Bond-days have played their reveal, in this process: the one place that
 * remembers it (a Koin `single`), so the moment plays once per day and never on
 * every refresh of a day already open.
 *
 * **Per person, not per phone.** The key is the signed-in user as well as the bond
 * and the day: two members of one bond can sign in on the same phone in turn, and
 * each is owed their own reveal. Keyed by bond and day alone, the second person's
 * reveal counted as already played (found in the offline-first smoke test,
 * 2026-11-08). [currentUserId] is read at each question, so a sign-in between two
 * questions is seen; with nobody signed in, nothing is recorded and nothing plays.
 *
 * In memory only. A cold start forgets, so a day revealed before the app was
 * killed plays again once on the next launch. Persisting it (doc 13 §5's local
 * store) is owed (docs/figma/today-check.md, "Owed").
 */
class RevealLedger(private val currentUserId: () -> String?) {
    private val shown = mutableSetOf<Key>()

    /**
     * True the first time the signed-in person is asked about [bondId]'s [date], and
     * records it; false after, and false when nobody is signed in.
     */
    fun firstShowing(bondId: String, date: LocalDate): Boolean {
        val user = currentUserId() ?: return false
        return shown.add(Key(user, bondId, date))
    }

    private data class Key(val userId: String, val bondId: String, val date: LocalDate)
}
