package com.moyi.app.data.repository

import com.moyi.app.data.session.SessionManager
import com.moyi.app.domain.auth.SessionState
import com.moyi.app.domain.today.OutboxPass
import com.moyi.app.domain.today.OutboxSender

/**
 * What a background task does: WorkManager's worker on Android, the BGTaskScheduler
 * task on iOS. The process may have been started for it alone, with no screen and so no
 * session restored yet; it restores the session first (one refresh, under the session's
 * own lock), then makes one pass.
 *
 * [OutboxPass.Waiting] tells the platform to try again later. A session that cannot be
 * read yet (an iOS Keychain before first unlock) is Waiting too: the words are still
 * there, and the session is not gone.
 */
class OutboxBackgroundRunner(
    private val session: SessionManager,
    private val sender: OutboxSender,
) {
    suspend fun run(): OutboxPass = when (session.restore()) {
        SessionState.SignedOut -> OutboxPass.SignedOut
        SessionState.Unknown -> OutboxPass.Waiting
        is SessionState.SignedIn -> sender.sendQueued()
    }
}
