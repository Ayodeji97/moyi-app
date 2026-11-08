package com.moyi.app.feature.today.write

import androidx.compose.runtime.Immutable
import com.moyi.app.designsystem.platform.PhotoPick
import com.moyi.app.designsystem.platform.PickedPhoto
/** Where a send or save stands (states.md §4; Figma 419:193, 384:229). */
enum class WriteSubmit {
    Idle,

    /** In flight: PrimaryAction State=Sending, no spinner (419:193). */
    Sending,

    /** Save failed: the text stays, the error sits above the action, the action says Try again (384:229). */
    Failed,

    /** Offline: accepted on the device, waiting for a connection (PrimaryAction State=QueuedOffline). */
    Queued,
}

/**
 * Write, the composer (Figma section 425:315: 181:3 empty, 181:43 past 400,
 * 181:83 over limit, 419:193 sending, 419:291 editing, 384:229 save failed).
 */
@Immutable
data class WriteState(
    val text: String = "",
    /** The server's prompt; null once dismissed, when editing, or when the day has none. An offer, never a requirement. */
    val prompt: String? = null,
    /** Editing before the reveal: the same composer, the action reads Save (states.md §4). */
    val editing: Boolean = false,
    val submit: WriteSubmit = WriteSubmit.Idle,
    /**
     * The words the composer opened with: empty for a new entry, what I wrote when
     * editing. Closing loses nothing while the text is still this.
     */
    val originalText: String = "",
    /** The discard question is showing (D10): closing would lose typed words. */
    val confirmingDiscard: Boolean = false,
    /**
     * The saved draft is being read (a few milliseconds at open). The composer is not shown
     * until it is, so the words arrive with the field and the cursor sits after them.
     */
    val restoringDraft: Boolean = false,
    /**
     * The photo being added, already prepared on the phone (downscaled, upright, JPEG). Held
     * in memory only: it is not part of the draft (docs/missing-components.md §40).
     */
    val photo: PickedPhoto? = null,
    /** Whether "Add a photo" is offered: not while editing, and not without an uploader. */
    val photosOffered: Boolean = false,
    /** While [submit] is Sending: true while the photo uploads, false once the words are on their way. */
    val sendingPhoto: Boolean = false,
    /** What went wrong with the photo itself, said above the action. */
    val photoNotice: PhotoNotice? = null,
    /** The platform dialog asking library or camera is showing. */
    val choosingPhotoSource: Boolean = false,
) {
    /**
     * FR-041 counts user-perceived characters (grapheme clusters): a family emoji is one.
     *
     * TODO(logic): count grapheme clusters (ICU BreakIterator on Android, NSString
     *  enumerateSubstrings byComposedCharacterSequences on iOS, behind expect/actual).
     *  `length` counts UTF-16 units, so an emoji counts as two or more here.
     */
    val characterCount: Int get() = text.length

    /** The counter appears only past 400 (states.md §4). */
    val showCounter: Boolean get() = characterCount > COUNTER_FROM

    /** Over 500: the entry is invalid; the counter turns `danger` and the action is disabled. */
    val overLimit: Boolean get() = characterCount > MAX_CHARACTERS

    /**
     * Whether closing now would lose words (D10), so it asks first. Not when nothing
     * was typed, when only spaces were, when an edit changed nothing, or once the
     * device has accepted the entry (Queued).
     *
     * The words are also kept as a draft (doc 13 §5), but closing still asks: D10 makes
     * discarding a choice, and Discard deletes the draft too. The question's third answer,
     * Keep as a draft, closes and keeps it.
     */
    val wouldLoseWords: Boolean
        get() = ((text.isNotBlank() && text != originalText) || photo != null) && submit != WriteSubmit.Queued

    /**
     * 1 to 500, with at least one non-space character (FR-041). A photo never stands in for
     * words (text is always sufficient, and always required). A photo the server refused
     * must be removed first: the line above the action says so.
     */
    val canSubmit: Boolean get() = text.isNotBlank() && !overLimit && photoNotice != PhotoNotice.Refused

    companion object {
        /** states.md §4 "character counter appears only now", past 400. */
        const val COUNTER_FROM = 400

        /** FR-041 / contract `SubmitEntryRequest.text`: at most 500 graphemes. */
        const val MAX_CHARACTERS = 500
    }
}

sealed interface WriteAction {
    data class OnTextChange(val text: String) : WriteAction
    data object OnDismissPrompt : WriteAction

    /** Send, Save, or Try again: whichever the action reads. */
    data object OnSubmit : WriteAction
    /**
     * Close the task (D10): the X on Android, Cancel on iOS, Android Back, or a
     * swipe down on the iOS sheet. Asks first when [WriteState.wouldLoseWords].
     */
    data object OnCloseClick : WriteAction

    /** The discard question's destructive answer: close and lose the words. */
    data object OnDiscardConfirm : WriteAction

    /** The discard question's other answer (or Android Back over it): stay, words intact. */
    data object OnDiscardDismiss : WriteAction

    /**
     * The discard question's third answer (owner ruling 2026-11-08): close and keep the
     * words as the draft, so they come back the next time Write opens.
     */
    data object OnKeepDraft : WriteAction
    /**
     * "Add a photo". [canTakePhoto]: the phone has a camera, so it asks library or camera;
     * without one it opens the library at once.
     */
    data class OnAddPhotoClick(val canTakePhoto: Boolean) : WriteAction

    /** The source dialog's answers. */
    data object OnChooseFromLibrary : WriteAction
    data object OnTakePhoto : WriteAction
    data object OnPhotoSourceDismiss : WriteAction

    /** The picker or camera returned (a cancelled pick sends nothing). Replaces any photo attached. */
    data class OnPhotoPicked(val pick: PhotoPick) : WriteAction

    /** The remove control on the attached photo. */
    data object OnRemovePhoto : WriteAction
    data object OnRecordVoiceClick : WriteAction

    /** DEBUG ONLY (`-PmoyiUiPreview=today`): step to the next drawn composer state. */
    data object OnDebugCycle : WriteAction
}

sealed interface WriteEvent {
    /** Close, back to Today: after a send or save, or a close that loses nothing or was confirmed. */
    data object Close : WriteEvent

    /** Open the system photo picker or the camera (the screen holds the platform launchers). */
    data class PickPhoto(val source: PhotoSource) : WriteEvent
}

enum class PhotoSource { Library, Camera }

/** What went wrong with the photo itself (not with sending). */
enum class PhotoNotice {
    /** The phone could not open or prepare the picked photo. Nothing is attached. */
    Unreadable,

    /** The server will not take it (processing failed, or moderation held it): remove it to send the words. */
    Refused,
}
