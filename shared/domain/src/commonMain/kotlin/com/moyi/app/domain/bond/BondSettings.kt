package com.moyi.app.domain.bond

import com.moyi.app.core.DataError
import com.moyi.app.core.EmptyResult
import com.moyi.app.core.Result
import kotlin.time.Instant

/**
 * The settings both members share (states.md §8), read with `GET /bonds/{id}`.
 *
 * [version] is the response's `ETag`, the Bond's row version (doc 06 §1). A change
 * sends it back as `If-Match`; if the other member changed something first, the
 * server answers `412 PRECONDITION_FAILED` and the screen reloads instead of
 * overwriting what they did (ADR-0029).
 */
data class BondSettings(
    val bondId: String,
    val name: String,
    val type: BondType,
    val status: BondStatus,
    /** The shared zone (FR-027), an IANA id. Changed only through [BondSettingsRepository.proposeTimezone]. */
    val anchorTimezone: String,
    /** Null: the day opens as soon as both have written (FR-061, states.md §1). */
    val revealTime: TimeOfDay?,
    /** Strict mode disables freezes, so rest days are on exactly when it is off (states.md §8). */
    val strictMode: Boolean,
    val version: String,
    /** A change to the shared zone waiting for the other member's answer (FR-027), or null. */
    val pendingTimezone: PendingTimezoneChange? = null,
    /** The live invite, while there is one (states.md §2). */
    val invite: Invite? = null,
    /** The other member's display name; null while nobody has joined. */
    val partnerName: String? = null,
    /** One member asked to close the box and the other has not yet (FR-028), or null. */
    val pendingDeletion: PendingDeletionRequest? = null,
    /**
     * When the box closes for good: set once both members have asked, which starts the
     * 30-day cooling-off ([BondStatus.PENDING_DELETION]). Null otherwise.
     */
    val deletionScheduledFor: Instant? = null,
    /**
     * The first day the shared zone can change again (FR-027's 30 days), `YYYY-MM-DD` in
     * [anchorTimezone], or null when it can change now. A string because the domain carries
     * no date library; the screen parses it. It may be a day that has since passed (a stale
     * cache), so only the screen, which knows today, decides whether it still blocks.
     */
    val timezoneChangeAllowedFrom: String? = null,
    /** The caller's own display name, from the members list; null when the list does not name them. */
    val myName: String? = null,
    /** When the bond began (`BondResponse.createdAt`): the header's "since". Null only for a source that does not know. */
    val createdAt: Instant? = null,
) {
    val restDays: Boolean get() = !strictMode

    /** Both asked: the box closes on [deletionScheduledFor] unless either calls it off (states.md §9). */
    val coolingOff: Boolean get() = status == BondStatus.PENDING_DELETION || deletionScheduledFor != null

    /** Nobody has joined yet: a proposed zone applies at once, as there is nobody to agree (doc 06). */
    val waitingForPartner: Boolean get() = status == BondStatus.PENDING_MEMBER
}

/**
 * A proposed shared time zone (FR-027, ADR-0030): one member proposes, the other
 * confirms within seven days, either may cancel. [proposedByMe] decides which screen
 * shows: the proposer can only cancel (their own confirmation is a `409`, BR-6).
 */
data class PendingTimezoneChange(
    val id: String,
    val timezone: String,
    val proposedByMe: Boolean,
    val expiresAt: Instant? = null,
)

/**
 * A request to close the box that waits for the other member (FR-028, ADR-0030). The
 * second member's own request is the agreement; [requestedByMe] decides which screen
 * shows, so the one who asked is never offered "agree" to their own request.
 */
data class PendingDeletionRequest(
    val requestedByMe: Boolean,
    /** When the ask lapses if the other member does not ask too. */
    val expiresAt: Instant? = null,
)

/**
 * What one save changes. Null fields are not sent, so they keep their value on the
 * server. There is no way to clear [revealTime] yet: no screen offers it.
 */
data class BondSettingsChange(
    val name: String? = null,
    val type: BondType? = null,
    val revealTime: TimeOfDay? = null,
    val strictMode: Boolean? = null,
)

/** A wall-clock time, `HH:mm` on the wire (doc 06 §1). */
data class TimeOfDay(val hour: Int, val minute: Int) {
    init {
        require(hour in 0..23 && minute in 0..59) { "Not a time of day: $hour:$minute" }
    }

    /** `21:00`: the one shape the API takes and gives. */
    override fun toString(): String = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

    companion object {
        /** Reads `HH:mm`; null for anything else, so a malformed body is never guessed at. */
        fun parse(value: String): TimeOfDay? {
            if (value.length != 5 || value[2] != ':') return null
            val hour = value.substring(0, 2).toIntOrNull() ?: return null
            val minute = value.substring(3).toIntOrNull() ?: return null
            return if (hour in 0..23 && minute in 0..59) TimeOfDay(hour, minute) else null
        }
    }
}

/**
 * The caller's own settings in a bond (`/members/me/settings`). Never the other
 * member's: the route has no member id in it (states.md §8).
 */
data class MemberSettings(
    val reminderTime: TimeOfDay,
    val reminderTimezone: String,
    val quietHoursStart: TimeOfDay? = null,
    val quietHoursEnd: TimeOfDay? = null,
    /** A name for the other member that only the caller sees. */
    val nicknameForOther: String? = null,
)

/**
 * Bond settings (doc 06 §3.3). Every call needs a signed-in member of the bond.
 *
 * The refusals the screens act on, read from [DataError.Network.Api]:
 * - `412 PRECONDITION_FAILED` from [update]: the other member changed the bond first.
 * - `409 BOND_ARCHIVED` from any write: the bond has ended.
 * - `404` from anything: not a member (any more), or no such bond.
 * - `422 VALIDATION_FAILED` naming `name`: blank or longer than 60.
 * - From the time-zone calls (doc 06 §3.3, ADR-0030): `409 PROPOSAL_PENDING` (one is
 *   already open), `409 TIMEZONE_CHANGE_TOO_SOON` (inside 30 days of the last change,
 *   checked at proposal and again at confirmation), `422` (not a region zone), and a
 *   `404` from confirm or cancel when nothing is waiting any more.
 */
interface BondSettingsRepository {
    suspend fun settings(bondId: String): Result<BondSettings, DataError.Network>

    /** `PATCH /bonds/{id}` with `If-Match: [version]`. Answers with the new settings and version. */
    suspend fun update(bondId: String, version: String, change: BondSettingsChange): Result<BondSettings, DataError.Network>

    /**
     * Leaves the bond (`POST /bonds/{id}/leave`): it ends for both, at once (states.md §9).
     * [withdrawEntries] (FR-029a) erases every entry the caller wrote in it, for both
     * members, for good. Always sent explicitly: absent means the opposite on [block].
     * `409 BOND_ARCHIVED` when it had already ended; nothing is withdrawn then.
     */
    suspend fun leave(bondId: String, withdrawEntries: Boolean): EmptyResult<DataError.Network>

    /**
     * Blocks the other member and ends the bond (`POST /bonds/{id}/block`, FR-029): the
     * same `204` as [leave], and the two accounts cannot be paired again. Accepted on a bond
     * that has already ended. [withdrawEntries] as for [leave], always sent explicitly.
     */
    suspend fun block(bondId: String, withdrawEntries: Boolean): EmptyResult<DataError.Network>

    /**
     * `POST /bonds/{id}/deletion-request` (FR-028): the first member's call asks, the other
     * member's call agrees and starts the 30-day cooling-off. Answers with the bond, which
     * says which of the two happened. `409 BOND_ARCHIVED` on any bond that has ended.
     */
    suspend fun requestDeletion(bondId: String): Result<BondSettings, DataError.Network>

    /**
     * `DELETE /bonds/{id}/deletion-request`: either member withdraws the ask, or calls off a
     * cooling-off already running. `404` when nothing was pending.
     */
    suspend fun cancelDeletion(bondId: String): EmptyResult<DataError.Network>

    /**
     * `PATCH /bonds/{id}/timezone`: proposes [timezone] (an IANA id). No `If-Match`. While
     * nobody has joined it applies at once; otherwise it waits for the other member.
     */
    suspend fun proposeTimezone(bondId: String, timezone: String): Result<BondSettings, DataError.Network>

    /** `POST /bonds/{id}/timezone/confirm`: the other member agrees to [proposalId], and the zone moves. */
    suspend fun confirmTimezone(bondId: String, proposalId: String): Result<BondSettings, DataError.Network>

    /** `DELETE /bonds/{id}/timezone`: either member withdraws the waiting proposal. */
    suspend fun cancelTimezoneChange(bondId: String): EmptyResult<DataError.Network>

    suspend fun memberSettings(bondId: String): Result<MemberSettings, DataError.Network>

    /** A full replacement (`PUT`): an absent field is cleared. No `If-Match`, nobody else writes it. */
    suspend fun replaceMemberSettings(bondId: String, settings: MemberSettings): Result<MemberSettings, DataError.Network>
}
