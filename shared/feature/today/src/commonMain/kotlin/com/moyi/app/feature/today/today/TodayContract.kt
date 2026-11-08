package com.moyi.app.feature.today.today

import androidx.compose.runtime.Immutable
import com.moyi.app.feature.today.common.TodayStatus
import kotlinx.datetime.LocalDate

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
    /** The Bond-day a queued entry will count for (states.md §4, BR-3a, `intendedAt`). */
    val queuedForDate: LocalDate? = null,
    /** Deleting my revealed entry is being confirmed, in the platform dialog over Today (D11). */
    val confirmingDelete: Boolean = false,
    /** The delete is in flight: its link is disabled so it cannot be sent twice. */
    val deleting: Boolean = false,
    /** A delete did not go through: the entry is still there, and the Generic error line says so (states.md §10). */
    val actionFailed: Boolean = false,
)

sealed interface TodayAction {
    /** Today came to the foreground (ON_START): refresh, never trusting a push to have arrived (states.md §10). */
    data object OnForeground : TodayAction

    /** The Generic error's Try again. */
    data object OnRetryClick : TodayAction
    data object OnWriteClick : TodayAction
    data object OnEditClick : TodayAction

    /** Before the reveal: immediate, no confirmation (states.md §4). */
    data object OnDeleteBeforeRevealClick : TodayAction

    /** After the reveal: confirmation first (states.md §5), in the platform dialog (D11). */
    data object OnDeleteAfterRevealClick : TodayAction

    /** The delete dialog's destructive answer. */
    data object OnDeleteConfirm : TodayAction

    /** The delete dialog's Cancel (or Android Back over it): nothing changes. */
    data object OnDeleteDismiss : TodayAction
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
