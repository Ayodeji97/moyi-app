package com.moyi.app.feature.today.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moyi.app.designsystem.MoyiTheme
import com.moyi.app.designsystem.component.ConfirmDialog
import com.moyi.app.designsystem.component.EntryCard
import com.moyi.app.designsystem.component.EntryCardVariant
import com.moyi.app.designsystem.component.InlineError
import com.moyi.app.designsystem.component.MoyiScaffold
import com.moyi.app.designsystem.component.MoyiText
import com.moyi.app.designsystem.component.PrimaryAction
import com.moyi.app.designsystem.component.SecondaryAction
import com.moyi.app.designsystem.component.StateBlock
import com.moyi.app.designsystem.component.TextRole
import com.moyi.app.designsystem.presentation.ObserveAsEvents
import com.moyi.app.feature.today.common.CenteredContent
import com.moyi.app.feature.today.common.DayNote
import com.moyi.app.feature.today.common.FakeCopy
import com.moyi.app.feature.today.common.NoteAction
import com.moyi.app.feature.today.common.ScreenTitle
import com.moyi.app.feature.today.common.TodayNavRow
import com.moyi.app.feature.today.common.TodayStatus
import com.moyi.app.feature.today.common.dayHeader
import com.moyi.app.feature.today.common.dayInSentence
import com.moyi.app.feature.today.resources.Res
import com.moyi.app.feature.today.resources.today_announce_not_written
import com.moyi.app.feature.today.resources.today_announce_partner_not_joined
import com.moyi.app.feature.today.resources.today_announce_queued
import com.moyi.app.feature.today.resources.today_announce_revealed
import com.moyi.app.feature.today.resources.today_announce_solo
import com.moyi.app.feature.today.resources.today_announce_waiting
import com.moyi.app.feature.today.resources.today_author_partner
import com.moyi.app.feature.today.resources.today_author_you
import com.moyi.app.feature.today.resources.delete_cancel
import com.moyi.app.feature.today.resources.delete_confirm
import com.moyi.app.feature.today.resources.delete_heading
import com.moyi.app.feature.today.resources.delete_line
import com.moyi.app.feature.today.resources.today_delete
import com.moyi.app.feature.today.resources.today_edit
import com.moyi.app.feature.today.resources.today_action_failed
import com.moyi.app.feature.today.resources.today_error_action
import com.moyi.app.feature.today.resources.today_error_heading
import com.moyi.app.feature.today.resources.today_error_line
import com.moyi.app.feature.today.resources.today_partner_pending_line
import com.moyi.app.feature.today.resources.today_partner_pending_line_unnamed
import com.moyi.app.feature.today.resources.today_queued_line
import com.moyi.app.feature.today.resources.today_revealed_title
import com.moyi.app.feature.today.resources.today_share_invite_again
import com.moyi.app.feature.today.resources.today_solo_line
import com.moyi.app.feature.today.resources.today_tombstone_line
import com.moyi.app.feature.today.resources.today_waiting_heading
import com.moyi.app.feature.today.resources.today_waiting_line
import com.moyi.app.feature.today.resources.today_write_action
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun TodayRoot(
    onNavigateToWrite: (editing: Boolean) -> Unit,
    onNavigateToReveal: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNoOpenBond: () -> Unit,
    debugTools: Boolean,
    viewModel: TodayViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Every foreground, and every return from Write or the reveal: the day may have moved on
    // the other phone, and a push is never relied on to have arrived (states.md §10).
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onAction(TodayAction.OnForeground) }
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is TodayEvent.NavigateToWrite -> onNavigateToWrite(event.editing)
            TodayEvent.NavigateToReveal -> onNavigateToReveal()
            TodayEvent.NavigateToSettings -> onNavigateToSettings()
            TodayEvent.NavigateToStart -> onNoOpenBond()
        }
    }
    TodayScreen(state = state, onAction = viewModel::onAction, debugTools = debugTools)
}

/**
 * Today, every state (Figma 159:2, 159:9, 159:19, 188:481, 188:491, 188:501,
 * 384:327, 420:54). The `nav` row is pinned at the top (y 24); the content starts
 * 16 below it (y 88). No back: Today is the root.
 *
 * Before the first answer: the bare `surface` (states.md §0), as the app shows while
 * restoring a session. A first load that failed: the Generic error (Figma 384:341),
 * the StateBlock the signed-in start uses, with Try again.
 *
 * @param debugTools `-PmoyiUiPreview=today` only: a long press on the date steps the preview day.
 */
@Composable
fun TodayScreen(
    state: TodayState,
    onAction: (TodayAction) -> Unit,
    debugTools: Boolean = false,
) {
    when (state.load) {
        TodayLoad.Loading -> Box(Modifier.fillMaxSize().background(MoyiTheme.colors.surface))
        TodayLoad.Failed -> MoyiScaffold(contentArrangement = Arrangement.Center) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                StateBlock(
                    heading = stringResource(Res.string.today_error_heading),
                    line = stringResource(Res.string.today_error_line),
                    actionLabel = stringResource(Res.string.today_error_action),
                    onAction = { onAction(TodayAction.OnRetryClick) },
                )
            }
        }
        TodayLoad.Ready -> TodayDay(state, onAction, debugTools)
    }
}

@Composable
private fun TodayDay(
    state: TodayState,
    onAction: (TodayAction) -> Unit,
    debugTools: Boolean,
) {
    MoyiScaffold(
        header = {
            TodayNavRow(
                date = state.date?.let { dayHeader(it) }.orEmpty(),
                onArchiveClick = { onAction(TodayAction.OnArchiveClick) },
                onSettingsClick = { onAction(TodayAction.OnSettingsClick) },
                onDebugCycle = if (debugTools) ({ onAction(TodayAction.OnDebugCycle) }) else null,
            )
        },
        scrollContent = false,
        action = if (state.status == TodayStatus.NotWritten) {
            { PrimaryAction(text = stringResource(Res.string.today_write_action), onClick = { onAction(TodayAction.OnWriteClick) }) }
        } else {
            null
        },
    ) {
        when (state.status) {
            TodayStatus.NotWritten -> NotWritten(state)
            TodayStatus.Waiting -> Waiting(state, onAction)
            TodayStatus.Revealed -> Revealed(state, onAction, deleted = false)
            TodayStatus.Tombstone -> Revealed(state, onAction, deleted = true)
            TodayStatus.PartnerNotJoined -> PartnerNotJoined(state, onAction)
            TodayStatus.SoloYouWrote -> SoloYouWrote(state)
            TodayStatus.SoloTheyWrote -> SoloTheyWrote(state)
            TodayStatus.Queued -> Queued(state)
        }
    }
    if (state.confirmingDelete) {
        // D11: the platform's confirm, over Today, in place of Figma 420:64's full screen.
        // The line must never become a read receipt (states.md Appendix): it says what is
        // true without reporting on anyone, and that deleting yours never touches theirs.
        ConfirmDialog(
            title = stringResource(Res.string.delete_heading),
            message = stringResource(Res.string.delete_line, state.partnerName.orEmpty()),
            confirmLabel = stringResource(Res.string.delete_confirm),
            dismissLabel = stringResource(Res.string.delete_cancel),
            onConfirm = { onAction(TodayAction.OnDeleteConfirm) },
            onDismiss = { onAction(TodayAction.OnDeleteDismiss) },
            destructive = true,
        )
    }
}

/**
 * 159:2. One job: write. The prompt in the `entry` style 48 below the content's
 * top (y 136), the PrimaryAction pinned at the bottom (y 772). Invitational, no
 * countdown.
 *
 * **"The partner wrote, I haven't" lands here too, on purpose.** Today shows no
 * partner status of any kind (states.md §3; copy.md bans "Tunde hasn't…" and its
 * inverse), so the day where only the partner has written must look exactly like
 * the day where nobody has. A distinct layout would leak the partner's activity.
 */
@Composable
private fun ColumnScope.NotWritten(state: TodayState) {
    val announce = stringResource(Res.string.today_announce_not_written)
    Column(
        Modifier
            .fillMaxWidth()
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .semantics { paneTitle = announce },
    ) {
        Spacer(Modifier.height(MoyiTheme.spacing.xxl))
        // The server's prompt of the day. With none (an ended bond, an empty library), the
        // layout keeps its one job, the Write action, and draws no invented line in its place.
        state.prompt?.let { MoyiText(text = it, role = TextRole.Entry) }
    }
}

/** 159:9 (and PENDING_REVEAL). Complete and calm: my entry and the waiting note, centred. */
@Composable
private fun ColumnScope.Waiting(state: TodayState, onAction: (TodayAction) -> Unit) {
    val announce = stringResource(Res.string.today_announce_waiting)
    CenteredContent(Modifier.semantics { paneTitle = announce }) {
        OwnCard(state)
        ActionFailed(state)
        Spacer(Modifier.height(MoyiTheme.spacing.md))
        WaitingNote(
            partnerName = state.partnerName.orEmpty(),
            onEdit = { onAction(TodayAction.OnEditClick) },
            onDelete = { onAction(TodayAction.OnDeleteBeforeRevealClick) },
        )
    }
}

/**
 * The waiting StateBlock stand-in, shared with Reveal · before (179:7 is the same 224 instance).
 * Reveal passes no handlers: mid-reveal the links would do nothing, so they are not drawn
 * at all rather than drawn as focusable targets that lie about being actions.
 */
@Composable
internal fun WaitingNote(partnerName: String, onEdit: (() -> Unit)?, onDelete: (() -> Unit)?) {
    val edit = stringResource(Res.string.today_edit)
    val delete = stringResource(Res.string.today_delete)
    DayNote(
        heading = stringResource(Res.string.today_waiting_heading),
        line = stringResource(Res.string.today_waiting_line, partnerName),
        // Outlined buttons, Edit in `ink` and Delete in `danger` (owner ruling 2026-11-06,
        // PR 30 smoke test: as underlined links they read as text, not actions). Before the
        // reveal delete is still not irreversible, so it asks no confirmation (states.md §4).
        actions = listOfNotNull(
            onEdit?.let { NoteAction(edit, it, outlined = true) },
            onDelete?.let { NoteAction(delete, it, destructive = true, outlined = true) },
        ),
    )
}

/**
 * 159:19, the settled reveal, and 420:54 once I delete mine ([deleted]).
 * The title under the nav row (y 88), then the partner's entry first and mine
 * second, 16 apart, centred. No metrics, no streak, no next action.
 */
@Composable
private fun ColumnScope.Revealed(state: TodayState, onAction: (TodayAction) -> Unit, deleted: Boolean) {
    val announce = stringResource(Res.string.today_announce_revealed)
    Column(Modifier.semantics { paneTitle = announce }) {
        ScreenTitle(stringResource(Res.string.today_revealed_title))
    }
    Spacer(Modifier.height(MoyiTheme.spacing.md))
    CenteredContent {
        // The partner's words, unless they deleted theirs after the reveal: no frame draws
        // their tombstone on Today yet (owed), so nothing stands in for it.
        if (state.partnerEntry != null) {
            PartnerCard(state)
            Spacer(Modifier.height(MoyiTheme.spacing.md))
        }
        if (deleted) {
            // states.md Appendix: the tombstone is labelled "From you", never the partner.
            EntryCard(
                authorLabel = stringResource(Res.string.today_author_you),
                text = stringResource(Res.string.today_tombstone_line),
                variant = EntryCardVariant.Tombstone,
            )
        } else {
            OwnCard(state)
            ActionFailed(state)
            // Entries are immutable after the reveal: no edit (states.md §5). Delete
            // goes through a confirmation. Placement interpreted: 159:19 draws no link.
            // An outlined `danger` button, not a link (owner ruling 2026-11-06).
            Spacer(Modifier.height(MoyiTheme.spacing.md))
            SecondaryAction(
                text = stringResource(Res.string.today_delete),
                onClick = { onAction(TodayAction.OnDeleteAfterRevealClick) },
                destructive = true,
                enabled = !state.deleting,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** 188:481. The creator writes normally; the entry simply waits (SKILL.md §4). */
@Composable
private fun ColumnScope.PartnerNotJoined(state: TodayState, onAction: (TodayAction) -> Unit) {
    val announce = stringResource(Res.string.today_announce_partner_not_joined)
    CenteredContent(Modifier.semantics { paneTitle = announce }) {
        OwnCard(state)
        Spacer(Modifier.height(MoyiTheme.spacing.md))
        DayNote(
            // Nobody has joined, so the real bond has no one to name yet; the named line
            // (copy.md) is kept for when a name is known. The unnamed one is PROPOSED.
            line = state.partnerName?.let { stringResource(Res.string.today_partner_pending_line, it) }
                ?: stringResource(Res.string.today_partner_pending_line_unnamed),
            actions = listOf(NoteAction(stringResource(Res.string.today_share_invite_again), { onAction(TodayAction.OnShareInviteClick) })),
        )
        // TODO(PR #7): Figma 220:27 places the InviteCode (358 × 195) 16 below this note.
        //  InviteCode is PR #7's component; add it here after the rebase.
    }
}

/** 188:491. The day closed at rollover with only my entry. Factual, forgiving. */
@Composable
private fun ColumnScope.SoloYouWrote(state: TodayState) {
    val announce = stringResource(Res.string.today_announce_solo)
    CenteredContent(Modifier.semantics { paneTitle = announce }) {
        OwnCard(state)
        Spacer(Modifier.height(MoyiTheme.spacing.md))
        DayNote(line = stringResource(Res.string.today_solo_line))
    }
}

/** 188:501. The partner's lone entry, unlocked at rollover (FR-063), and one line. */
@Composable
private fun ColumnScope.SoloTheyWrote(state: TodayState) {
    val announce = stringResource(Res.string.today_announce_solo)
    CenteredContent(Modifier.semantics { paneTitle = announce }) {
        PartnerCard(state)
        Spacer(Modifier.height(MoyiTheme.spacing.md))
        MoyiText(stringResource(Res.string.today_solo_line))
    }
}

/** 384:327. My entry is safe on the device and says which Bond-day it counts for. */
@Composable
private fun ColumnScope.Queued(state: TodayState) {
    val announce = stringResource(Res.string.today_announce_queued)
    CenteredContent(Modifier.semantics { paneTitle = announce }) {
        OwnCard(state)
        Spacer(Modifier.height(MoyiTheme.spacing.md))
        // TODO(logic): the outbox (doc 13 §5, owed with SQLDelight) sends it when the connection
        //  returns, with the Idempotency-Key minted at the tap and `intendedAt` = the Bond-day it
        //  was written for. Until then only the UI preview reaches this layout.
        DayNote(line = stringResource(Res.string.today_queued_line, state.queuedForDate?.let { dayInSentence(it) }.orEmpty()))
    }
}

@Composable
internal fun OwnCard(state: TodayState) {
    EntryCard(authorLabel = stringResource(Res.string.today_author_you), text = state.myEntry)
}

/**
 * A delete that did not go through: the entry is back (or never left), and the Generic
 * error's words say so (states.md §10), in InlineError's `danger` under my card.
 * Placement interpreted: no frame draws a failed delete on Today.
 */
@Composable
private fun ActionFailed(state: TodayState) {
    if (!state.actionFailed) return
    Spacer(Modifier.height(MoyiTheme.spacing.sm))
    InlineError(stringResource(Res.string.today_action_failed))
}

@Composable
internal fun PartnerCard(state: TodayState) {
    EntryCard(
        authorLabel = stringResource(Res.string.today_author_partner, state.partnerName.orEmpty()),
        // Only Revealed, Tombstone and SoloTheyWrote draw this card, and only they hold the words.
        text = state.partnerEntry.orEmpty(),
        variant = EntryCardVariant.Partner,
    )
}

// ---- Previews: every state, dark (the reference) ----

private fun previewState(status: TodayStatus) = TodayState(
    load = TodayLoad.Ready,
    status = status,
    date = if (status == TodayStatus.SoloYouWrote || status == TodayStatus.SoloTheyWrote) FakeCopy.SOLO_DATE else FakeCopy.DATE,
    partnerName = FakeCopy.PARTNER_NAME,
    prompt = FakeCopy.PROMPT,
    myEntry = FakeCopy.MY_ENTRY,
    queuedForDate = FakeCopy.DATE,
    partnerEntry = FakeCopy.PARTNER_ENTRY.takeIf {
        status == TodayStatus.Revealed || status == TodayStatus.Tombstone || status == TodayStatus.SoloTheyWrote
    },
)

@Composable
private fun TodayPreview(status: TodayStatus) {
    MoyiTheme(darkTheme = true) { TodayScreen(state = previewState(status), onAction = {}) }
}

@Preview @Composable private fun TodayNotWrittenPreview() = TodayPreview(TodayStatus.NotWritten)

@Preview @Composable private fun TodayWaitingPreview() = TodayPreview(TodayStatus.Waiting)

@Preview @Composable private fun TodayRevealedPreview() = TodayPreview(TodayStatus.Revealed)

@Preview @Composable private fun TodayTombstonePreview() = TodayPreview(TodayStatus.Tombstone)

@Preview @Composable private fun TodayPartnerNotJoinedPreview() = TodayPreview(TodayStatus.PartnerNotJoined)

@Preview @Composable private fun TodaySoloYouWrotePreview() = TodayPreview(TodayStatus.SoloYouWrote)

@Preview @Composable private fun TodaySoloTheyWrotePreview() = TodayPreview(TodayStatus.SoloTheyWrote)

@Preview @Composable private fun TodayQueuedPreview() = TodayPreview(TodayStatus.Queued)

@Preview
@Composable
private fun TodayLoadFailedPreview() {
    MoyiTheme(darkTheme = true) { TodayScreen(state = TodayState(load = TodayLoad.Failed), onAction = {}) }
}

@Preview
@Composable
private fun TodayRevealedLightPreview() {
    MoyiTheme(darkTheme = false) { TodayScreen(state = previewState(TodayStatus.Revealed), onAction = {}) }
}
