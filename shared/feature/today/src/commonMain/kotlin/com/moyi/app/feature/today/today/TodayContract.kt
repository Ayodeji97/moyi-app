package com.moyi.app.feature.today.today

import androidx.compose.runtime.Immutable
import com.moyi.app.designsystem.component.EntryPhoto
import com.moyi.app.designsystem.component.VoiceNote
import com.moyi.app.domain.today.OutboxRefusal
import com.moyi.app.domain.today.Reaction
import com.moyi.app.feature.today.common.QueuedDay
import com.moyi.app.feature.today.common.TodayStatus
import com.moyi.app.feature.today.resources.Res
import com.moyi.app.feature.today.resources.today_both_wrote_title
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.StringResource

/** Whether there is a day to draw (states.md §3, §10). */
enum class TodayLoad {
    /** Nothing yet: the bare `surface`, as at launch (states.md §0). No spinner. */
    Loading,

    /** The first load failed: the Generic error StateBlock, with Try again (Figma 384:341). */
    Failed,
    Ready,
}

/**
 * Today (Figma section 425:314). One status, one complete layout each
 * (SKILL.md §4: "a distinct, complete layout, not a variation with hidden elements").
 *
 * There is deliberately **no field for the partner's progress**. Today shows no
 * partner status of any kind (states.md §3), so the state cannot carry one: a
 * screen cannot leak what its state does not hold.
 */
@Immutable
data class TodayState(
    val load: TodayLoad = TodayLoad.Loading,
    val status: TodayStatus = TodayStatus.NotWritten,
    /** The Bond-day; the screen formats it, locale names, day first. */
    val date: LocalDate? = null,
    /** The other member. Null only while nobody has joined (partner not joined names no one). */
    val partnerName: String? = null,
    /** The server's prompt for today, personalised (ADR-0038). Not a client string. */
    val prompt: String? = null,
    val myEntry: String = "",
    /**
     * The partner's words: set ONLY for Revealed, Tombstone (the day is open) and
     * SoloTheyWrote (unlocked at rollover, FR-063); null in every other layout, so a
     * layout that must not show them has nothing it could show. Null on a revealed
     * day too, when the partner deleted theirs (no frame draws that yet).
     */
    val partnerEntry: String? = null,
    /** The photo on my entry, drawn in its card. */
    val myPhoto: EntryPhoto? = null,
    /** The partner's photo: set exactly where [partnerEntry] is. */
    val partnerPhoto: EntryPhoto? = null,
    /** The voice note on my entry, played in its card. */
    val myVoice: VoiceNote? = null,
    /** The partner's voice note: set exactly where [partnerEntry] is. */
    val partnerVoice: VoiceNote? = null,
    /** Which Bond-day a queued entry will count for (states.md §4, BR-3a, `intendedAt`); set in Queued. */
    val queuedDay: QueuedDay? = null,
    /** A queued entry the server refused: its words are back in Write, and Today says so. */
    val refusal: OutboxRefusal? = null,
    /** Deleting my revealed entry is being confirmed, in the platform dialog over Today (D11). */
    val confirmingDelete: Boolean = false,
    /**
     * Deleting my entry **before** the reveal is being confirmed, in the same platform dialog
     * (owner ruling 2026-11-09, which amends states.md §4's "no confirmation"). Its line says
     * what deleting means here, which is not what it means after the reveal.
     */
    val confirmingDeleteBeforeReveal: Boolean = false,
    /** The delete is in flight: its link is disabled so it cannot be sent twice. */
    val deleting: Boolean = false,
    /** A delete did not go through: the entry is still there, and the Generic error line says so (states.md §10). */
    val actionFailed: Boolean = false,
    /** The reaction set is drawn under the partner's entry: it is revealed and shown (ADR-0037). */
    val canReact: Boolean = false,
    /** The reaction I left on the partner's entry, as shown (optimistically, while it is on its way). */
    val myReaction: Reaction? = null,
    /** The reaction the partner left on my entry. */
    val partnerReaction: Reaction? = null,
    /** The last reaction tap did not go through: it is back as it was, and the Generic error line says so. */
    val reactionFailed: Boolean = false,
)

/** What the "More" menu on your own entry card can hold (owner ruling 2026-11-11). */
enum class OwnEntryMenuItem { Edit, Delete }

/**
 * The menu's items in this state, in order, and only what is allowed: Edit before the reveal
 * (states.md §4), never after (§5: entries are immutable once the day is open); Delete in both,
 * each behind its own confirmation. Everywhere else (not written, queued, solo, deleted)
 * there is no own entry to change here, so no menu.
 */
fun TodayState.ownEntryMenu(): List<OwnEntryMenuItem> = when (status) {
    TodayStatus.Waiting -> listOf(OwnEntryMenuItem.Edit, OwnEntryMenuItem.Delete)
    TodayStatus.Revealed -> listOf(OwnEntryMenuItem.Delete)
    else -> emptyList()
}

/** The action a menu item dispatches: the same actions the buttons used, so nothing is bypassed. */
fun TodayState.actionFor(item: OwnEntryMenuItem): TodayAction = when (item) {
    OwnEntryMenuItem.Edit -> TodayAction.OnEditClick
    OwnEntryMenuItem.Delete ->
        if (status == TodayStatus.Revealed) TodayAction.OnDeleteAfterRevealClick else TodayAction.OnDeleteBeforeRevealClick
}

/** The title string of the settled Today, by status; null where Today draws no title. */
fun TodayStatus.titleResource(): StringResource? = when (this) {
    TodayStatus.Revealed, TodayStatus.Tombstone -> Res.string.today_both_wrote_title
    else -> null
}

sealed interface TodayAction {
    /** Today came to the foreground (ON_START): refresh, never trusting a push to have arrived (states.md §10). */
    data object OnForeground : TodayAction

    /**
     * Today stopped showing (ON_STOP): the app went to the background, or a screen opened over
     * it. The re-reading of a photo or voice note on its way stops until the next [OnForeground].
     */
    data object OnBackground : TodayAction

    /** The Generic error's Try again. */
    data object OnRetryClick : TodayAction
    data object OnWriteClick : TodayAction
    data object OnEditClick : TodayAction

    /**
     * Before the reveal: confirmation first, in the platform dialog (owner ruling 2026-11-09;
     * states.md §4 said "no confirmation"). Nothing is deleted until [OnDeleteConfirm].
     */
    data object OnDeleteBeforeRevealClick : TodayAction

    /** After the reveal: confirmation first (states.md §5), in the platform dialog (D11). */
    data object OnDeleteAfterRevealClick : TodayAction

    /** The delete dialog's destructive answer: deletes whichever entry the open dialog asked about. */
    data object OnDeleteConfirm : TodayAction

    /** The delete dialog's Cancel (or Android Back over it): nothing changes. */
    data object OnDeleteDismiss : TodayAction
    /** A chip of the reaction set: leaves it, replaces mine with it, or (mine already) takes it back. */
    data class OnReactionClick(val reaction: Reaction) : TodayAction
    data object OnArchiveClick : TodayAction
    data object OnSettingsClick : TodayAction
    data object OnShareInviteClick : TodayAction

    /** DEBUG ONLY (`-PmoyiUiPreview=today`): step to the next drawn layout. */
    data object OnDebugCycle : TodayAction
}

sealed interface TodayEvent {
    data class NavigateToWrite(val editing: Boolean) : TodayEvent
    data object NavigateToReveal : TodayEvent
    /** Bond settings, for the bond this day belongs to. */
    data class NavigateToSettings(val bondId: String) : TodayEvent
    data object NavigateToArchive : TodayEvent

    /** The OS share sheet with the invite; fire and forget, it proves nothing was sent. */
    data class ShareInvite(val link: String, val code: String) : TodayEvent

    /** No open bond any more: the shell asks the signed-in start where this person belongs. */
    data object NavigateToStart : TodayEvent
}
