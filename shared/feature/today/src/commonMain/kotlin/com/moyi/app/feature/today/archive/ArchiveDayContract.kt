package com.moyi.app.feature.today.archive

import androidx.compose.runtime.Immutable
import com.moyi.app.domain.today.Reaction
import kotlinx.datetime.LocalDate

/**
 * The day view (Figma 438:1462, long entry 243:2): both entries in full, scrolling,
 * and "Delete what you wrote" on my live entry of a revealed day.
 */
@Immutable
data class ArchiveDayState(
    val load: ArchiveLoad = ArchiveLoad.Loading,
    val date: LocalDate? = null,
    val day: ArchiveDayUi? = null,
    val partnerName: String? = null,
    /** The platform's confirm is up (D11), over the day view. */
    val confirmingDelete: Boolean = false,
    /** The delete is in flight: its link is disabled so it cannot be sent twice. */
    val deleting: Boolean = false,
    /** A delete did not go through: the entry is still there, and the Generic error line says so. */
    val actionFailed: Boolean = false,
    /** A favourite toggle did not go through: it is back as it was, and the line under it says so. */
    val markFailed: FavouriteTarget? = null,
    /** The last reaction tap did not go through: it is back as it was, and the line under the set says so. */
    val reactionFailed: Boolean = false,
)

/** Which entry's FavouriteToggle: the one under my entry, or under the partner's. */
enum class FavouriteTarget { Mine, Theirs }

sealed interface ArchiveDayAction {
    data object OnBackClick : ArchiveDayAction
    data object OnRetryClick : ArchiveDayAction
    data object OnDeleteClick : ArchiveDayAction
    data object OnDeleteConfirm : ArchiveDayAction
    data object OnDeleteDismiss : ArchiveDayAction

    /** The FavouriteToggle under [target]: marks it if I have not, unmarks it if I have. */
    data class OnFavouriteClick(val target: FavouriteTarget) : ArchiveDayAction

    /** A chip of the reaction set under the partner's entry: leaves, replaces or takes back mine. */
    data class OnReactionClick(val reaction: Reaction) : ArchiveDayAction
}

sealed interface ArchiveDayEvent {
    data object NavigateBack : ArchiveDayEvent
}
