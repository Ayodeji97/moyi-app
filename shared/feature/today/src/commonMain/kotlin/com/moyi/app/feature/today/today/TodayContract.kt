package com.moyi.app.feature.today.today

import androidx.compose.runtime.Immutable
import com.moyi.app.feature.today.common.FakeCopy
import com.moyi.app.feature.today.common.TodayStatus

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
    val status: TodayStatus = TodayStatus.NotWritten,
    /** The Bond-day, locale-formatted, day-first. */
    val date: String = FakeCopy.DATE,
    val partnerName: String = FakeCopy.PARTNER_NAME,
    /** The server's prompt for today, personalised (ADR-0038). Not a client string. */
    val prompt: String = FakeCopy.PROMPT,
    val myEntry: String = FakeCopy.MY_ENTRY,
    /** Only ever shown once the day is open (Revealed, Tombstone) or SOLO (FR-063). */
    val partnerEntry: String = FakeCopy.PARTNER_ENTRY,
    /** The Bond-day a queued entry will count for (states.md §4, BR-3a, `intendedAt`). */
    val queuedForDate: String = FakeCopy.DATE_IN_SENTENCE,
)

sealed interface TodayAction {
    data object OnWriteClick : TodayAction
    data object OnEditClick : TodayAction

    /** Before the reveal: immediate, no confirmation (states.md §4). */
    data object OnDeleteBeforeRevealClick : TodayAction

    /** After the reveal: confirmation first (states.md §5). */
    data object OnDeleteAfterRevealClick : TodayAction
    data object OnArchiveClick : TodayAction
    data object OnSettingsClick : TodayAction
    data object OnShareInviteClick : TodayAction

    /** DEBUG ONLY (PR #8): step to the next fake layout. */
    data object OnDebugCycle : TodayAction
}

sealed interface TodayEvent {
    data class NavigateToWrite(val editing: Boolean) : TodayEvent
    data object NavigateToReveal : TodayEvent
    data object NavigateToDeleteConfirm : TodayEvent
    data object NavigateToSettings : TodayEvent
}
