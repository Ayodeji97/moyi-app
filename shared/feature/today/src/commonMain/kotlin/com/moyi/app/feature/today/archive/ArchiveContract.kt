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

/**
 * The Archive's filters (states.md §6), in the order of the routes: All days, Favourites,
 * Search, the third, for word recall (FR-091), and Remember when, the fourth (FR-204): a
 * question answered with the closest days, the one place the archive is not newest first.
 */
enum class ArchiveFilter { AllDays, Favourites, Search, Remember }

/** Why the last question got no answer. Only these two are told apart (owner ruling 2026-11-18). */
enum class RememberFailure {
    /** Anything but a `429`: the generic failure line, with Try again. */
    Failed,

    /** `429`: "too many tries"; Ask is held until the server's `Retry-After` has passed. */
    RateLimited,
}

/**
 * One day of a "Remember when" answer, in the order the server ranked it. [withYear]: the
 * row names the year, because the day is not in the current Bond-day's year (the list has no
 * month headings to say so).
 */
@Immutable
data class RememberResult(val day: ArchiveDayUi, val withYear: Boolean)

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
    /** The empty state offers Write: not while the bond is paused (ADR-0066), when it would only be refused. */
    val canWrite: Boolean = true,
    val filter: ArchiveFilter = ArchiveFilter.AllDays,
    /**
     * The All days / Favourites chips are drawn. Hidden over an archive with nothing in
     * it (states.md §6: an empty filter over an empty list is two emptinesses); always
     * shown once a chip has been chosen, since they are the way back.
     */
    val showFilters: Boolean = false,
    /**
     * The Search chip is drawn: the archive is known to hold a day. **Hidden when the
     * archive is empty** (states.md §6): there is nothing to search.
     */
    val showSearch: Boolean = false,
    /** What is in the search field, as typed. Kept through the day view and back. */
    val query: String = "",
    /** The field should take focus and raise the keyboard (once, on choosing Search). */
    val focusSearch: Boolean = false,
    /**
     * The month row (states.md §6, route 1) is drawn: on All days and Favourites over an
     * archive that holds a day. **Absent in Search**: the field takes its slot (they swap).
     */
    val showMonthRow: Boolean = false,
    /** The month the row names: the topmost visible day group's, as the first of that month. */
    val visibleMonth: LocalDate? = null,
    /** The picker is open. */
    val showMonthPicker: Boolean = false,
    /**
     * The months the picker offers, newest first, as the first of each: from the current
     * Bond-day's month (never a later one) back to the month of the oldest day loaded so far.
     */
    val pickerMonths: List<LocalDate> = emptyList(),
    /** The list was jumped to a month: "Back to the newest days" is offered. */
    val jumped: Boolean = false,
    /** The list should scroll to its top (once, after a jump or the way back from one). */
    val scrollToTop: Boolean = false,
    /**
     * The Remember when chip is drawn: the archive is known to hold a day, and this build
     * offers the feature (hidden in release builds for now, owner ruling 2026-11-18).
     */
    val showRemember: Boolean = false,
    /** What is in the question field, as typed. Memory only: kept through the day view, never saved. */
    val question: String = "",
    /** The question field should take focus and raise the keyboard (once, on choosing the chip). */
    val focusQuestion: Boolean = false,
    /** A question is on its way: Ask reads Asking, and nothing else is sent until it answers. */
    val asking: Boolean = false,
    /** Ask can be tapped: a question that is not only spaces, none on its way, and no `429` wait. */
    val canAsk: Boolean = false,
    /** The last question failed, and how. Null when it did not, or a new one is on its way. */
    val rememberFailure: RememberFailure? = null,
    /**
     * The answer, in relevance order: no month groups. Null before the first answer and while a
     * question is on its way; empty when nothing came back.
     */
    val results: List<RememberResult>? = null,
    /** Focus should move to the answer, once, when it arrives (states the count to a screen reader). */
    val focusResults: Boolean = false,
) {
    val isEmpty: Boolean get() = load == ArchiveLoad.Ready && months.isEmpty()

    /** A query was searched and nothing matched: the message block replaces the list, the query stays. */
    val noResults: Boolean get() = isEmpty && filter == ArchiveFilter.Search && query.isNotBlank()

    // The query is the person's own words: never in a log (CLAUDE.md, Code).
    override fun toString(): String =
        "ArchiveState(load=$load, months=$months, partnerName=$partnerName, canLoadMore=$canLoadMore, pageFailed=$pageFailed, pages=$pages, " +
            "filter=$filter, showFilters=$showFilters, showSearch=$showSearch, " +
            "query=${if (query.isEmpty()) "" else "(redacted)"}, focusSearch=$focusSearch, " +
            "showMonthRow=$showMonthRow, visibleMonth=$visibleMonth, showMonthPicker=$showMonthPicker, " +
            "pickerMonths=$pickerMonths, jumped=$jumped, scrollToTop=$scrollToTop, " +
            "showRemember=$showRemember, question=${if (question.isEmpty()) "" else "(redacted)"}, focusQuestion=$focusQuestion, " +
            "asking=$asking, canAsk=$canAsk, rememberFailure=$rememberFailure, results=$results, focusResults=$focusResults)"
}

sealed interface ArchiveAction {
    data object OnBackClick : ArchiveAction
    data object OnRetryClick : ArchiveAction

    /** The list came near its end. */
    data object OnLoadMore : ArchiveAction

    /** The Try again under a page that failed. */
    data object OnRetryPageClick : ArchiveAction
    data class OnDayClick(val date: LocalDate) : ArchiveAction

    /**
     * A chip: reloads the list, with `favourites=true` for [ArchiveFilter.Favourites].
     * [ArchiveFilter.Search] shows the field, focused, over the unfiltered list.
     */
    data class OnFilterClick(val filter: ArchiveFilter) : ArchiveAction

    /** The search field changed. Searched once it has rested for [SEARCH_DEBOUNCE]. */
    data class OnQueryChange(val query: String) : ArchiveAction {
        override fun toString(): String = "OnQueryChange(query=(redacted))"
    }

    /** The screen has focused the field and raised the keyboard. */
    data object OnSearchFocused : ArchiveAction

    /** The Remember when question changed. Nothing is sent until Ask: each question may cost a provider call. */
    data class OnQuestionChange(val question: String) : ArchiveAction {
        override fun toString(): String = "OnQuestionChange(question=(redacted))"
    }

    /** Ask, the keyboard's Search key, or the Try again under a failed question: sends the question in the field. */
    data object OnAskClick : ArchiveAction

    /** The screen has focused the question field and raised the keyboard. */
    data object OnQuestionFocused : ArchiveAction

    /** The screen has moved focus to the answer. */
    data object OnResultsFocused : ArchiveAction

    /** The month row's "Jump to": opens the month picker. */
    data object OnJumpClick : ArchiveAction

    /** The picker was closed without a choice. */
    data object OnMonthPickerDismiss : ArchiveAction

    /**
     * A month chosen in the picker ([month] is any day in it): the list reloads with `until`
     * = its last day, in the current filter, and scrolls to the top.
     */
    data class OnMonthChosen(val month: LocalDate) : ArchiveAction

    /** "Back to the newest days": the list as it was before any jump. */
    data object OnNewestClick : ArchiveAction

    /** The topmost visible day group is now in [month] (any day in it). */
    data class OnVisibleMonthChange(val month: LocalDate) : ArchiveAction

    /** The screen has scrolled the list to its top. */
    data object OnScrolledToTop : ArchiveAction

    /** The empty state's Write (Figma 223:25's PrimaryAction). */
    data object OnWriteClick : ArchiveAction

    /** "Your streak" in the title row (Figma 430:1435). */
    data object OnStreakClick : ArchiveAction

    /** DEBUG ONLY (`-PmoyiUiPreview=today`): list, then empty, then the error, then the list. */
    data object OnDebugCycle : ArchiveAction
}

sealed interface ArchiveEvent {
    data object NavigateBack : ArchiveEvent
    data class NavigateToDay(val date: LocalDate) : ArchiveEvent
    data object NavigateToWrite : ArchiveEvent
    data object NavigateToStreak : ArchiveEvent
}
