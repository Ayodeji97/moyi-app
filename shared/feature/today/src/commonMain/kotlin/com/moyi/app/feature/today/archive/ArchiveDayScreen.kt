package com.moyi.app.feature.today.archive

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moyi.app.designsystem.MoyiTheme
import com.moyi.app.designsystem.component.ConfirmDialog
import com.moyi.app.designsystem.component.EntryCard
import com.moyi.app.designsystem.component.EntryCardVariant
import com.moyi.app.designsystem.component.FavouriteToggle
import com.moyi.app.designsystem.component.InlineError
import com.moyi.app.designsystem.component.MoyiScaffold
import com.moyi.app.designsystem.component.StateBlock
import com.moyi.app.designsystem.component.TextLink
import com.moyi.app.designsystem.presentation.ObserveAsEvents
import com.moyi.app.feature.today.common.FakeCopy
import com.moyi.app.feature.today.common.PartnerReaction
import com.moyi.app.feature.today.common.ReactionControl
import com.moyi.app.feature.today.common.dayHeader
import com.moyi.app.feature.today.resources.Res
import com.moyi.app.feature.today.resources.archive_day_report
import com.moyi.app.feature.today.resources.delete_cancel
import com.moyi.app.feature.today.resources.delete_confirm
import com.moyi.app.feature.today.resources.delete_heading
import com.moyi.app.feature.today.resources.delete_line
import com.moyi.app.feature.today.resources.today_action_failed
import com.moyi.app.feature.today.resources.today_author_partner
import com.moyi.app.feature.today.resources.today_author_you
import com.moyi.app.feature.today.resources.today_delete
import com.moyi.app.feature.today.resources.today_error_action
import com.moyi.app.feature.today.resources.today_error_heading
import com.moyi.app.feature.today.resources.today_error_line
import com.moyi.app.feature.today.resources.today_tombstone_line
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun ArchiveDayRoot(
    date: String,
    onBack: () -> Unit,
    onReport: (bondId: String, entryId: String?, partnerName: String?) -> Unit,
    viewModel: ArchiveDayViewModel = koinViewModel { parametersOf(date) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            ArchiveDayEvent.NavigateBack -> onBack()
            is ArchiveDayEvent.NavigateToReport -> onReport(event.bondId, event.entryId, event.partnerName)
        }
    }
    LifecycleResumeEffect(viewModel) {
        viewModel.onAction(ArchiveDayAction.OnResumed)
        onPauseOrDispose {}
    }
    ArchiveDayScreen(state = state, onAction = viewModel::onAction)
}

/**
 * The day view (Figma 438:1462; long entry 243:2). The back target and the day,
 * "Tuesday · 2 September" as on Today, in one row at y 24; the content from 88:
 * the partner's entry, then mine, 16 apart, **in full**, scrolling (states.md §6: a
 * 481-character entry runs to about 2.1 screens). Then "Delete what you wrote" on my
 * live entry of a revealed day, in `danger` with its underline.
 *
 * Under each revealed entry, mine and theirs, **my** FavouriteToggle (664:1939). Under the
 * partner's revealed entry, the interim reaction set; under mine, the partner's reaction
 * (docs/missing-components.md §45: no frame draws either yet).
 *
 * Report in the header, across from the back target (617:1835; `26` §3.2: one discreet
 * header action, never a control under an entry), once there is someone to report.
 */
@Composable
fun ArchiveDayScreen(state: ArchiveDayState, onAction: (ArchiveDayAction) -> Unit) {
    when (state.load) {
        ArchiveLoad.Loading -> Box(Modifier.fillMaxSize().background(MoyiTheme.colors.surface))
        ArchiveLoad.Failed -> MoyiScaffold(onBack = { onAction(ArchiveDayAction.OnBackClick) }, scrollContent = false) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                StateBlock(
                    heading = stringResource(Res.string.today_error_heading),
                    line = stringResource(Res.string.today_error_line),
                    actionLabel = stringResource(Res.string.today_error_action),
                    onAction = { onAction(ArchiveDayAction.OnRetryClick) },
                )
            }
        }
        ArchiveLoad.Ready -> Day(state, onAction)
    }
}

@Composable
private fun Day(state: ArchiveDayState, onAction: (ArchiveDayAction) -> Unit) {
    val day = state.day ?: return
    MoyiScaffold(
        onBack = { onAction(ArchiveDayAction.OnBackClick) },
        backLabel = dayHeader(day.date),
        backAction = if (state.canReport) {
            { TextLink(text = stringResource(Res.string.archive_day_report), onClick = { onAction(ArchiveDayAction.OnReportClick) }) }
        } else {
            null
        },
    ) {
        // TODO(#17): the partner's words are protected from screen capture there (R3);
        //  this EntryCard gains it when #17 merges.
        day.partnerEntry?.let { words ->
            EntryWithMark(
                marked = day.myMarkOnTheirs,
                failed = state.markFailed == FavouriteTarget.Theirs,
                onToggle = { onAction(ArchiveDayAction.OnFavouriteClick(FavouriteTarget.Theirs)) },
            ) {
                EntryCard(
                    authorLabel = stringResource(Res.string.today_author_partner, state.partnerName.orEmpty()),
                    text = words,
                    variant = EntryCardVariant.Partner,
                    photo = day.partnerPhoto,
                    voice = day.partnerVoice,
                )
            }
            // Interim (docs/missing-components.md §45): no frame draws a reaction yet.
            if (day.canReact) {
                ReactionControl(
                    partnerName = state.partnerName.orEmpty(),
                    mine = day.myReaction,
                    failed = state.reactionFailed,
                    onChoose = { onAction(ArchiveDayAction.OnReactionClick(it)) },
                )
            }
        }
        if (day.myEntry != null) {
            EntryWithMark(
                marked = day.myMarkOnMine,
                failed = state.markFailed == FavouriteTarget.Mine,
                onToggle = { onAction(ArchiveDayAction.OnFavouriteClick(FavouriteTarget.Mine)) },
            ) {
                EntryCard(authorLabel = stringResource(Res.string.today_author_you), text = day.myEntry, photo = day.myPhoto, voice = day.myVoice)
                PartnerReaction(reaction = day.partnerReaction, partnerName = state.partnerName.orEmpty(), modifier = Modifier.fillMaxWidth())
            }
        } else if (day.myTombstone) {
            // states.md Appendix: the tombstone is labelled "From you", never the partner.
            EntryCard(
                authorLabel = stringResource(Res.string.today_author_you),
                text = stringResource(Res.string.today_tombstone_line),
                variant = EntryCardVariant.Tombstone,
            )
        }
        if (state.actionFailed) {
            InlineError(stringResource(Res.string.today_action_failed))
        }
        if (day.canDelete) {
            TextLink(
                text = stringResource(Res.string.today_delete),
                onClick = { onAction(ArchiveDayAction.OnDeleteClick) },
                destructive = true,
                enabled = !state.deleting,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(MoyiTheme.spacing.md))
    }
    if (state.confirmingDelete) {
        // D11, as on Today: the platform's confirm, and the line that is never a read receipt.
        ConfirmDialog(
            title = stringResource(Res.string.delete_heading),
            message = stringResource(Res.string.delete_line, state.partnerName.orEmpty()),
            confirmLabel = stringResource(Res.string.delete_confirm),
            dismissLabel = stringResource(Res.string.delete_cancel),
            onConfirm = { onAction(ArchiveDayAction.OnDeleteConfirm) },
            onDismiss = { onAction(ArchiveDayAction.OnDeleteDismiss) },
            destructive = true,
        )
    }
}

/**
 * One entry and, under it, **my** FavouriteToggle (438:1503: the card, then the toggle 8
 * under it, start-aligned). [marked] null is the toggle **absent** (states.md §6: before
 * the reveal it is not there at all, not disabled). A failed toggle is already back as
 * it was; the Generic failure line under it says so.
 */
@Composable
private fun EntryWithMark(
    marked: Boolean?,
    failed: Boolean,
    onToggle: () -> Unit,
    entry: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(MoyiTheme.spacing.sm)) {
        entry()
        if (marked != null) FavouriteToggle(marked = marked, onToggle = onToggle)
        if (failed) InlineError(stringResource(Res.string.today_action_failed))
    }
}

// ---- Previews ----

private fun previewDay(tombstone: Boolean = false) = ArchiveDayState(
    load = ArchiveLoad.Ready,
    canReport = true,
    date = FakeCopy.DATE,
    partnerName = FakeCopy.PARTNER_NAME,
    day = ArchiveDayUi(
        date = FakeCopy.DATE,
        myEntry = FakeCopy.MY_ENTRY.takeUnless { tombstone },
        myTombstone = tombstone,
        myEntryId = "e1",
        partnerEntry = FakeCopy.PARTNER_ENTRY,
        canDelete = !tombstone,
        partnerEntryId = "t1",
        myMarkOnMine = false.takeUnless { tombstone },
        myMarkOnTheirs = true,
    ),
)

@Preview
@Composable
private fun ArchiveDayPreview() {
    MoyiTheme(darkTheme = true) { ArchiveDayScreen(previewDay(), onAction = {}) }
}

@Preview
@Composable
private fun ArchiveDayTombstonePreview() {
    MoyiTheme(darkTheme = false) { ArchiveDayScreen(previewDay(tombstone = true), onAction = {}) }
}
