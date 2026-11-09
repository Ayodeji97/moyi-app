package com.moyi.app.feature.today

import com.moyi.app.designsystem.component.AttachedVoiceState
import com.moyi.app.designsystem.platform.PhotoPick
import com.moyi.app.designsystem.platform.PickedPhoto
import com.moyi.app.designsystem.platform.RecordedVoice
import com.moyi.app.designsystem.platform.RecorderEvent
import com.moyi.app.designsystem.platform.VoiceRecording
import com.moyi.app.domain.media.MediaStatus
import com.moyi.app.domain.media.MediaUploader
import com.moyi.app.feature.today.common.TodayStatus
import com.moyi.app.feature.today.common.WriteResult
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
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * A voice note in the composer: recorded through the screen's recorder (a fake here, which
 * answers the view model's events the way the platform recorders do), uploaded at Send
 * after any photo and before the words, kept on every failure.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WriteVoiceTest : MainDispatcherTest() {
    private val loop = FakeDailyLoop().apply { show(dayView(TodayStatus.NotWritten, myEntry = null)) }
    private val media = FakeMedia()
    private var keys = 0
    private val said = RecordedVoice(ByteArray(16) { it.toByte() }, 4_200)

    private fun viewModel(editing: Boolean = false) = WriteViewModel(
        editing,
        loop,
        FakeDraftStore(),
        uploader = MediaUploader(media, newIdempotencyKey = { "complete-${++keys}" }, pollDelays = listOf(0, 0)),
        newIdempotencyKey = { "key-${++keys}" },
        clock = FixedClock(TAP),
    )

    /**
     * The platform recorder, as the screen drives it: started and stopped by the view
     * model's events, reporting back as the real ones do. [answerStart] is what a start
     * reports (Started, or why not); a stop reports [recording] as Finished.
     */
    private class FakeRecorder(private val vm: WriteViewModel) {
        var answerStart: RecorderEvent = RecorderEvent.Started
        var recording: RecordedVoice? = null
        val calls = mutableListOf<String>()

        // Unconfined, as the view model is here: each event is answered before onAction returns.
        fun attach(scope: TestScope) = scope.backgroundScope.launch(UnconfinedTestDispatcher(scope.testScheduler)) {
            vm.events.collect { event ->
                when (event) {
                    WriteEvent.StartRecording -> {
                        calls += "start"
                        vm.onAction(WriteAction.OnRecorderEvent(answerStart))
                    }
                    WriteEvent.StopRecording -> {
                        calls += "stop"
                        val made = recording
                        vm.onAction(WriteAction.OnRecorderEvent(if (made != null) RecorderEvent.Finished(made) else RecorderEvent.Failed))
                    }
                    else -> calls += "$event"
                }
            }
        }
    }

    private fun TestScope.recorderFor(vm: WriteViewModel) = FakeRecorder(vm).apply {
        recording = said
        attach(this@recorderFor)
    }

    private fun WriteViewModel.withWordsAndVoice(): WriteViewModel = apply {
        onAction(WriteAction.OnTextChange(MINE))
        onAction(WriteAction.OnRecordVoiceClick)
        onAction(WriteAction.OnStopRecordingClick)
    }

    @Test
    fun recordingStartsAtTheTapRunsAClockAndStopKeepsWhatWasSaid() = runTest {
        val vm = viewModel()
        val recorder = recorderFor(vm)
        assertTrue(vm.state.value.voiceOffered)

        vm.onAction(WriteAction.OnRecordVoiceClick)
        assertEquals(listOf("start"), recorder.calls)
        assertEquals(AttachedVoiceState.Recording(0), vm.state.value.voice)
        advanceTimeBy(3_100)
        assertEquals(AttachedVoiceState.Recording(3_000), vm.state.value.voice)
        assertFalse(vm.state.value.canSubmit, "Send waits for the recording to stop")

        vm.onAction(WriteAction.OnStopRecordingClick)
        assertEquals(AttachedVoiceState.Recorded(said), vm.state.value.voice)
        advanceTimeBy(10_000)
        assertEquals(AttachedVoiceState.Recorded(said), vm.state.value.voice, "the clock stopped with the recording")
    }

    @Test
    fun theTrashThrowsTheRecordingAwayAtOnceAndTheNextTapRecordsAfresh() = runTest {
        val vm = viewModel()
        val recorder = recorderFor(vm)
        vm.onAction(WriteAction.OnTextChange(MINE))
        vm.onAction(WriteAction.OnRecordVoiceClick)
        advanceTimeBy(2_100)

        vm.onAction(WriteAction.OnDiscardRecordingClick)
        assertEquals(listOf("start", "${WriteEvent.CancelRecording}"), recorder.calls, "cancelled, not stopped: nothing is kept")
        assertNull(vm.state.value.voice)
        assertNull(vm.state.value.voiceNotice, "no question and no notice: it was never sent")
        assertTrue(vm.state.value.canSubmit, "the words can go on their own")
        advanceTimeBy(VoiceRecording.MAX_DURATION_MS)
        assertNull(vm.state.value.voice, "the clock stopped with it")

        vm.onAction(WriteAction.OnRecordVoiceClick)
        assertEquals(AttachedVoiceState.Recording(0), vm.state.value.voice)
    }

    @Test
    fun theTrashDoesNothingOnceTheRecordingIsKept() = runTest {
        val vm = viewModel()
        val recorder = recorderFor(vm)
        vm.onAction(WriteAction.OnRecordVoiceClick)
        vm.onAction(WriteAction.OnStopRecordingClick)
        vm.onAction(WriteAction.OnDiscardRecordingClick)
        assertEquals(AttachedVoiceState.Recorded(said), vm.state.value.voice)
        assertEquals(listOf("start", "stop"), recorder.calls)
    }

    @Test
    fun theRecordingStopsItselfAtTheLimitAndKeepsTheMinute() = runTest {
        val vm = viewModel()
        val recorder = recorderFor(vm)
        vm.onAction(WriteAction.OnRecordVoiceClick)

        advanceTimeBy(VoiceRecording.MAX_DURATION_MS - 300)
        assertTrue(vm.state.value.recording)
        advanceTimeBy(400)

        assertEquals(listOf("start", "stop"), recorder.calls)
        assertIs<AttachedVoiceState.Recorded>(vm.state.value.voice)
    }

    @Test
    fun anInterruptionEndsTheRecordingWhereItIsAndKeepsIt() = runTest {
        val vm = viewModel()
        recorderFor(vm)
        vm.onAction(WriteAction.OnRecordVoiceClick)
        advanceTimeBy(2_000)
        // The platform recorder stopped on its own (a call, the app in the background) and reports what it kept.
        val cut = RecordedVoice(ByteArray(4), 2_000)
        vm.onAction(WriteAction.OnRecorderEvent(RecorderEvent.Finished(cut)))
        assertEquals(AttachedVoiceState.Recorded(cut), vm.state.value.voice)
    }

    @Test
    fun aRefusedMicrophoneSaysWhereToAllowItAndAttachesNothing() = runTest {
        val vm = viewModel()
        recorderFor(vm).answerStart = RecorderEvent.PermissionDenied
        vm.onAction(WriteAction.OnTextChange(MINE))
        vm.onAction(WriteAction.OnRecordVoiceClick)
        assertNull(vm.state.value.voice)
        assertEquals(VoiceNotice.MicrophoneDenied, vm.state.value.voiceNotice)
        assertTrue(vm.state.value.canSubmit, "the words can still go")
    }

    @Test
    fun aRecordingThatFailedSaysSoAndTheNextTapTriesAgain() = runTest {
        val vm = viewModel()
        val recorder = recorderFor(vm)
        recorder.recording = null
        vm.onAction(WriteAction.OnRecordVoiceClick)
        vm.onAction(WriteAction.OnStopRecordingClick)
        assertNull(vm.state.value.voice)
        assertEquals(VoiceNotice.CouldNotRecord, vm.state.value.voiceNotice)

        recorder.recording = said
        vm.onAction(WriteAction.OnRecordVoiceClick)
        assertNull(vm.state.value.voiceNotice)
        assertTrue(vm.state.value.recording)
    }

    @Test
    fun sendingUploadsTheVoiceNoteThenSendsTheWordsWithItsId() = runTest {
        val vm = viewModel()
        recorderFor(vm)
        vm.withWordsAndVoice()
        vm.onAction(WriteAction.OnSubmit)

        assertEquals(listOf("intent", "put", "complete:m1", "status:m1"), media.calls)
        val sent = loop.submits.single()
        assertEquals("m1", sent.voiceMediaId)
        assertNull(sent.imageMediaId)
        assertEquals(TAP, sent.intendedAt)
    }

    @Test
    fun whileTheVoiceNoteIsOnItsWayTheActionSaysSo() = runTest {
        val gate = CompletableDeferred<Unit>()
        media.putGate = gate
        val vm = viewModel()
        recorderFor(vm)
        vm.withWordsAndVoice()
        vm.onAction(WriteAction.OnSubmit)
        assertTrue(vm.state.value.sendingVoice)
        // Nothing about it can change while it is being sent.
        vm.onAction(WriteAction.OnRemoveVoice)
        vm.onAction(WriteAction.OnRecordAgainClick)
        assertEquals(AttachedVoiceState.Recorded(said), vm.state.value.voice)
        gate.complete(Unit)
        assertFalse(vm.state.value.sendingVoice)
    }

    @Test
    fun aPhotoAndAVoiceNoteGoTogetherThePhotoFirst() = runTest {
        val gate = CompletableDeferred<Unit>()
        media.putGate = gate
        val vm = viewModel()
        recorderFor(vm)
        vm.withWordsAndVoice()
        vm.onAction(WriteAction.OnPhotoPicked(PhotoPick.Picked(PickedPhoto(ByteArray(3), 1, 1))))
        vm.onAction(WriteAction.OnSubmit)
        assertTrue(vm.state.value.sendingPhoto)
        assertFalse(vm.state.value.sendingVoice)
        gate.complete(Unit)

        val sent = loop.submits.single()
        assertEquals("m1", sent.imageMediaId)
        assertEquals("m2", sent.voiceMediaId)
    }

    @Test
    fun aFailedUploadKeepsTheWordsAndTheRecordingAndTryAgainResumes() = runTest {
        media.failAt = "complete"
        val vm = viewModel()
        recorderFor(vm)
        vm.withWordsAndVoice()
        vm.onAction(WriteAction.OnSubmit)

        assertEquals(WriteSubmit.Failed, vm.state.value.submit)
        assertEquals(MINE, vm.state.value.text)
        assertEquals(AttachedVoiceState.Recorded(said), vm.state.value.voice)
        assertTrue(loop.submits.isEmpty(), "no words go without their voice note")

        media.failAt = null
        vm.onAction(WriteAction.OnSubmit)
        assertEquals(1, media.calls.count { it == "put" }, "the bytes went to storage once")
        assertEquals(listOf("complete-1", "complete-1"), media.completeKeys, "the same completion, replayed")
        assertEquals("m1", loop.submits.single().voiceMediaId)
    }

    @Test
    fun anEntryThatFailsAfterTheVoiceNoteIsReadyRetriesTheIdenticalRequest() = runTest {
        loop.submitResult = WriteResult.Failed
        val vm = viewModel()
        recorderFor(vm)
        vm.withWordsAndVoice()
        vm.onAction(WriteAction.OnSubmit)
        loop.submitResult = WriteResult.Saved
        vm.onAction(WriteAction.OnSubmit)

        assertEquals(1, media.calls.count { it == "intent" })
        assertEquals(loop.submits[0], loop.submits[1], "same words, voice note, key and intendedAt")
    }

    @Test
    fun aVoiceNoteTheServerRefusesSaysSoAndOnlyRemovingItLetsTheWordsGo() = runTest {
        media.finalStatus = MediaStatus.Quarantined
        val vm = viewModel()
        recorderFor(vm)
        vm.withWordsAndVoice()
        vm.onAction(WriteAction.OnSubmit)

        assertEquals(VoiceNotice.Refused, vm.state.value.voiceNotice)
        assertEquals(WriteSubmit.Idle, vm.state.value.submit)
        assertFalse(vm.state.value.canSubmit)

        vm.onAction(WriteAction.OnRemoveVoice)
        assertNull(vm.state.value.voice)
        assertNull(vm.state.value.voiceNotice)
        assertTrue(vm.state.value.canSubmit)
        vm.onAction(WriteAction.OnSubmit)
        assertNull(loop.submits.single().voiceMediaId)
    }

    @Test
    fun aVoiceNoteTheEntryCouldNotAttachIsRefusedTheSameWay() = runTest {
        loop.submitResult = WriteResult.VoiceRefused
        val vm = viewModel()
        recorderFor(vm)
        vm.withWordsAndVoice()
        vm.onAction(WriteAction.OnSubmit)
        assertEquals(VoiceNotice.Refused, vm.state.value.voiceNotice)
        assertEquals(AttachedVoiceState.Recorded(said), vm.state.value.voice)
        assertEquals(MINE, vm.state.value.text)
    }

    @Test
    fun recordingAgainOrRemovingAnUploadedVoiceNoteDeletesTheUpload() = runTest {
        loop.submitResult = WriteResult.Failed
        val vm = viewModel()
        val recorder = recorderFor(vm)
        vm.withWordsAndVoice()
        vm.onAction(WriteAction.OnSubmit)

        val again = RecordedVoice(ByteArray(2), 1_000)
        recorder.recording = again
        vm.onAction(WriteAction.OnRecordAgainClick)
        assertEquals(listOf("m1"), media.deleted)
        assertTrue(vm.state.value.recording)
        vm.onAction(WriteAction.OnStopRecordingClick)
        assertEquals(AttachedVoiceState.Recorded(again), vm.state.value.voice)
        assertEquals(WriteSubmit.Idle, vm.state.value.submit)

        loop.submitResult = WriteResult.Saved
        vm.onAction(WriteAction.OnSubmit)
        assertEquals(2, media.calls.count { it == "intent" }, "the new recording is a new upload")
        assertEquals("m2", loop.submits.last().voiceMediaId)
    }

    @Test
    fun closingWhileRecordingStopsItKeepsItAndAsksFirst() = runTest {
        val vm = viewModel()
        val recorder = recorderFor(vm)
        vm.onAction(WriteAction.OnRecordVoiceClick)
        vm.onAction(WriteAction.OnCloseClick)
        assertEquals(listOf("start", "stop"), recorder.calls)
        assertTrue(vm.state.value.confirmingDiscard)
        assertSame(said, vm.state.value.recordedVoice?.voice)
    }

    @Test
    fun aVoiceNoteNeverStandsInForWordsAndIsNotOfferedWhileEditing() = runTest {
        val vm = viewModel()
        recorderFor(vm)
        vm.onAction(WriteAction.OnRecordVoiceClick)
        vm.onAction(WriteAction.OnStopRecordingClick)
        assertFalse(vm.state.value.canSubmit)

        loop.show(dayView(TodayStatus.Waiting))
        assertFalse(viewModel(editing = true).state.value.voiceOffered)
    }
}
