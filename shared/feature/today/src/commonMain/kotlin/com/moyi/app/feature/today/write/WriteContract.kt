package com.moyi.app.feature.today.write

import androidx.compose.runtime.Immutable
import com.moyi.app.feature.today.common.FakeCopy

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
    /** The server's prompt; null once dismissed, or when editing. An offer, never a requirement. */
    val prompt: String? = FakeCopy.PROMPT,
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
     * TODO(logic): once drafts are saved locally (doc 13 §5), closing still asks: the
     *  owner's D10 makes discarding a choice, and Discard deletes the draft too.
     */
    val wouldLoseWords: Boolean
        get() = text.isNotBlank() && text != originalText && submit != WriteSubmit.Queued

    /** 1 to 500, with at least one non-space character (FR-041). */
    val canSubmit: Boolean get() = text.isNotBlank() && !overLimit

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
    data object OnAddPhotoClick : WriteAction
    data object OnRecordVoiceClick : WriteAction

    /** DEBUG ONLY (PR #8): step to the next drawn composer state. */
    data object OnDebugCycle : WriteAction
}

sealed interface WriteEvent {
    /** Close, back to Today: after a send or save, or a close that loses nothing or was confirmed. */
    data object Close : WriteEvent
}
