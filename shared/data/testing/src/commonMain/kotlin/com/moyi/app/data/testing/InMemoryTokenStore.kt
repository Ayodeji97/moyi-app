package com.moyi.app.data.testing

import com.moyi.app.data.session.SecureTokenStore
import com.moyi.app.data.session.StoredSession
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The [SecureTokenStore] tests use. The real stores need a Keychain (which
 * fails with -34018 in unsigned simulator tests) or the Android Keystore
 * (absent on the JVM). Records each write and clear in [log].
 */
class InMemoryTokenStore(
    initial: StoredSession? = null,
    private val log: EventLog = EventLog(),
    /** Called inside [write] before the session is kept, to capture state at that moment. */
    private val onWrite: (StoredSession) -> Unit = {},
) : SecureTokenStore {
    private val mutex = Mutex()
    private var session: StoredSession? = initial

    var failWrites: Boolean = false

    /** When set, [read] throws it (say, a TokenStoreUnavailableException for a locked Keychain). */
    var readFailure: Exception? = null
    var writes: Int = 0
        private set

    val current: StoredSession? get() = session

    override suspend fun read(): StoredSession? = mutex.withLock {
        readFailure?.let { throw it }
        session
    }

    override suspend fun write(session: StoredSession) = mutex.withLock {
        if (failWrites) throw IllegalStateException("disk full")
        onWrite(session)
        writes++
        log.add("store.write(${session.refreshToken})")
        this.session = session
    }

    override suspend fun clear() = mutex.withLock {
        log.add("store.clear")
        session = null
    }
}
