package com.moyi.app.domain.notification

import com.moyi.app.core.DataError
import com.moyi.app.core.Result
import kotlinx.coroutines.flow.Flow

/**
 * The kinds of push a member can turn on or off in one bond (FR-080, FR-081, ADR-0045):
 * the six toggles `/bonds/{id}/members/me/notifications` carries, in the order the
 * settings screen shows them.
 */
enum class NotificationCategory {
    /** The daily reminder to write. */
    Reminder,

    /** The day's entries opened. */
    Reveal,

    /** The other person accepted the invite. */
    PartnerJoined,

    /** 7, 30, 100 or 365 days you both wrote (ADR-0041). */
    Milestone,

    /** An earlier year's entry on this date, at most weekly. Off until turned on (FR-092). */
    OnThisDay,

    /** A change the other member proposed, waiting on you. */
    Proposal,
}

/**
 * The caller's own toggles in one bond, every one of them: a member who never chose is
 * shown the server's defaults. Never the other member's (SKILL.md §3): the route has no
 * member id in it.
 */
data class NotificationPreferences(val on: Set<NotificationCategory>) {
    fun isOn(category: NotificationCategory): Boolean = category in on
}

/**
 * `GET` and `PUT /bonds/{bondId}/members/me/notifications`.
 *
 * A change sends only the one toggle it changes: the server keeps every toggle it is not
 * sent, so a client that does not know a newer toggle never turns it off. Refusals as
 * for the member's settings: `404` (not a member any more), `409 BOND_ARCHIVED`.
 */
interface NotificationSettingsRepository {
    suspend fun preferences(bondId: String): Result<NotificationPreferences, DataError.Network>

    suspend fun change(bondId: String, category: NotificationCategory, on: Boolean): Result<NotificationPreferences, DataError.Network>
}

/**
 * Where this phone's push token comes from: FCM on both platforms, once Firebase is set up.
 *
 * [token] is the current token, or null while the platform has none (no provider yet,
 * not registered yet, or the provider withdrew it). It emits again when the provider
 * rotates the token. A token is a secret device identifier: never log it.
 */
interface PushTokenSource {
    val token: Flow<String?>
}

/**
 * Keeps the server told which push token this device has while someone is signed in
 * here. Started once, by the app shell.
 */
interface PushTokenRegistration {
    fun start()

    /** The app came to the foreground: a registration that failed is tried again. */
    fun retry()
}
