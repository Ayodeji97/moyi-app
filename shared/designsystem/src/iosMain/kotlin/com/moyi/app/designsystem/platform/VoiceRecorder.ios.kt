@file:OptIn(ExperimentalForeignApi::class)

package com.moyi.app.designsystem.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import platform.AVFAudio.AVAudioApplication
import platform.AVFAudio.AVAudioApplicationRecordPermissionDenied
import platform.AVFAudio.AVAudioApplicationRecordPermissionGranted
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioQualityHigh
import platform.AVFAudio.AVAudioRecorder
import platform.AVFAudio.AVAudioRecorderDelegateProtocol
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryOptionDefaultToSpeaker
import platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
import platform.AVFAudio.AVAudioSessionInterruptionNotification
import platform.AVFAudio.AVAudioSessionInterruptionTypeBegan
import platform.AVFAudio.AVAudioSessionInterruptionTypeKey
import platform.AVFAudio.AVAudioSessionModeDefault
import platform.AVFAudio.AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation
import platform.AVFAudio.AVEncoderAudioQualityKey
import platform.AVFAudio.AVEncoderBitRateKey
import platform.AVFAudio.AVFormatIDKey
import platform.AVFAudio.AVNumberOfChannelsKey
import platform.AVFAudio.AVSampleRateKey
import platform.AVFAudio.setActive
import platform.CoreAudioTypes.kAudioFormatMPEG4AAC
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSNotification
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNumber
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.dataWithContentsOfURL
import platform.darwin.NSObject
import platform.darwin.NSObjectProtocol
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

@Composable
actual fun rememberVoiceRecorder(onEvent: (RecorderEvent) -> Unit): VoiceRecorder {
    val currentOnEvent by rememberUpdatedState(onEvent)
    val scope = rememberCoroutineScope()
    val recorder = remember { IosVoiceRecorder(scope) { currentOnEvent(it) } }
    // Leaving Write mid-recording throws the recording away: nothing is left on disk.
    DisposableEffect(recorder) { onDispose { recorder.cancel() } }
    return recorder
}

/**
 * `AVAudioRecorder` into one file in the app's temporary directory, the only place a voice
 * note is ever on disk, and only while it records: when it stops, the file is read into
 * memory and deleted. The audio session is PlayAndRecord while it records and let go after.
 */
private class IosVoiceRecorder(scope: CoroutineScope, private val onEvent: (RecorderEvent) -> Unit) : VoiceRecorder {
    private val meter = RecordingLevels(scope)
    override val levels: StateFlow<List<Float>> = meter.levels
    private var recorder: AVAudioRecorder? = null
    private var file: NSURL? = null
    private var discarding = false
    private var interruptions: NSObjectProtocol? = null

    /** Held strongly: `AVAudioRecorder.delegate` is weak. */
    private val delegate = object : NSObject(), AVAudioRecorderDelegateProtocol {
        override fun audioRecorderDidFinishRecording(recorder: AVAudioRecorder, successfully: Boolean) {
            finished(successfully)
        }
    }

    override fun start() {
        if (recorder != null) return
        val app = AVAudioApplication.sharedInstance
        when (app.recordPermission) {
            AVAudioApplicationRecordPermissionGranted -> begin()
            AVAudioApplicationRecordPermissionDenied -> onEvent(RecorderEvent.PermissionDenied)
            // Undetermined: asked now, at the tap, with NSMicrophoneUsageDescription.
            else -> AVAudioApplication.requestRecordPermissionWithCompletionHandler { granted ->
                dispatch_async(dispatch_get_main_queue()) {
                    if (granted) begin() else onEvent(RecorderEvent.PermissionDenied)
                }
            }
        }
    }

    private fun begin() {
        if (recorder != null) return
        sweepLeftovers()
        val session = AVAudioSession.sharedInstance()
        val url = NSURL.fileURLWithPath(NSTemporaryDirectory() + PREFIX + NSUUID().UUIDString + ".m4a")
        val settings = mapOf<Any?, Any>(
            AVFormatIDKey to NSNumber(unsignedInt = kAudioFormatMPEG4AAC),
            AVSampleRateKey to NSNumber(double = VoiceRecording.SAMPLE_RATE.toDouble()),
            AVNumberOfChannelsKey to NSNumber(int = VoiceRecording.CHANNELS),
            AVEncoderBitRateKey to NSNumber(int = VoiceRecording.BIT_RATE),
            AVEncoderAudioQualityKey to NSNumber(long = AVAudioQualityHigh),
        )
        val ready = session.setCategory(
            AVAudioSessionCategoryPlayAndRecord,
            mode = AVAudioSessionModeDefault,
            options = AVAudioSessionCategoryOptionDefaultToSpeaker,
            error = null,
        ) && session.setActive(true, error = null)
        val made = if (ready) failable { AVAudioRecorder(uRL = url, settings = settings, error = null) } else null
        if (made == null) {
            letGoOfSession()
            onEvent(RecorderEvent.Failed)
            return
        }
        made.delegate = delegate
        // Metering costs a little CPU while recording, and gives the live waveform its levels.
        made.meteringEnabled = true
        discarding = false
        recorder = made
        file = url
        // A call or Siri takes the microphone: stop, keeping what was said.
        interruptions = NSNotificationCenter.defaultCenter.addObserverForName(
            name = AVAudioSessionInterruptionNotification,
            `object` = session,
            queue = NSOperationQueue.mainQueue,
        ) { note: NSNotification? ->
            val type = (note?.userInfo?.get(AVAudioSessionInterruptionTypeKey) as? NSNumber)?.unsignedLongValue
            if (type == AVAudioSessionInterruptionTypeBegan) stop()
        }
        // The platform stops it at the limit too: recordForDuration ends it and calls the delegate.
        if (!made.prepareToRecord() || !made.recordForDuration(VoiceRecording.MAX_DURATION_MS / MS_PER_SECOND)) {
            discarding = true
            made.stop()
            cleanUp()
            onEvent(RecorderEvent.Failed)
            return
        }
        meter.start { readPeak() }
        onEvent(RecorderEvent.Started)
    }

    override fun stop() {
        recorder?.stop() // The delegate reports it.
    }

    override fun cancel() {
        val made = recorder ?: return
        discarding = true
        meter.clear()
        made.stop()
        cleanUp()
    }

    /** The peak since the meters were last updated, in decibels, as a level from 0 to 1. */
    private fun readPeak(): Float {
        val made = recorder ?: return 0f
        made.updateMeters()
        return levelFromDecibels(made.peakPowerForChannel(0u))
    }

    private fun finished(successfully: Boolean) {
        if (discarding || recorder == null) return
        val heard = meter.stop()
        val url = file
        val data = if (successfully && url != null) NSData.dataWithContentsOfURL(url) else null
        val bytes = data?.copyBytes()
        val durationMs = data?.let { failable { AVAudioPlayer(data = it, error = null) }?.duration }?.let { (it * MS_PER_SECOND).toLong() }
        cleanUp()
        onEvent(
            if (bytes != null && bytes.isNotEmpty() && durationMs != null && durationMs > 0) {
                RecorderEvent.Finished(RecordedVoice(bytes, durationMs, heard))
            } else {
                meter.clear()
                RecorderEvent.Failed
            },
        )
    }

    private fun cleanUp() {
        interruptions?.let(NSNotificationCenter.defaultCenter::removeObserver)
        interruptions = null
        file?.let { NSFileManager.defaultManager.removeItemAtURL(it, error = null) }
        file = null
        recorder = null
        letGoOfSession()
    }

    /** A recording a killed process left behind is deleted before the next one starts. */
    private fun sweepLeftovers() {
        val files = NSFileManager.defaultManager.contentsOfDirectoryAtPath(NSTemporaryDirectory(), error = null).orEmpty()
        files.filterIsInstance<String>().filter { it.startsWith(PREFIX) }.forEach {
            NSFileManager.defaultManager.removeItemAtPath(NSTemporaryDirectory() + it, error = null)
        }
    }

    private fun letGoOfSession() {
        AVAudioSession.sharedInstance().setActive(false, withOptions = AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation, error = null)
    }

    private companion object {
        const val MS_PER_SECOND = 1000.0
        const val PREFIX = "voice-"
    }
}
