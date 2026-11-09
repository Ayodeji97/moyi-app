package com.moyi.app.feature.today

import app.cash.turbine.test
import com.moyi.app.core.DataError
import com.moyi.app.core.EmptyResult
import com.moyi.app.core.Result
import com.moyi.app.designsystem.platform.PhotoPick
import com.moyi.app.designsystem.platform.PickedPhoto
import com.moyi.app.domain.media.MediaBytes
import com.moyi.app.domain.media.MediaRepository
import com.moyi.app.domain.media.MediaStatus
import com.moyi.app.domain.media.MediaUploader
import com.moyi.app.domain.media.UploadTicket
import com.moyi.app.feature.today.common.TodayStatus
import com.moyi.app.feature.today.common.WriteResult
import com.moyi.app.feature.today.write.PhotoNotice
import com.moyi.app.feature.today.write.PhotoSource
import com.moyi.app.feature.today.write.WriteAction
import com.moyi.app.feature.today.write.WriteEvent
import com.moyi.app.feature.today.write.WriteSubmit
import com.moyi.app.feature.today.write.WriteViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** A photo in the composer: picked, uploaded at Send before the words, kept on every failure. */
class WritePhotoTest : MainDispatcherTest() {
    private val loop = FakeDailyLoop().apply { show(dayView(TodayStatus.NotWritten, myEntry = null)) }
    private val media = FakeMedia()
    private var keys = 0
    private val photo = PickedPhoto(ByteArray(10) { it.toByte() }, 4, 3)

    private fun viewModel(editing: Boolean = false) = WriteViewModel(
        editing,
        loop,
        FakeDraftStore(),
        uploader = MediaUploader(media, newIdempotencyKey = { "complete-${++keys}" }, pollDelays = listOf(0, 0)),
        newIdempotencyKey = { "key-${++keys}" },
        clock = FixedClock(TAP),
    )

    private fun WriteViewModel.withPhotoAndWords(): WriteViewModel = apply {
        onAction(WriteAction.OnTextChange(MINE))
        onAction(WriteAction.OnPhotoPicked(PhotoPick.Picked(photo)))
    }

    @Test
    fun addAPhotoAsksLibraryOrCameraOnlyWhenThereIsACamera() = runTest {
        val vm = viewModel()
        assertTrue(vm.state.value.photosOffered)
        vm.events.test {
            vm.onAction(WriteAction.OnAddPhotoClick(canTakePhoto = false))
            assertEquals(WriteEvent.PickPhoto(PhotoSource.Library), awaitItem())

            vm.onAction(WriteAction.OnAddPhotoClick(canTakePhoto = true))
            assertTrue(vm.state.value.choosingPhotoSource)
            vm.onAction(WriteAction.OnTakePhoto)
            assertFalse(vm.state.value.choosingPhotoSource)
            assertEquals(WriteEvent.PickPhoto(PhotoSource.Camera), awaitItem())
        }
    }

    @Test
    fun aPhotoIsOfferedWhileEditingToo() {
        written()
        // §60: Edit can replace the entry's photo, or add one to an entry with none.
        assertTrue(viewModel(editing = true).state.value.photosOffered)
    }

    @Test
    fun sendingUploadsThePhotoFirstThenSendsTheWordsWithItsId() = runTest {
        val vm = viewModel().withPhotoAndWords()
        vm.events.test {
            vm.onAction(WriteAction.OnSubmit)
            assertEquals(WriteEvent.Close, awaitItem())
        }
        assertEquals(listOf("intent", "put", "complete:m1", "status:m1"), media.calls)
        assertEquals("m1", loop.submits.single().imageMediaId)
        assertEquals(TAP, loop.submits.single().intendedAt, "intendedAt is the tap, not when the upload finished")
    }

    @Test
    fun whileThePhotoIsOnItsWayTheActionSaysSoThenTheWordsAreSending() = runTest {
        val gate = CompletableDeferred<Unit>()
        media.putGate = gate
        val vm = viewModel().withPhotoAndWords()
        vm.onAction(WriteAction.OnSubmit)
        assertEquals(WriteSubmit.Sending, vm.state.value.submit)
        assertTrue(vm.state.value.sendingPhoto)
        // Nothing about the photo can change while it is being sent.
        vm.onAction(WriteAction.OnRemovePhoto)
        assertSame(photo, vm.state.value.photo)

        loop.gate = CompletableDeferred()
        gate.complete(Unit)
        assertFalse(vm.state.value.sendingPhoto)
        assertEquals(WriteSubmit.Sending, vm.state.value.submit)
        loop.gate?.complete(Unit)
    }

    @Test
    fun aFailureAtEachUploadStepKeepsTheWordsAndThePhotoAndSendsNoWords() = runTest {
        for (step in listOf("intent", "put", "complete", "status")) {
            val failing = FakeMedia().apply { failAt = step }
            val vm = WriteViewModel(
                false,
                loop,
                FakeDraftStore(),
                uploader = MediaUploader(failing, newIdempotencyKey = { "k" }, pollDelays = listOf(0)),
                clock = FixedClock(TAP),
            ).withPhotoAndWords()

            vm.onAction(WriteAction.OnSubmit)

            assertEquals(WriteSubmit.Failed, vm.state.value.submit, step)
            assertEquals(MINE, vm.state.value.text, step)
            assertSame(photo, vm.state.value.photo, step)
            assertFalse(vm.state.value.sendingPhoto, step)
        }
        assertTrue(loop.submits.isEmpty(), "no words go without their photo")
    }

    @Test
    fun tryAgainResumesTheUploadAndTheBytesGoToStorageOnce() = runTest {
        media.failAt = "complete"
        val vm = viewModel().withPhotoAndWords()
        vm.onAction(WriteAction.OnSubmit)
        assertEquals(WriteSubmit.Failed, vm.state.value.submit)

        media.failAt = null
        vm.events.test {
            vm.onAction(WriteAction.OnSubmit)
            assertEquals(WriteEvent.Close, awaitItem())
        }
        assertEquals(1, media.calls.count { it == "put" })
        assertEquals(listOf("complete:m1", "complete:m1"), media.calls.filter { it.startsWith("complete") })
        assertEquals(listOf("complete-1", "complete-1"), media.completeKeys, "the same completion, replayed")
    }

    @Test
    fun anEntryThatFailsAfterThePhotoIsReadyRetriesWithTheSamePhotoAndTheSameKey() = runTest {
        loop.submitResult = WriteResult.Failed
        val vm = viewModel().withPhotoAndWords()
        vm.onAction(WriteAction.OnSubmit)
        assertEquals(WriteSubmit.Failed, vm.state.value.submit)

        loop.submitResult = WriteResult.Saved
        vm.onAction(WriteAction.OnSubmit)

        assertEquals(1, media.calls.count { it == "intent" }, "uploaded once")
        assertEquals(2, loop.submits.size)
        assertEquals(loop.submits[0], loop.submits[1], "the identical request: same words, photo, key and intendedAt")
    }

    @Test
    fun aPhotoTheServerRefusesSaysSoAndOnlyRemovingItLetsTheWordsGo() = runTest {
        media.finalStatus = MediaStatus.Quarantined
        val vm = viewModel().withPhotoAndWords()
        vm.onAction(WriteAction.OnSubmit)

        assertEquals(PhotoNotice.Refused, vm.state.value.photoNotice)
        assertEquals(WriteSubmit.Idle, vm.state.value.submit)
        assertFalse(vm.state.value.canSubmit)
        assertSame(photo, vm.state.value.photo)

        vm.onAction(WriteAction.OnRemovePhoto)
        assertNull(vm.state.value.photo)
        assertTrue(vm.state.value.canSubmit)
        vm.onAction(WriteAction.OnSubmit)
        assertNull(loop.submits.single().imageMediaId)
    }

    @Test
    fun aPhotoTheEntryCouldNotAttachIsRefusedTheSameWay() = runTest {
        loop.submitResult = WriteResult.PhotoRefused
        val vm = viewModel().withPhotoAndWords()
        vm.onAction(WriteAction.OnSubmit)
        assertEquals(PhotoNotice.Refused, vm.state.value.photoNotice)
        assertSame(photo, vm.state.value.photo)
        assertEquals(MINE, vm.state.value.text)
    }

    @Test
    fun anUnreadablePickSaysSoAndKeepsWhatWasAttached() {
        val vm = viewModel().withPhotoAndWords()
        vm.onAction(WriteAction.OnPhotoPicked(PhotoPick.Unreadable))
        assertEquals(PhotoNotice.Unreadable, vm.state.value.photoNotice)
        assertSame(photo, vm.state.value.photo)
    }

    @Test
    fun replacingOrRemovingAnUploadedPhotoDeletesTheUploadAndTheNextSendStartsAfresh() = runTest {
        loop.submitResult = WriteResult.Failed
        val vm = viewModel().withPhotoAndWords()
        vm.onAction(WriteAction.OnSubmit)
        val other = PickedPhoto(ByteArray(3), 1, 1)
        vm.onAction(WriteAction.OnPhotoPicked(PhotoPick.Picked(other)))

        assertSame(other, vm.state.value.photo)
        assertEquals(WriteSubmit.Idle, vm.state.value.submit)
        assertEquals(listOf("m1"), media.deleted)
        loop.submitResult = WriteResult.Saved
        vm.onAction(WriteAction.OnSubmit)
        assertEquals(2, media.calls.count { it == "intent" })
    }

    @Test
    fun closingWithOnlyAPhotoAsksFirst() = runTest {
        val vm = viewModel()
        vm.onAction(WriteAction.OnPhotoPicked(PhotoPick.Picked(photo)))
        vm.onAction(WriteAction.OnCloseClick)
        assertTrue(vm.state.value.confirmingDiscard)
    }

    @Test
    fun aPhotoNeverStandsInForWords() {
        val vm = viewModel()
        vm.onAction(WriteAction.OnPhotoPicked(PhotoPick.Picked(photo)))
        assertFalse(vm.state.value.canSubmit)
    }

    private fun written() = loop.show(dayView(TodayStatus.Waiting))
}

/** A [MediaRepository] that answers each step, fails at [failAt] with no answer, and records the calls. */
internal class FakeMedia : MediaRepository {
    var failAt: String? = null
    var finalStatus = MediaStatus.Ready
    var putGate: CompletableDeferred<Unit>? = null
    val calls = mutableListOf<String>()
    val completeKeys = mutableListOf<String>()
    val deleted = mutableListOf<String>()
    private var intents = 0

    override suspend fun requestUpload(media: MediaBytes): Result<UploadTicket, DataError.Network> {
        calls += "intent"
        if (failAt == "intent") return Result.Failure(DataError.Network.NoInternet)
        intents++
        return Result.Success(UploadTicket("m$intents", "http://storage.test/k$intents?sig", mapOf("Content-Type" to "image/jpeg")))
    }

    override suspend fun upload(ticket: UploadTicket, media: MediaBytes): EmptyResult<DataError.Network> {
        calls += "put"
        putGate?.await()
        return if (failAt == "put") Result.Failure(DataError.Network.Timeout) else Result.Success(Unit)
    }

    override suspend fun complete(mediaId: String, idempotencyKey: String): Result<MediaStatus, DataError.Network> {
        calls += "complete:$mediaId"
        completeKeys += idempotencyKey
        return if (failAt == "complete") Result.Failure(DataError.Network.NoInternet) else Result.Success(MediaStatus.Processing)
    }

    override suspend fun status(mediaId: String): Result<MediaStatus, DataError.Network> {
        calls += "status:$mediaId"
        return if (failAt == "status") Result.Failure(DataError.Network.NoInternet) else Result.Success(finalStatus)
    }

    override suspend fun delete(mediaId: String): EmptyResult<DataError.Network> {
        deleted += mediaId
        return Result.Success(Unit)
    }
}
