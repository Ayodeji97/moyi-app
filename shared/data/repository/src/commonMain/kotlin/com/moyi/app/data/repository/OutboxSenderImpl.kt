package com.moyi.app.data.repository

import com.moyi.app.core.DataError
import com.moyi.app.core.Result
import com.moyi.app.domain.today.EntryOutbox
import com.moyi.app.domain.today.OutboxPass
import com.moyi.app.domain.today.OutboxSender
import com.moyi.app.domain.today.SendFailure
import com.moyi.app.domain.today.TodayCache
import com.moyi.app.domain.today.TodayRepository
import com.moyi.app.domain.today.sendFailure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.concurrent.Volatile
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Asks the platform to run [OutboxBackgroundRunner] when it can, even if this process
 * dies first: WorkManager on Android, BGTaskScheduler on iOS. Calling it again while a
 * request is already waiting changes nothing.
 */
fun interface OutboxScheduler {
    fun schedule()
}

/** Calls back each time the device gets a usable network connection. */
fun interface ConnectivityWatcher {
    fun watch(onOnline: () -> Unit)
}

/**
 * How long the in-process retry waits between tries: [initial], doubled after each
 * failure, never more than [max]. A connection that comes back (or the app coming to
 * the foreground) cuts the wait short and starts again from [initial].
 */
data class Backoff(
    val initial: Duration = 2.seconds,
    val max: Duration = 5.minutes,
) {
    fun next(current: Duration): Duration = (current * 2).coerceAtMost(max)
}

/**
 * The real [OutboxSender], over [EntryOutbox] and [TodayRepository] (whose authed client
 * owns the one-refresh-at-a-time rule: a `401` here has already been through it).
 *
 * Every send of a queued entry is the queued request again: same key, same body,
 * [com.moyi.app.domain.today.QueuedEntry.intendedAt] included. The server then answers a
 * retry whose first attempt it already took with that attempt's own answer (`201` again,
 * `Idempotency-Replayed: true`), so a lost answer never becomes a second entry.
 *
 * Outcomes, per entry ([sendFailure]):
 * - `2xx`, a replay included: removed. Today reloads ([changes]) and shows what the server holds.
 * - A `2xx` whose body could not be read: the server took it, so it is removed too.
 * - No answer, a timeout, `5xx`, `429`, `409 IDEMPOTENCY_KEY_IN_FLIGHT`: left for the next try.
 * - Any other refusal: never retried. Its words go back to the new-entry draft, and the
 *   refusal waits for Today to say so ([EntryOutbox.refuse]).
 * - `401` (the refresh was refused, so the session ended and its wipe took the queue):
 *   the pass stops.
 *
 * When a pass sends or refuses something it also forgets the cached day ([TodayCache]):
 * that copy was taken before the send, so a launch whose first refresh fails would
 * otherwise draw "Write" under an entry the server already holds.
 *
 * @param scope where the in-process retry runs: the app's own, for as long as the process lives.
 */
class OutboxSenderImpl(
    private val outbox: EntryOutbox,
    private val entries: TodayRepository,
    private val cache: TodayCache,
    private val scheduler: OutboxScheduler,
    private val connectivity: ConnectivityWatcher,
    private val scope: CoroutineScope,
    private val backoff: Backoff = Backoff(),
) : OutboxSender {
    private val mutex = Mutex()

    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val changes: Flow<Unit> = _changes.asSharedFlow()

    /** A trigger that came while the retry was waiting: it tries at once instead. */
    private val kicks = Channel<Unit>(Channel.CONFLATED)

    @Volatile private var retry: Job? = null

    @Volatile private var started = false

    override fun start() {
        if (started) return
        started = true
        connectivity.watch { retrySoon() }
    }

    override fun retrySoon() {
        if (retry?.isActive == true) {
            kicks.trySend(Unit)
            return
        }
        retry = scope.launch { retryLoop() }
    }

    private suspend fun retryLoop() {
        if (outbox.queued().isEmpty()) return
        // Before the first try: if this process dies mid-wait, the platform still sends it.
        scheduler.schedule()
        var wait = backoff.initial
        while (sendQueued() == OutboxPass.Waiting) {
            val kicked = withTimeoutOrNull(wait) { kicks.receive() } != null
            wait = if (kicked) backoff.initial else backoff.next(wait)
        }
    }

    override suspend fun sendQueued(): OutboxPass = mutex.withLock {
        var changed = false
        try {
            for (entry in outbox.queued()) {
                val result = entries.submitEntry(entry.bondId, entry.text, entry.promptId, entry.idempotencyKey, entry.intendedAt)
                val failure = when (result) {
                    is Result.Success -> null
                    // A 2xx the contract could not read: the server took the entry.
                    is Result.Failure -> if (result.error == DataError.Network.Serialization) null else result.error.sendFailure()
                }
                when (failure) {
                    null -> {
                        outbox.remove(entry.idempotencyKey)
                        changed = true
                    }
                    SendFailure.Transient -> Unit
                    SendFailure.SessionEnded -> return@withLock OutboxPass.SignedOut
                    is SendFailure.Refused -> {
                        outbox.refuse(entry, failure.reason)
                        changed = true
                    }
                }
            }
        } finally {
            if (changed) {
                runCatching { cache.clear() }
                _changes.tryEmit(Unit)
            }
        }
        if (outbox.queued().isEmpty()) OutboxPass.Empty else OutboxPass.Waiting
    }
}
