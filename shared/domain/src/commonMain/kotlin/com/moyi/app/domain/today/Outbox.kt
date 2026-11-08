package com.moyi.app.domain.today

import com.moyi.app.core.ApiErrorCode
import com.moyi.app.core.DataError
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

/**
 * A new entry the person sent while the server could not be reached, kept on this
 * device until it can be (doc 13 §5: entry submission is queued locally).
 *
 * Every retry sends exactly the same request: the same [idempotencyKey] (minted at the
 * Send tap) and the same body, [intendedAt] included. The server binds a key to a hash
 * of the body (doc 06 §1), so a retry whose body differed by a millisecond would be
 * refused as `422 IDEMPOTENCY_KEY_REUSED` instead of being replayed.
 *
 * @param intendedAt the instant of the Send tap, in whole milliseconds (BR-3a, FR-045).
 * @param dayDate the Bond-day (ISO date) Today showed at the tap.
 */
data class QueuedEntry(
    val bondId: String,
    val text: String,
    val promptId: String?,
    val idempotencyKey: String,
    val intendedAt: Instant,
    val dayDate: String,
) {
    /** Never the words: a data class would print them into any log that printed this. */
    override fun toString(): String =
        "QueuedEntry(bondId=$bondId, promptId=$promptId, intendedAt=$intendedAt, dayDate=$dayDate, text=(redacted))"
}

/** Why the server refused a queued entry. Its words went back to the new-entry draft either way. */
enum class OutboxRefusal {
    /**
     * That day already holds my entry (written from another phone): `409 ENTRY_ALREADY_EXISTS`,
     * or `409 DAY_CLOSED` once both had written and the day opened. A queued entry meets a
     * closed day only then: BR-3a files it under today whenever its own day has closed.
     */
    AlreadyWritten,

    /** Any other refusal (`422`, `404`, `409 BOND_ARCHIVED`, another `4xx`): sending again cannot help. */
    NotAccepted,
}

/** A refusal Today has not shown yet. It carries no words: those are in the draft. */
data class RefusedEntry(
    val bondId: String,
    val reason: OutboxRefusal,
    /** The Bond-day Today showed when the entry was queued. */
    val dayDate: String,
)

/**
 * The queue of new entries waiting for a connection, on this device only. Only new
 * entries are queued: an edit or a delete needs the server's answer to be honest, so
 * those stay online-only.
 *
 * Scoped to the person signed in, and wiped with everything else whenever a session ends
 * or starts. Signed out, or still deciding, there is nobody: reads are empty and writes
 * do nothing. Nothing here may be logged: a queued entry is entry text.
 */
interface EntryOutbox {
    suspend fun enqueue(entry: QueuedEntry)

    /** Every entry still waiting, oldest first. */
    suspend fun queued(): List<QueuedEntry>

    /** The entry waiting in [bondId], or null. */
    suspend fun queuedFor(bondId: String): QueuedEntry?

    /** The server has it (or replayed it): it leaves the queue. */
    suspend fun remove(idempotencyKey: String)

    /**
     * The server refused it. In one transaction its words become the new-entry draft of
     * its bond (added after any draft already there, never over it), and it is kept only
     * as a [RefusedEntry], without its words, until Today has said so.
     */
    suspend fun refuse(entry: QueuedEntry, reason: OutboxRefusal)

    /** The refusal in [bondId] Today should still mention, or null. */
    suspend fun refusalFor(bondId: String): RefusedEntry?

    /** Today said it, and the person went to the words (or the day moved on). */
    suspend fun clearRefusal(bondId: String)
}

/**
 * Sends what [EntryOutbox] holds (doc 13 §5, the background worker's job). One sender
 * per process: every pass runs under one lock, so the app and a background task never
 * send the same key at once (the server would answer the second `409
 * IDEMPOTENCY_KEY_IN_FLIGHT`).
 */
interface OutboxSender {
    /** Emits after a pass that sent or refused something, so Today can show what the server holds now. */
    val changes: Flow<Unit>

    /**
     * Starts the in-process triggers: a send whenever the connection comes back. Called
     * once by the app shell; calling it again does nothing.
     */
    fun start()

    /**
     * Tries now and, while the failure is only the connection, again with exponential
     * backoff for as long as this process lives. Also asks the platform's background
     * scheduler to try, in case the process does not live that long. Returns at once.
     */
    fun retrySoon()

    /** One pass over the queue, now, without waiting between tries. */
    suspend fun sendQueued(): OutboxPass
}

/** What a pass left behind. */
enum class OutboxPass {
    /** Nothing is waiting any more: every entry was sent or refused. */
    Empty,

    /** At least one entry is still waiting for a connection (or for the server to recover). */
    Waiting,

    /** The session ended (its refresh was refused): the wipe takes the queue with it. */
    SignedOut,
}

/** What a failed send of a new entry means for the queue. */
sealed interface SendFailure {
    /** Worth trying again with the same key: no answer, a timeout, the server's own failure, a rate limit. */
    data object Transient : SendFailure

    /** `401` after the refresh: the session is gone. */
    data object SessionEnded : SendFailure

    /** The server answered and will answer the same again: never retried. */
    data class Refused(val reason: OutboxRefusal) : SendFailure
}

/**
 * Classifies a failed `POST /bonds/{id}/entries` (doc 06 §2). Only the connection and the
 * server's own trouble are transient; every other `4xx` is an answer.
 *
 * A `409 IDEMPOTENCY_KEY_IN_FLIGHT` is transient: the same key is still being handled (a
 * request whose answer was lost), and the next try is answered with its result.
 */
fun DataError.Network.sendFailure(): SendFailure = when (this) {
    DataError.Network.NoInternet,
    DataError.Network.Timeout,
    DataError.Network.Unknown,
    is DataError.Network.Server,
    -> SendFailure.Transient

    // A 2xx whose body could not be read: the server took it. The caller treats it as sent.
    DataError.Network.Serialization -> SendFailure.Transient

    is DataError.Network.Api -> when {
        problem.status == HTTP_UNAUTHORIZED -> SendFailure.SessionEnded
        problem.status >= HTTP_SERVER_ERROR || problem.status == HTTP_TOO_MANY_REQUESTS -> SendFailure.Transient
        problem.code == ApiErrorCode.IDEMPOTENCY_KEY_IN_FLIGHT -> SendFailure.Transient
        problem.code == ApiErrorCode.ENTRY_ALREADY_EXISTS || problem.code == ApiErrorCode.DAY_CLOSED ->
            SendFailure.Refused(OutboxRefusal.AlreadyWritten)
        else -> SendFailure.Refused(OutboxRefusal.NotAccepted)
    }
}

/** True when this failure means the request never reached a server that could answer: queue it. */
fun DataError.Network.isConnectionFailure(): Boolean = sendFailure() == SendFailure.Transient &&
    this != DataError.Network.Serialization

private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_TOO_MANY_REQUESTS = 429
private const val HTTP_SERVER_ERROR = 500
