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
) {
    val restDays: Boolean get() = !strictMode

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
     * The caller's entries are kept: FR-029a's offer to withdraw them is not built.
     */
    suspend fun leave(bondId: String): EmptyResult<DataError.Network>

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
