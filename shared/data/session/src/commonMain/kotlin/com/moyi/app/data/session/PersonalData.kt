package com.moyi.app.data.session

/**
 * Everything this device keeps about the signed-in person apart from the session
 * itself: the local database (cached days now; drafts and the outbox later).
 *
 * [SessionManager] wipes it whenever a session ends, for any reason, and whenever a
 * new one starts, so a different person signing in on the same phone never sees a
 * trace of the last one. Bound by the data layer to the local database; an
 * interface so this module does not depend on the database.
 */
fun interface PersonalData {
    /** Deletes all of it. Must not throw for an empty store. */
    suspend fun wipe()
}
