package com.moyi.app.data.repository

import com.moyi.app.data.database.LocalDatabase
import com.moyi.app.data.database.Shared_settings_snapshot
import com.moyi.app.domain.auth.SessionState
import com.moyi.app.domain.bond.BondStatus
import com.moyi.app.domain.bond.BondType
import com.moyi.app.domain.bond.PendingNotice
import com.moyi.app.domain.bond.PendingProposal
import com.moyi.app.domain.bond.PendingRequest
import com.moyi.app.domain.bond.SharedSettingNotice
import com.moyi.app.domain.bond.SharedSettingsSnapshot
import com.moyi.app.domain.bond.SharedSettingsStore
import com.moyi.app.domain.bond.TimeOfDay
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Instant

/**
 * [SharedSettingsStore] over the local database's `shared_settings_snapshot` and
 * `shared_settings_notice` tables, keyed by the signed-in person's id ([session]). Signed out
 * it holds nothing and keeps nothing. The session wipes both tables with everything else, so
 * the first read after signing in only remembers.
 *
 * A row this build cannot read (an enum name it does not know) counts as none: the next read
 * remembers afresh, and a note it cannot read is skipped rather than guessed at.
 */
class SqlSharedSettingsStore(
    private val database: LocalDatabase,
    private val session: StateFlow<SessionState>,
) : SharedSettingsStore {
    private val userId: String? get() = (session.value as? SessionState.SignedIn)?.user?.id

    override suspend fun snapshot(bondId: String): SharedSettingsSnapshot? {
        val user = userId ?: return null
        return database.io { sharedSettingsQueries.snapshotFor(user, bondId).executeAsOneOrNull() }?.toDomain()
    }

    override suspend fun save(snapshot: SharedSettingsSnapshot, notices: List<SharedSettingNotice>) {
        val user = userId ?: return
        database.io {
            sharedSettingsQueries.transaction {
                sharedSettingsQueries.upsertSnapshot(snapshot.toRow(user))
                notices.forEach { notice ->
                    val (kind, value) = notice.toColumns()
                    sharedSettingsQueries.insertNotice(user, snapshot.bondId, kind, value)
                }
            }
        }
    }

    override suspend fun notices(bondId: String): List<PendingNotice> {
        val user = userId ?: return emptyList()
        return database.io { sharedSettingsQueries.noticesFor(user, bondId).executeAsList() }
            .mapNotNull { row -> noticeOf(row.kind, row.new_value)?.let { PendingNotice(row.id, it) } }
    }

    override suspend fun removeNotice(bondId: String, id: Long) {
        val user = userId ?: return
        database.io { sharedSettingsQueries.deleteNotice(user, bondId, id) }
    }
}

private fun SharedSettingsSnapshot.toRow(user: String) = Shared_settings_snapshot(
    user_id = user,
    bond_id = bondId,
    status = status.name,
    name = name,
    type = type.name,
    anchor_timezone = anchorTimezone,
    reveal_time = revealTime?.toString(),
    strict_mode = strictMode.toLong(),
    semantic_search = semanticSearchEnabled.toLong(),
    deletion_scheduled = deletionScheduled.toLong(),
    tz_proposal_id = timezoneProposal?.id,
    tz_proposal_zone = timezoneProposal?.value,
    tz_proposal_by_me = timezoneProposal?.byMe?.toLong(),
    tz_proposal_expires_at = timezoneProposal?.expiresAt?.toEpochMilliseconds(),
    deletion_by_me = deletionRequest?.byMe?.toLong(),
    deletion_expires_at = deletionRequest?.expiresAt?.toEpochMilliseconds(),
    semantic_by_me = semanticSearchRequest?.byMe?.toLong(),
    semantic_expires_at = semanticSearchRequest?.expiresAt?.toEpochMilliseconds(),
)

private fun Shared_settings_snapshot.toDomain(): SharedSettingsSnapshot? = SharedSettingsSnapshot(
    bondId = bond_id,
    status = BondStatus.entries.firstOrNull { it.name == status } ?: return null,
    name = name,
    type = BondType.entries.firstOrNull { it.name == type } ?: return null,
    anchorTimezone = anchor_timezone,
    revealTime = reveal_time?.let { TimeOfDay.parse(it) ?: return null },
    strictMode = strict_mode != 0L,
    semanticSearchEnabled = semantic_search != 0L,
    deletionScheduled = deletion_scheduled != 0L,
    timezoneProposal = proposal(tz_proposal_id, tz_proposal_zone, tz_proposal_by_me, tz_proposal_expires_at),
    deletionRequest = request(deletion_by_me, deletion_expires_at),
    semanticSearchRequest = request(semantic_by_me, semantic_expires_at),
)

private fun proposal(id: String?, zone: String?, byMe: Long?, expiresAt: Long?): PendingProposal? =
    if (id != null && zone != null && byMe != null && expiresAt != null) {
        PendingProposal(id, zone, byMe != 0L, Instant.fromEpochMilliseconds(expiresAt))
    } else {
        null
    }

private fun request(byMe: Long?, expiresAt: Long?): PendingRequest? =
    if (byMe != null && expiresAt != null) PendingRequest(byMe != 0L, Instant.fromEpochMilliseconds(expiresAt)) else null

private fun Boolean.toLong(): Long = if (this) 1L else 0L

/** A note as the `kind` and `value` columns hold it. The kinds are stored by name; never renamed. */
private fun SharedSettingNotice.toColumns(): Pair<String, String?> = when (this) {
    is SharedSettingNotice.TimezoneAgreed -> "TIMEZONE_AGREED" to timezone
    is SharedSettingNotice.TimezoneKept -> "TIMEZONE_KEPT" to timezone
    is SharedSettingNotice.TimezoneWithdrawn -> "TIMEZONE_WITHDRAWN" to proposed
    SharedSettingNotice.DeletionAgreed -> "DELETION_AGREED" to null
    SharedSettingNotice.DeletionKeptOpen -> "DELETION_KEPT_OPEN" to null
    SharedSettingNotice.DeletionWithdrawn -> "DELETION_WITHDRAWN" to null
    SharedSettingNotice.DeletionCalledOff -> "DELETION_CALLED_OFF" to null
    SharedSettingNotice.SemanticSearchAgreed -> "SEMANTIC_SEARCH_AGREED" to null
    SharedSettingNotice.SemanticSearchKeptOff -> "SEMANTIC_SEARCH_KEPT_OFF" to null
    SharedSettingNotice.SemanticSearchWithdrawn -> "SEMANTIC_SEARCH_WITHDRAWN" to null
    SharedSettingNotice.SemanticSearchTurnedOff -> "SEMANTIC_SEARCH_TURNED_OFF" to null
    SharedSettingNotice.SemanticSearchAsked -> "SEMANTIC_SEARCH_ASKED" to null
    is SharedSettingNotice.NameChanged -> "NAME_CHANGED" to name
    is SharedSettingNotice.RevealTimeChanged -> "REVEAL_TIME_CHANGED" to time?.toString()
    is SharedSettingNotice.RestDaysChanged -> "REST_DAYS_CHANGED" to on.toString()
    is SharedSettingNotice.TypeChanged -> "TYPE_CHANGED" to type.name
}

private fun noticeOf(kind: String, value: String?): SharedSettingNotice? = when (kind) {
    "TIMEZONE_AGREED" -> value?.let { SharedSettingNotice.TimezoneAgreed(it) }
    "TIMEZONE_KEPT" -> value?.let { SharedSettingNotice.TimezoneKept(it) }
    "TIMEZONE_WITHDRAWN" -> value?.let { SharedSettingNotice.TimezoneWithdrawn(it) }
    "DELETION_AGREED" -> SharedSettingNotice.DeletionAgreed
    "DELETION_KEPT_OPEN" -> SharedSettingNotice.DeletionKeptOpen
    "DELETION_WITHDRAWN" -> SharedSettingNotice.DeletionWithdrawn
    "DELETION_CALLED_OFF" -> SharedSettingNotice.DeletionCalledOff
    "SEMANTIC_SEARCH_AGREED" -> SharedSettingNotice.SemanticSearchAgreed
    "SEMANTIC_SEARCH_KEPT_OFF" -> SharedSettingNotice.SemanticSearchKeptOff
    "SEMANTIC_SEARCH_WITHDRAWN" -> SharedSettingNotice.SemanticSearchWithdrawn
    "SEMANTIC_SEARCH_TURNED_OFF" -> SharedSettingNotice.SemanticSearchTurnedOff
    "SEMANTIC_SEARCH_ASKED" -> SharedSettingNotice.SemanticSearchAsked
    "NAME_CHANGED" -> value?.let { SharedSettingNotice.NameChanged(it) }
    // No value: the day now opens as soon as both have written. A value that is not HH:mm is unreadable.
    "REVEAL_TIME_CHANGED" -> if (value == null) SharedSettingNotice.RevealTimeChanged(null) else TimeOfDay.parse(value)?.let { SharedSettingNotice.RevealTimeChanged(it) }
    "REST_DAYS_CHANGED" -> value?.toBooleanStrictOrNull()?.let { SharedSettingNotice.RestDaysChanged(it) }
    "TYPE_CHANGED" -> BondType.entries.firstOrNull { it.name == value }?.let { SharedSettingNotice.TypeChanged(it) }
    else -> null
}
