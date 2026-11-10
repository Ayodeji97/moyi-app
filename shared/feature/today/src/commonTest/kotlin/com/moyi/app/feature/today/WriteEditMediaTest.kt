package com.moyi.app.feature.today

import com.moyi.app.designsystem.component.AttachedVoiceState
import com.moyi.app.designsystem.component.EntryPhoto
import com.moyi.app.designsystem.component.EntryPhotoLook
import com.moyi.app.designsystem.component.VoiceNote
import com.moyi.app.designsystem.component.VoiceNoteLook
import com.moyi.app.designsystem.platform.PhotoPick
import com.moyi.app.designsystem.platform.PickedPhoto
import com.moyi.app.designsystem.platform.RecordedVoice
import com.moyi.app.designsystem.platform.RecorderEvent
import com.moyi.app.domain.media.MediaStatus
import com.moyi.app.domain.media.MediaUploader
import com.moyi.app.feature.today.common.TodayStatus
import com.moyi.app.feature.today.common.WriteResult
import com.moyi.app.feature.today.write.PhotoNotice
import com.moyi.app.feature.today.write.VoiceNotice
import com.moyi.app.feature.today.write.WriteAction
import com.moyi.app.feature.today.write.WriteEvent
import com.moyi.app.feature.today.write.WriteSubmit
import com.moyi.app.feature.today.write.WriteViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Editing before the reveal can put a new photo or voice note in place of the entry's, or add
 * one to an entry with none (docs/missing-components.md §60). The new one uploads at Save as
 * at Send and goes in the PATCH by name; nothing ever takes one off (backend-contract-gaps
 * row 41), so the entry's own stays unless a new one replaces it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WriteEditMediaTest : MainDispatcherTest() {
    private val sentPhoto = EntryPhoto(key = "photo-old", url = "https://local.test/p", width = 4, height = 3, look = EntryPhotoLook.Shown)
    private val sentVoice = VoiceNote(key = "voice-old", url = "https://local.test/v", durationMs = 9_000, look = VoiceNoteLook.Playable)
    private val loop = FakeDailyLoop().apply { show(dayView(TodayStatus.Waiting).copy(myPhoto = sentPhoto, myVoice = sentVoice)) }
    private val media = FakeMedia()
    private var keys = 0
    private val newPhoto = PickedPhoto(ByteArray(10) { it.toByte() }, 4, 3)
    private val said = RecordedVoice(ByteArray(16) { it.toByte() }, 4_200)

    private fun viewModel(withUploader: Boolean = true) = WriteViewModel(
        editing = true,
        loop = loop,
        drafts = FakeDraftStore(),
        uploader = if (withUploader) MediaUploader(media, newIdempotencyKey = { "complete-${++keys}" }, pollDelays = listOf(0, 0)) else null,
        newIdempotencyKey = { "key-${++keys}" },
        clock = FixedClock(TAP),
    )

    /** Answers the view model's recorder events as the platform recorders do (see WriteVoiceTest). */
    private fun TestScope.recorderFor(vm: WriteViewModel) = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
        vm.events.collect { event ->
            when (event) {
                WriteEvent.StartRecording -> vm.onAction(WriteAction.OnRecorderEvent(RecorderEvent.Started))
                WriteEvent.StopRecording -> vm.onAction(WriteAction.OnRecorderEvent(RecorderEvent.Finished(said)))
                else -> Unit
            }
        }
    }

    private fun WriteViewModel.recordAgain() = apply {
        onAction(WriteAction.OnRecordVoiceClick)
        onAction(WriteAction.OnStopRecordingClick)
    }

    @Test
    fun editShowsTheEntrysPhotoAndVoiceNoteAndOffersToReplaceThem() {
        val state = viewModel().state.value
        assertTrue(state.editing)
        assertEquals(sentPhoto, state.sentPhoto)
        assertEquals(sentVoice, state.sentVoice)
        assertTrue(state.photosOffered)
        assertTrue(state.voiceOffered)
        assertNull(state.photo, "nothing new is attached yet")
        assertNull(state.voice)
        assertFalse(state.wouldLoseWords, "the entry's own media are not something closing loses")
    }

    @Test
    fun withoutAnUploaderNothingIsOfferedButTheEntrysMediaAreStillShown() {
        val state = viewModel(withUploader = false).state.value
        assertFalse(state.photosOffered)
        assertFalse(state.voiceOffered)
        assertEquals(sentPhoto, state.sentPhoto)
    }

    @Test
    fun aNewEntryShowsNoSentMedia() {
        loop.show(dayView(TodayStatus.NotWritten, myEntry = null))
        val state = WriteViewModel(false, loop, FakeDraftStore()).state.value
        assertNull(state.sentPhoto)
        assertNull(state.sentVoice)
    }

    @Test
    fun savingTheWordsAloneSendsNoMediaSoTheEntryKeepsItsOwn() = runTest {
        val vm = viewModel()
        vm.onAction(WriteAction.OnTextChange("Changed words."))
        vm.onAction(WriteAction.OnSubmit)
        assertTrue(media.calls.isEmpty(), "nothing uploaded")
        assertEquals(FakeDailyLoop.Edit("Changed words.", "key-1"), loop.edits.single())
    }

    @Test
    fun aRecordingAgainUploadsAtSaveThenPatchesItsIdByName() = runTest {
        val gate = CompletableDeferred<Unit>()
        media.putGate = gate
        val vm = viewModel()
        recorderFor(vm)
        vm.recordAgain()
        assertEquals(AttachedVoiceState.Recorded(said), vm.state.value.voice)
        assertEquals(sentVoice, vm.state.value.sentVoice, "kept to come back to until Save")

        vm.onAction(WriteAction.OnSubmit)
        assertEquals(WriteSubmit.Sending, vm.state.value.submit)
        assertTrue(vm.state.value.sendingVoice, "the action says Sending your voice note")
        assertTrue(loop.edits.isEmpty(), "the words wait for their voice note")
        gate.complete(Unit)

        assertEquals(listOf("intent", "put", "complete:m1", "status:m1"), media.calls)
        val saved = loop.edits.single()
        assertEquals(MINE, saved.text)
        assertEquals("m1", saved.voiceMediaId)
        assertNull(saved.imageMediaId, "the photo is left as it is")
    }

    @Test
    fun aNewPhotoUploadsAtSaveThenPatchesItsIdByName() = runTest {
        val gate = CompletableDeferred<Unit>()
        media.putGate = gate
        val vm = viewModel()
        vm.onAction(WriteAction.OnPhotoPicked(PhotoPick.Picked(newPhoto)))
        vm.onAction(WriteAction.OnSubmit)
        assertTrue(vm.state.value.sendingPhoto, "the action says Sending your photo")
        gate.complete(Unit)

        val saved = loop.edits.single()
        assertEquals("m1", saved.imageMediaId)
        assertNull(saved.voiceMediaId)
    }

    @Test
    fun anEntryWithNoMediaCanAddThemInEdit() = runTest {
        loop.show(dayView(TodayStatus.Waiting))
        val vm = viewModel()
        recorderFor(vm)
        assertNull(vm.state.value.sentPhoto)
        assertNull(vm.state.value.sentVoice)
        vm.onAction(WriteAction.OnPhotoPicked(PhotoPick.Picked(newPhoto)))
        vm.recordAgain()
        vm.onAction(WriteAction.OnSubmit)

        val saved = loop.edits.single()
        assertEquals("m1", saved.imageMediaId)
        assertEquals("m2", saved.voiceMediaId)
    }

    @Test
    fun throwingTheNewRecordingAwayKeepsTheEntrysOwn() = runTest {
        val vm = viewModel()
        recorderFor(vm)
        vm.onAction(WriteAction.OnRecordVoiceClick)
        assertTrue(vm.state.value.recording)
        vm.onAction(WriteAction.OnDiscardRecordingClick)
        assertNull(vm.state.value.voice)
        assertEquals(sentVoice, vm.state.value.sentVoice)

        // A kept recording, removed before Save, puts the entry's own back the same way.
        vm.recordAgain()
        assertTrue(vm.state.value.wouldLoseWords, "a new recording is something closing would lose")
        vm.onAction(WriteAction.OnRemoveVoice)
        assertNull(vm.state.value.voice)
        assertEquals(sentVoice, vm.state.value.sentVoice)

        vm.onAction(WriteAction.OnSubmit)
        assertTrue(media.calls.isEmpty())
        assertNull(loop.edits.single().voiceMediaId, "absent: the server keeps the entry's voice note")
    }

    @Test
    fun theLibraryOrCameraQuestionIsTitledReplaceWhenAPhotoIsAlreadyThere() = runTest {
        // §61: Replace on the entry's own photo opens the question as "Replace the photo".
        val vm = viewModel()
        vm.onAction(WriteAction.OnAddPhotoClick(canTakePhoto = true))
        assertTrue(vm.state.value.choosingPhotoSource)
        assertTrue(vm.state.value.replacingPhoto)

        // An entry with none: the same question is "Add a photo" until one is picked.
        loop.show(dayView(TodayStatus.Waiting))
        val bare = viewModel()
        assertFalse(bare.state.value.replacingPhoto)
        bare.onAction(WriteAction.OnPhotoPicked(PhotoPick.Picked(newPhoto)))
        assertTrue(bare.state.value.replacingPhoto, "a picked photo is replaced by the next pick")
        bare.onAction(WriteAction.OnRemovePhoto)
        assertFalse(bare.state.value.replacingPhoto)
    }

    @Test
    fun removingTheNewPhotoKeepsTheEntrysOwn() = runTest {
        val vm = viewModel()
        vm.onAction(WriteAction.OnPhotoPicked(PhotoPick.Picked(newPhoto)))
        vm.onAction(WriteAction.OnRemovePhoto)
        assertNull(vm.state.value.photo)
        assertEquals(sentPhoto, vm.state.value.sentPhoto)
        vm.onAction(WriteAction.OnSubmit)
        assertNull(loop.edits.single().imageMediaId)
    }

    @Test
    fun aFailedUploadKeepsEverythingSendsNoWordsAndTryAgainResumes() = runTest {
        media.failAt = "complete"
        val vm = viewModel()
        recorderFor(vm)
        vm.recordAgain()
        vm.onAction(WriteAction.OnSubmit)

        assertEquals(WriteSubmit.Failed, vm.state.value.submit)
        assertEquals(AttachedVoiceState.Recorded(said), vm.state.value.voice)
        assertTrue(loop.edits.isEmpty(), "no words go without their voice note")

        media.failAt = null
        vm.onAction(WriteAction.OnSubmit)
        assertEquals(1, media.calls.count { it == "put" }, "the bytes went to storage once")
        assertEquals("m1", loop.edits.single().voiceMediaId)
    }

    @Test
    fun anEditThatCannotReachTheServerIsSaveFailedNeverQueuedAndKeepsTheNewMedia() = runTest {
        loop.editResult = WriteResult.Failed
        val vm = viewModel()
        vm.onAction(WriteAction.OnPhotoPicked(PhotoPick.Picked(newPhoto)))
        vm.onAction(WriteAction.OnSubmit)
        assertEquals(WriteSubmit.Failed, vm.state.value.submit)
        assertEquals(newPhoto, vm.state.value.photo)

        loop.editResult = WriteResult.Saved
        vm.onAction(WriteAction.OnSubmit)
        assertEquals(1, media.calls.count { it == "intent" }, "the ready photo is not uploaded again")
        assertEquals(loop.edits[0], loop.edits[1], "the identical request, the same key")
    }

    @Test
    fun aNewPhotoOrVoiceNoteTheServerRefusesIsSaidSo() = runTest {
        loop.editResult = WriteResult.PhotoRefused
        val vm = viewModel()
        vm.onAction(WriteAction.OnPhotoPicked(PhotoPick.Picked(newPhoto)))
        vm.onAction(WriteAction.OnSubmit)
        assertEquals(PhotoNotice.Refused, vm.state.value.photoNotice)
        assertFalse(vm.state.value.canSubmit, "remove it to save the words")

        vm.onAction(WriteAction.OnRemovePhoto)
        assertTrue(vm.state.value.canSubmit)
        assertEquals(sentPhoto, vm.state.value.sentPhoto, "the entry's own photo is back")
    }

    @Test
    fun aVoiceNoteRefusedByProcessingIsSaidSoAndTheWordsWait() = runTest {
        media.finalStatus = MediaStatus.Quarantined
        val vm = viewModel()
        recorderFor(vm)
        vm.recordAgain()
        vm.onAction(WriteAction.OnSubmit)
        assertEquals(VoiceNotice.Refused, vm.state.value.voiceNotice)
        assertTrue(loop.edits.isEmpty())
    }
}
