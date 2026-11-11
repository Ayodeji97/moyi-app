package com.moyi.app.feature.today.write

import androidx.compose.runtime.Immutable
import com.moyi.app.designsystem.component.AttachedVoiceState
import com.moyi.app.designsystem.component.EntryPhoto
import com.moyi.app.designsystem.component.VoiceNote
import com.moyi.app.designsystem.platform.PhotoPick
import com.moyi.app.designsystem.platform.PickedPhoto
import com.moyi.app.designsystem.platform.RecorderEvent

/** Where a send or save stands (states.md §4; Figma 419:193, 384:229). */
enum class WriteSubmit {
    Idle,

    /** In flight: PrimaryAction State=Sending, no spinner (419:193). */
    Sending,

    /** Save failed: the text stays, the error sits above the action, the action says Try again (384:229). */
    Failed,

    /** Offline: accepted on the device, waiting for a connection (PrimaryAction State=QueuedOffline). */
    Queued,

    /**
     * Edit only: the partner wrote while the composer was open, so the day has opened and the
     * entry stays as it was (`409 ENTRY_IMMUTABLE`, BR-7). No Save, no Try again: the action
     * is "See today". The typed words stay on screen, to copy, until the person leaves.
     */
    Revealed,
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
    /**
     * Whether a photo can be added: not without an uploader. In Edit it is offered too
     * (§60): a photo picked there replaces the entry's, or adds one to an entry with none.
     */
    val photosOffered: Boolean = false,
    /**
     * Edit only: the photo already on the entry, as the server shows it to me. Shown while no
     * new [photo] is picked; a new one goes in its place at Save. Taking it off is [removePhoto]:
     * this field keeps the entry's photo, so Cancel (which saves nothing) leaves it where it was.
     */
    val sentPhoto: EntryPhoto? = null,
    /**
     * Edit only: Remove was tapped on the entry's own photo ([sentPhoto]). Nothing leaves the
     * server until Save, which sends `removeImage`; closing without saving keeps the photo.
     */
    val removePhoto: Boolean = false,
    /** While [submit] is Sending: true while the photo uploads, false once the words are on their way. */
    val sendingPhoto: Boolean = false,
    /** What went wrong with the photo itself, said above the action. */
    val photoNotice: PhotoNotice? = null,
    /** The platform dialog asking library or camera is showing. */
    val choosingPhotoSource: Boolean = false,
    /**
     * The voice note being recorded, or recorded and not sent (docs/missing-components.md §44).
     * Held in memory only: like the photo, it is not part of the draft.
     */
    val voice: AttachedVoiceState? = null,
    /** Whether a voice note can be recorded: not without an uploader. In Edit too, as [photosOffered]. */
    val voiceOffered: Boolean = false,
    /** Edit only: the voice note already on the entry, shown while no new [voice] is recording or recorded. As [sentPhoto]. */
    val sentVoice: VoiceNote? = null,
    /** Edit only: Remove was tapped on the entry's own voice note. As [removePhoto]; Save sends `removeVoice`. */
    val removeVoice: Boolean = false,
    /** While [submit] is Sending: true while the voice note uploads (after the photo, before the words). */
    val sendingVoice: Boolean = false,
    /** What went wrong with the voice note itself, said above the action. */
    val voiceNotice: VoiceNotice? = null,
    /** The other member's name, for the line shown in [WriteSubmit.Revealed]; null when the day did not carry one. */
    val partnerName: String? = null,
) {
    /**
     * Whether a photo is already in the slot, the entry's own (Edit) or one picked here, so
     * the library or camera question is titled "Replace the photo", not "Add a photo"
     * (docs/missing-components.md §61): whatever is picked next goes in its place.
     */
    val replacingPhoto: Boolean get() = photo != null || shownSentPhoto != null

    /** The entry's own photo as the composer shows it: not once Remove was tapped on it. */
    val shownSentPhoto: EntryPhoto? get() = sentPhoto.takeUnless { removePhoto }

    /** The entry's own voice note as the composer shows it: not once Remove was tapped on it. */
    val shownSentVoice: VoiceNote? get() = sentVoice.takeUnless { removeVoice }

    /** A finished recording, ready to send; null while recording or with none. */
    val recordedVoice: AttachedVoiceState.Recorded? get() = voice as? AttachedVoiceState.Recorded

    val recording: Boolean get() = voice is AttachedVoiceState.Recording

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
        get() = ((text.isNotBlank() && text != originalText) || photo != null || voice != null || removePhoto || removeVoice) &&
            submit != WriteSubmit.Queued && submit != WriteSubmit.Revealed

    /**
     * 1 to 500, with at least one non-space character (FR-041). A photo or a voice note never
     * stands in for words (text is always sufficient, and always required, NFR-103). Media the
     * server refused must be removed first: the line above the action says so. While
     * recording, the recording is stopped first: Send waits for it.
     */
    val canSubmit: Boolean
        get() = submit != WriteSubmit.Revealed && text.isNotBlank() && !overLimit && photoNotice != PhotoNotice.Refused &&
            voiceNotice != VoiceNotice.Refused && !recording

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

    /** "See today", once an edit found the day revealed: close, to Today, which shows the revealed day. */
    data object OnSeeTodayClick : WriteAction

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
     * "Add a photo", or in Edit "Replace the photo" beside the entry's own. [canTakePhoto]: the phone has a camera, so it asks library or camera;
     * without one it opens the library at once.
     */
    data class OnAddPhotoClick(val canTakePhoto: Boolean) : WriteAction

    /** The source dialog's answers. */
    data object OnChooseFromLibrary : WriteAction
    data object OnTakePhoto : WriteAction
    data object OnPhotoSourceDismiss : WriteAction

    /** The picker or camera returned (a cancelled pick sends nothing). Replaces any photo attached. */
    data class OnPhotoPicked(val pick: PhotoPick) : WriteAction

    /** The remove control on the attached photo. In Edit, removing a new photo puts the entry's own back. */
    data object OnRemovePhoto : WriteAction

    /** Edit: the remove control on the entry's own photo. Marks it for removal; Save takes it off, Cancel keeps it. */
    data object OnRemoveSentPhoto : WriteAction

    /** Edit: the remove control on the entry's own voice note. As [OnRemoveSentPhoto]. */
    data object OnRemoveSentVoice : WriteAction

    /** "Record a voice note", or in Edit Record again beside the entry's own: asks for the microphone if it must, then records. */
    data object OnRecordVoiceClick : WriteAction

    /** The tick while recording: stop, and keep what was said to listen back to. */
    data object OnStopRecordingClick : WriteAction

    /** The trash while recording: stop and throw the unsent recording away, no question asked (states.md §4). */
    data object OnDiscardRecordingClick : WriteAction

    /** Throws the recorded voice note away and records a new one. */
    data object OnRecordAgainClick : WriteAction

    /** The remove control on the recorded voice note. In Edit, removing a new one puts the entry's own back. */
    data object OnRemoveVoice : WriteAction

    /** What the platform recorder reported. */
    data class OnRecorderEvent(val event: RecorderEvent) : WriteAction

    /** DEBUG ONLY (`-PmoyiUiPreview=today`): step to the next drawn composer state. */
    data object OnDebugCycle : WriteAction
}

sealed interface WriteEvent {
    /** Close, back to Today: after a send or save, or a close that loses nothing or was confirmed. */
    data object Close : WriteEvent

    /** Open the system photo picker or the camera (the screen holds the platform launchers). */
    data class PickPhoto(val source: PhotoSource) : WriteEvent

    /** Start the platform recorder (the screen holds it, as it holds the photo launchers). */
    data object StartRecording : WriteEvent

    /** Stop it, keeping what was recorded: the person stopped, or the limit was reached. */
    data object StopRecording : WriteEvent

    /** Stop it and throw the recording away: the recorder reports nothing back. */
    data object CancelRecording : WriteEvent
}

enum class PhotoSource { Library, Camera }

/** What went wrong with the photo itself (not with sending). */
enum class PhotoNotice {
    /** The phone could not open or prepare the picked photo. Nothing is attached. */
    Unreadable,

    /** The server will not take it (processing failed, or moderation held it): remove it to send the words. */
    Refused,
}

/** What went wrong with the voice note itself (not with sending). */
enum class VoiceNotice {
    /** The person said no to the microphone, now or before: Settings is where to allow it. */
    MicrophoneDenied,

    /** The microphone could not be opened, or nothing usable was recorded. Nothing is attached. */
    CouldNotRecord,

    /** The server will not take it (its processing failed, or moderation held it): remove it to send the words. */
    Refused,
}
