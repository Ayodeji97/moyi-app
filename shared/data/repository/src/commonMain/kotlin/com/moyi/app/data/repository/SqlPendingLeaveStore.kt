package com.moyi.app.data.repository

import com.moyi.app.data.database.LocalDatabase
import com.moyi.app.domain.auth.SessionState
import com.moyi.app.domain.bond.PendingLeave
import com.moyi.app.domain.bond.PendingLeaveStore
import kotlinx.coroutines.flow.StateFlow

/**
 * [PendingLeaveStore] over the local database's `pending_leave` table, keyed by the signed-in
 * person's id ([session]). Signed out it holds nothing and keeps nothing: there is no one to
 * leave a bond for.
 */
class SqlPendingLeaveStore(
    private val database: LocalDatabase,
    private val session: StateFlow<SessionState>,
) : PendingLeaveStore {
    private val userId: String? get() = (session.value as? SessionState.SignedIn)?.user?.id

    override suspend fun all(): List<PendingLeave> {
        val user = userId ?: return emptyList()
        return database.io { pendingLeaveQueries.forUser(user).executeAsList() }
            .map { PendingLeave(bondId = it.bond_id, joinedBondId = it.joined_bond_id) }
    }

    override suspend fun add(leave: PendingLeave) {
        val user = userId ?: return
        database.io { pendingLeaveQueries.insert(user, leave.bondId, leave.joinedBondId) }
    }

    override suspend fun remove(bondId: String) {
        val user = userId ?: return
        database.io { pendingLeaveQueries.deleteForBond(user, bondId) }
    }
}
