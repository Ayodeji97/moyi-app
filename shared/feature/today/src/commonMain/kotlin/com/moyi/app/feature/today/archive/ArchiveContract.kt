package com.moyi.app.feature.today.archive

import androidx.compose.runtime.Immutable
import kotlinx.datetime.LocalDate

/** Whether there is a list to draw. The same three as Today's (states.md §0, §10). */
enum class ArchiveLoad {
    /** The bare `surface`, no spinner (CLAUDE.md). */
    Loading,

    /** The first page failed: the Generic error StateBlock, with Try again. */
    Failed,
    Ready,
}

/** The Archive's filters (states.md §6). Search, the third chip, is the next slice's. */
enum class ArchiveFilter { AllDays, Favourites }

/** One month of days, newest first (states.md §6: month-grouped). */
@Immutable
data class ArchiveMonth(
    /** Any day in the month; the screen names the month from it. */
    val month: LocalDate,
    val days: List<ArchiveDayUi>,
)

/**
 * The Archive list (Figma 223:3, empty 223:25). Days with nothing to draw are not
 * in it (see [toArchiveDayUi]); [isEmpty] is true only at the real end, so a short
 * page with more to come never shows the empty state.
 */
@Immutable
data class ArchiveState(
    val load: ArchiveLoad = ArchiveLoad.Loading,
    val months: List<ArchiveMonth> = emptyList(),
    val partnerName: String? = null,
    /** A next page is there to ask for, and none is on its way or has just failed. */
    val canLoadMore: Boolean = false,
    /** The next page failed: the list stays, with a Try again at its end. */
    val pageFailed: Boolean = false,
    /** Moves on with every page, so the list asks again near its end (ArchiveScreen). */
    val pages: Int = 0,
    val filter: ArchiveFilter = ArchiveFilter.AllDays,
    /**
     * The All days / Favourites chips are drawn. Hidden over an archive with nothing in
     * it (states.md §6: an empty filter over an empty list is two emptinesses); always
     * shown once a chip has been chosen, since they are the way back.
     */
    val showFilters: Boolean = false,
) {
    val isEmpty: Boolean get() = load == ArchiveLoad.Ready && months.isEmpty()
}

sealed interface ArchiveAction {
    data object OnBackClick : ArchiveAction
    data object OnRetryClick : ArchiveAction

    /** The list came near its end. */
    data object OnLoadMore : ArchiveAction

    /** The Try again under a page that failed. */
    data object OnRetryPageClick : ArchiveAction
    data class OnDayClick(val date: LocalDate) : ArchiveAction

    /** A chip: reloads the list, with `favourites=true` for [ArchiveFilter.Favourites]. */
    data class OnFilterClick(val filter: ArchiveFilter) : ArchiveAction

    /** The empty state's Write (Figma 223:25's PrimaryAction). */
    data object OnWriteClick : ArchiveAction

    /** DEBUG ONLY (`-PmoyiUiPreview=today`): list, then empty, then the error, then the list. */
    data object OnDebugCycle : ArchiveAction
}

sealed interface ArchiveEvent {
    data object NavigateBack : ArchiveEvent
    data class NavigateToDay(val date: LocalDate) : ArchiveEvent
    data object NavigateToWrite : ArchiveEvent
}
