package com.moyi.app.feature.bond.signout

import androidx.compose.runtime.Composable
import com.moyi.app.designsystem.component.ConfirmDialog
import com.moyi.app.domain.auth.AuthRepository
import com.moyi.app.domain.today.EntryOutbox
import com.moyi.app.domain.today.OutboxSender
import com.moyi.app.feature.bond.resources.Res
import com.moyi.app.feature.bond.resources.settings_sign_out_unsent_confirm
import com.moyi.app.feature.bond.resources.settings_sign_out_unsent_dismiss
import com.moyi.app.feature.bond.resources.settings_sign_out_unsent_line
import com.moyi.app.feature.bond.resources.settings_sign_out_unsent_title
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration.Companion.seconds

/**
 * A voluntary sign-out, the one sequence Bond settings and Account both run (owner ruling
 * 2026-11-09: Account signs out exactly as Bond settings does).
 *
 * Signing out wipes everything on the device (FR-124), entries still waiting to send
 * included. So it first tries to send them (one pass, at most [UNSENT_FLUSH_LIMIT]); if any
 * are still waiting, the caller asks before deleting someone's words ([UnsentSignOutQuestion]).
 * Nothing waiting, the caller asks the plain "Sign out?" instead (never both questions). An involuntary end (a refresh refused, a token
 * reused) never comes through here: privacy wins, and the wipe takes them.
 *
 * Signing out always ends the local session, even offline ([AuthRepository.logout]); the
 * shell routes on the session ending, so the screen needs no event of its own.
 */
internal class SignOut(
    private val auth: AuthRepository,
    private val outbox: EntryOutbox,
    private val sender: OutboxSender,
) {
    /**
     * True when signing out would delete entries: some were waiting, and one more try did not
     * send them all. The caller then asks the unsent question INSTEAD of the plain one.
     */
    suspend fun wouldLoseWords(): Boolean = unsentAfterLastTry()

    /** The person chose to sign out anyway: the session, and its wipe, end. */
    suspend fun anyway() {
        auth.logout()
    }

    /** True when entries would be lost: some were waiting, and one more try did not send them all. */
    private suspend fun unsentAfterLastTry(): Boolean {
        val waiting = runCatching { outbox.queued().isNotEmpty() }.getOrDefault(false)
        if (!waiting) return false
        withTimeoutOrNull(UNSENT_FLUSH_LIMIT) { sender.sendQueued() }
        return runCatching { outbox.queued().isNotEmpty() }.getOrDefault(false)
    }
}

/**
 * A sign-out that would delete words still waiting to send (the session wipe is total).
 * ConfirmDialog with the copy approved 2026-11-08 (strings-proposed.md §12), its safe
 * answer the dismiss. Shared by Bond settings and Account.
 */
@Composable
internal fun UnsentSignOutQuestion(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ConfirmDialog(
        title = stringResource(Res.string.settings_sign_out_unsent_title),
        message = stringResource(Res.string.settings_sign_out_unsent_line),
        confirmLabel = stringResource(Res.string.settings_sign_out_unsent_confirm),
        dismissLabel = stringResource(Res.string.settings_sign_out_unsent_dismiss),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        destructive = true,
    )
}

/** How long a sign-out waits for one last send of the waiting entries before it asks. */
private val UNSENT_FLUSH_LIMIT = 10.seconds
