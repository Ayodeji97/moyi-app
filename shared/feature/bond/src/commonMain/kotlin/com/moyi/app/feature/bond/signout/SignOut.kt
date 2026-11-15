package com.moyi.app.feature.bond.signout

import androidx.compose.runtime.Composable
import com.moyi.app.designsystem.component.ConfirmDialog
import com.moyi.app.domain.auth.AuthRepository
import com.moyi.app.domain.bond.EmptyBondExit
import com.moyi.app.domain.today.EntryOutbox
import com.moyi.app.domain.today.OutboxSender
import com.moyi.app.feature.bond.resources.Res
import com.moyi.app.feature.bond.resources.settings_sign_out_unsent_confirm
import com.moyi.app.feature.bond.resources.settings_sign_out_unsent_dismiss
import com.moyi.app.feature.bond.resources.settings_sign_out_unsent_line
import com.moyi.app.feature.bond.resources.settings_sign_out_unsent_title
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration.Companion.seconds

/**
 * A voluntary sign-out, the one sequence Bond settings and Account both run (owner ruling
 * 2026-11-09: Account signs out exactly as Bond settings does).
 *
 * Signing out wipes everything on the device (FR-124), entries still waiting to send
 * included. So it first tries to send them (one pass, at most [LAST_TRY_LIMIT]); if any
 * are still waiting, the caller asks before deleting someone's words ([UnsentSignOutQuestion]).
 * Nothing waiting, the caller asks the plain "Sign out?" instead (never both questions). An involuntary end (a refresh refused, a token
 * reused) never comes through here: privacy wins, and the wipe takes them.
 *
 * **A bond left waiting after a join** ([EmptyBondExit]): the wipe also takes the leave kept on
 * the device, which would then never be tried again. So the same moment tries it once more,
 * alongside the entries ([EmptyBondExit.settle], which reads the bonds first and leaves only a
 * bond nobody has joined). Best effort: inside the same [LAST_TRY_LIMIT], never asked about,
 * and a leave that still fails is let go with the session. The empty bond then stays open on
 * the server, as it would have without the join; nothing is lost.
 *
 * Signing out always ends the local session, even offline ([AuthRepository.logout]); the
 * shell routes on the session ending, so the screen needs no event of its own. Offline both
 * tries fail at once, so nothing waits on the network.
 */
internal class SignOut(
    private val auth: AuthRepository,
    private val outbox: EntryOutbox,
    private val sender: OutboxSender,
    private val emptyBonds: EmptyBondExit,
    /** Where the last tries run, outside the caller's wait (the screen's ViewModel scope). */
    private val scope: CoroutineScope,
) {
    /**
     * True when signing out would delete entries: some were waiting, and one more try did not
     * send them all. The caller then asks the unsent question INSTEAD of the plain one.
     * A leave kept from a join is tried in the same moment.
     *
     * **At most [LAST_TRY_LIMIT], hard** (#105). Either try may need a token refresh, and a
     * refresh, once sent, cannot be cancelled: it runs to its end so a rotated refresh token is
     * stored before anything uses the new access token (SessionManager, ADR-0021). Cancelling the
     * caller does not stop it; only the 30 s HTTP timeout does. So the tries run in [scope], and
     * this waits for them, not inside them: at the limit it stops waiting and cancels what can be
     * cancelled. A refresh in flight finishes on its own, under the session's lock, and the
     * sign-out that follows (`logout`, which takes the same lock) waits for it before the wipe.
     */
    suspend fun wouldLoseWords(): Boolean {
        val waiting = stillQueued()
        val tries = scope.launch {
            launch { quietly { emptyBonds.settle() } }
            if (waiting) quietly { sender.sendQueued() }
        }
        if (withTimeoutOrNull(LAST_TRY_LIMIT) { tries.join() } == null) tries.cancel()
        return waiting && stillQueued()
    }

    /** The person chose to sign out anyway: the session, and its wipe, end. */
    suspend fun anyway() {
        auth.logout()
    }

    private suspend fun stillQueued(): Boolean = quietly { outbox.queued().isNotEmpty() } ?: false

    /** A last try never fails sign-out, and must not crash the scope it runs in. */
    private suspend fun <T> quietly(block: suspend () -> T): T? = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
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

/**
 * How long a sign-out waits for its last tries (the waiting entries, and a kept leave, side by
 * side) before it asks.
 */
internal val LAST_TRY_LIMIT = 10.seconds
