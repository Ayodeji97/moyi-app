package com.moyi.app.domain.bond

import com.moyi.app.core.DataError
import com.moyi.app.core.Result
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Ends the bond nobody joined, once the person has joined someone else's with a code (owner
 * ruling 2026-11-13: joining from an empty bond ends it; there is no bond switcher).
 *
 * The join always comes first (`POST /invites/{code}/accept`); this is called only after it
 * succeeded, so a join that fails changes nothing.
 */
interface EmptyBondExit {
    /**
     * The person joined [joinedBondId] from [emptyBondId]. Remembers the leave on the device
     * first, then tries it ([settle]). Never fails: a leave that could not be made now stays
     * remembered and is tried again on the next [settle].
     */
    suspend fun leaveAfterJoining(emptyBondId: String, joinedBondId: String)

    /**
     * Tries every remembered leave again (at the next Today load, so at the next app start
     * too). Each one is checked against `GET /bonds` first, every time: see [EmptyBondExitImpl].
     */
    suspend fun settle()
}

/** A bond to end because the person joined another: kept on the device until it is done. */
data class PendingLeave(val bondId: String, val joinedBondId: String)

/**
 * The device's list of [PendingLeave]s, for the signed-in person. Local, never the server's;
 * the session wipes it with everything else.
 */
interface PendingLeaveStore {
    suspend fun all(): List<PendingLeave>
    suspend fun add(leave: PendingLeave)
    suspend fun remove(bondId: String)
}

/**
 * [EmptyBondExit] over `GET /bonds` and `POST /bonds/{id}/leave`.
 *
 * **Which bond is left.** Before every leave, the first and every retry, the bonds are read
 * again, and the bond is left only while all of this is still true:
 * - it is not the bond just joined;
 * - it is still waiting for its second member ([BondStatus.PENDING_MEMBER]) with the person
 *   as its only member ([Bond.nobodyHasJoined]); an ACTIVE bond is never left;
 * - the bond joined is still open, so the person is never left with no bond at all.
 *
 * When any of it stops being true the leave is forgotten, not tried. Someone joined the empty
 * bond after all, or it ended on the other phone: there is nothing left to do.
 *
 * **What is kept.** `withdrawEntries` is sent as false: the person's entries in the empty bond
 * are kept (doc 06 §3.3), readable in the export. Leaving also revokes the bond's live invite
 * on the server, so nothing else is called.
 *
 * **When it fails.** No answer, a timeout, the server's own error or a rate limit: kept, and
 * tried again at the next [settle]. Any other refusal (`409 BOND_ARCHIVED`, `404`) is the
 * server saying the bond has already gone: forgotten.
 *
 * Not conditional on the server: there is no "leave only if still waiting" (recorded in
 * docs/backend-contract-gaps.md). A partner joining in the moment between the read and the
 * leave would see the bond end; the read just before keeps that window as small as it can be.
 */
class EmptyBondExitImpl(
    private val bonds: BondRepository,
    private val settings: BondSettingsRepository,
    private val store: PendingLeaveStore,
) : EmptyBondExit {
    /** One pass at a time: a leave from Join and a retry from Today must not both send it. */
    private val mutex = Mutex()

    override suspend fun leaveAfterJoining(emptyBondId: String, joinedBondId: String) {
        if (emptyBondId == joinedBondId) return
        val leave = PendingLeave(emptyBondId, joinedBondId)
        // Remembered before it is tried, so an app killed mid-leave still finishes it later.
        // If the device cannot keep it, it is still tried once now.
        val kept = quietly { store.add(leave) } != null
        mutex.withLock { pass(if (kept) null else listOf(leave)) }
    }

    override suspend fun settle() {
        mutex.withLock { pass(null) }
    }

    /** Call with [mutex] held. [unkept] stands in for the store when it could not be written. */
    private suspend fun pass(unkept: List<PendingLeave>?) {
        val pending = unkept ?: quietly { store.all() } ?: return
        if (pending.isEmpty()) return
        val listed = when (val list = bonds.bonds()) {
            is Result.Failure -> return
            is Result.Success -> list.data
        }
        pending.forEach { leave ->
            if (!shouldLeave(leave, listed)) {
                quietly { store.remove(leave.bondId) }
                return@forEach
            }
            when (val left = settings.leave(leave.bondId, withdrawEntries = false)) {
                is Result.Success -> quietly { store.remove(leave.bondId) }
                is Result.Failure -> if (!left.error.isWorthRetrying()) quietly { store.remove(leave.bondId) }
            }
        }
    }

    private fun shouldLeave(leave: PendingLeave, listed: List<Bond>): Boolean {
        if (leave.bondId == leave.joinedBondId) return false
        val empty = listed.firstOrNull { it.id == leave.bondId } ?: return false
        val joined = listed.firstOrNull { it.id == leave.joinedBondId } ?: return false
        return empty.nobodyHasJoined && joined.isOpen
    }

    /** The device's store is local and should not fail; if it does, the leave is not lost from this pass. */
    private suspend fun <T> quietly(block: suspend () -> T): T? = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }
}

/**
 * True when the leave got no real answer and may work later: no connection, a timeout, the
 * server's own failure, a rate limit, or an expired session. Every other `4xx` is an answer.
 */
private fun DataError.Network.isWorthRetrying(): Boolean = when (this) {
    is DataError.Network.Api -> problem.status >= HTTP_SERVER_ERROR ||
        problem.status == HTTP_TOO_MANY_REQUESTS ||
        problem.status == HTTP_UNAUTHORIZED
    else -> true
}

private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_TOO_MANY_REQUESTS = 429
private const val HTTP_SERVER_ERROR = 500
