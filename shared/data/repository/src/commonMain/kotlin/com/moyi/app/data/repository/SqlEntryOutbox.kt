package com.moyi.app.data.repository

import com.moyi.app.data.database.LocalDatabase
import com.moyi.app.data.database.Outbox_entry
import com.moyi.app.domain.auth.SessionState
import com.moyi.app.domain.today.EntryOutbox
import com.moyi.app.domain.today.OutboxRefusal
import com.moyi.app.domain.today.QueuedEntry
import com.moyi.app.domain.today.RefusedEntry
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Instant
import com.moyi.app.data.database.Draft as DraftRow

/**
 * [EntryOutbox] over the local database's `outbox_entry` table, keyed by the signed-in
 * person's id ([session]). It never touches the network; [OutboxSenderImpl] does.
 *
 * A refusal moves the words to the new-entry draft in the same transaction as it empties
 * the row, so there is no moment, and no crash, at which the words are in neither place.
 */
class SqlEntryOutbox(
    private val database: LocalDatabase,
    private val session: StateFlow<SessionState>,
) : EntryOutbox {
    private val userId: String? get() = (session.value as? SessionState.SignedIn)?.user?.id

    override suspend fun enqueue(entry: QueuedEntry) {
        val user = userId ?: return
        database.io { outboxQueries.insert(entry.toRow(user)) }
    }

    override suspend fun queued(): List<QueuedEntry> {
        val user = userId ?: return emptyList()
        return database.io { outboxQueries.queuedForUser(user).executeAsList() }.map { it.toQueuedEntry() }
    }

    override suspend fun queuedFor(bondId: String): QueuedEntry? {
        val user = userId ?: return null
        return database.io { outboxQueries.queuedForBond(user, bondId).executeAsOneOrNull() }?.toQueuedEntry()
    }

    override suspend fun remove(idempotencyKey: String) {
        val user = userId ?: return
        database.io { outboxQueries.deleteForKey(user, idempotencyKey) }
    }

    override suspend fun refuse(entry: QueuedEntry, reason: OutboxRefusal) {
        val user = userId ?: return
        database.io {
            transaction {
                val existing = draftQueries.forKey(user, entry.bondId, NEW_ENTRY_KIND).executeAsOneOrNull()
                // Never over words already there: both are kept, the older first.
                val text = when {
                    existing == null || existing.text.isBlank() -> entry.text
                    existing.text == entry.text -> existing.text
                    else -> entry.text + "\n\n" + existing.text
                }
                draftQueries.upsert(
                    DraftRow(
                        user_id = user,
                        bond_id = entry.bondId,
                        kind = NEW_ENTRY_KIND,
                        entry_id = null,
                        day_date = existing?.day_date ?: entry.dayDate,
                        text = text,
                    ),
                )
                outboxQueries.markRefused(reason.stored, user, entry.idempotencyKey)
            }
        }
    }

    override suspend fun refusalFor(bondId: String): RefusedEntry? {
        val user = userId ?: return null
        val row = database.io { outboxQueries.refusedForBond(user, bondId).executeAsOneOrNull() } ?: return null
        val reason = OutboxRefusal.entries.firstOrNull { it.stored == row.refused } ?: return null
        return RefusedEntry(row.bond_id, reason, row.day_date)
    }

    override suspend fun clearRefusal(bondId: String) {
        val user = userId ?: return
        database.io { outboxQueries.deleteRefusedForBond(user, bondId) }
    }
}

private val OutboxRefusal.stored: String
    get() = when (this) {
        OutboxRefusal.AlreadyWritten -> "ALREADY_WRITTEN"
        OutboxRefusal.NotAccepted -> "NOT_ACCEPTED"
    }

/** The draft table's name for a new entry (SqlDraftStore's NEW_ENTRY). */
private const val NEW_ENTRY_KIND = "NEW_ENTRY"

internal fun QueuedEntry.toRow(userId: String) = Outbox_entry(
    idempotency_key = idempotencyKey,
    user_id = userId,
    bond_id = bondId,
    text = text,
    prompt_id = promptId,
    intended_at = intendedAt.toEpochMilliseconds(),
    day_date = dayDate,
    refused = null,
)

internal fun Outbox_entry.toQueuedEntry() = QueuedEntry(
    bondId = bond_id,
    text = text,
    promptId = prompt_id,
    idempotencyKey = idempotency_key,
    intendedAt = Instant.fromEpochMilliseconds(intended_at),
    dayDate = day_date,
)
