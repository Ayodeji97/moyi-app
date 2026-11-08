package com.moyi.app.data.repository

import com.moyi.app.data.database.LocalDatabase
import com.moyi.app.domain.auth.SessionState
import com.moyi.app.domain.today.Draft
import com.moyi.app.domain.today.DraftKind
import com.moyi.app.domain.today.DraftStore
import kotlinx.coroutines.flow.StateFlow
import com.moyi.app.data.database.Draft as DraftRow

/**
 * [DraftStore] over the local database's `draft` table, one row per person, bond and
 * kind, keyed by the signed-in person's id ([session]). It never touches the
 * network: a draft is not the server's (FR-045).
 *
 * A row whose kind this build does not know is no draft, never a guess.
 */
class SqlDraftStore(
    private val database: LocalDatabase,
    private val session: StateFlow<SessionState>,
) : DraftStore {
    private val userId: String? get() = (session.value as? SessionState.SignedIn)?.user?.id

    override suspend fun read(bondId: String, kind: DraftKind): Draft? {
        val user = userId ?: return null
        return database.io { draftQueries.forKey(user, bondId, kind.stored).executeAsOneOrNull() }?.toDraft()
    }

    override suspend fun write(draft: Draft) {
        val row = draft.toRow(userId ?: return)
        database.io { draftQueries.upsert(row) }
    }

    override suspend fun delete(bondId: String, kind: DraftKind) {
        val user = userId ?: return
        database.io { draftQueries.deleteForKey(user, bondId, kind.stored) }
    }
}

private val DraftKind.stored: String
    get() = when (this) {
        DraftKind.NewEntry -> NEW_ENTRY
        DraftKind.Edit -> EDIT
    }

internal fun Draft.toRow(userId: String) = DraftRow(
    user_id = userId,
    bond_id = bondId,
    kind = kind.stored,
    entry_id = entryId,
    day_date = dayDate,
    text = text,
)

internal fun DraftRow.toDraft(): Draft? = Draft(
    bondId = bond_id,
    kind = when (kind) {
        NEW_ENTRY -> DraftKind.NewEntry
        EDIT -> DraftKind.Edit
        else -> return null
    },
    text = text,
    dayDate = day_date,
    entryId = entry_id,
)

private const val NEW_ENTRY = "NEW_ENTRY"
private const val EDIT = "EDIT"
