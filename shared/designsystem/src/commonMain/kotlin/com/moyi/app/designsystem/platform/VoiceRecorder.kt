package com.moyi.app.designsystem.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.StateFlow

/**
 * A voice note recorded on the phone, read back into memory and its file deleted: AAC in
 * an MPEG-4 container (`audio/mp4`), which the backend accepts from both platforms
 * (`VoiceFormat.MP4`: Android's `MediaRecorder` writes the `mp42` brand, iOS's
 * `AVAudioRecorder` the `M4A ` brand) and transcodes to Opus.
 *
 * [levels] is how loud it was as it recorded ([RecordingLevels]), for the listen-back
 * waveform; empty when the recorder could not meter it. It stays on the phone.
 *
 * Treated as immutable: nothing writes to [bytes] after it is made. Never printed: what
 * someone said is entry content.
 */
@Immutable
class RecordedVoice(
    val bytes: ByteArray,
    val durationMs: Long,
    val levels: List<Float> = emptyList(),
) {
    val contentType: String get() = VoiceRecording.CONTENT_TYPE

    override fun toString(): String = "RecordedVoice(${durationMs}ms, ${bytes.size} bytes)"
}

/** What the platform recorder reports, always on the main thread. */
sealed interface RecorderEvent {
    /** The microphone is open and recording. */
    data object Started : RecorderEvent

    /**
     * Recording ended: stopped by the person, at [VoiceRecording.MAX_DURATION_MS], or
     * interrupted (a call, another app taking the microphone, the app leaving the screen).
     * Whatever was recorded until then is kept.
     */
    data class Finished(val voice: RecordedVoice) : RecorderEvent

    /** The person said no to the microphone, now or before. */
    data object PermissionDenied : RecorderEvent

    /** The microphone could not be opened, or nothing usable was recorded. */
    data object Failed : RecorderEvent
}

/**
 * The phone's own recorder (expect/actual):
 *
 * - **Android**: `MediaRecorder`, AAC in MPEG-4, mono, 44.1 kHz, 64 kbit/s, into a file
 *   in the app's private cache (MPEG-4 needs a seekable file: its index is written last),
 *   read back and deleted the moment it stops. `RECORD_AUDIO` is asked for at the first
 *   tap, not before. It holds transient exclusive audio focus while recording, and a
 *   loss of focus (a call) stops it, keeping what was said.
 * - **iOS**: `AVAudioRecorder` with the same settings, into the app's temporary
 *   directory, the `AVAudioSession` in PlayAndRecord while it runs. The microphone is
 *   asked for at the first tap (`NSMicrophoneUsageDescription`); an interruption stops it,
 *   keeping what was said.
 *
 * Both meter the input as they record: Android polls `MediaRecorder.getMaxAmplitude`, iOS
 * turns on `meteringEnabled` and reads `peakPower`, each every [RecordingLevels.TICK_MS].
 *
 * Leaving the composition cancels a recording (its file is deleted, nothing reported).
 */
interface VoiceRecorder {
    /**
     * The microphone's level while it records ([RecordingLevels]), for the live waveform.
     * Not application state: it is drawn and kept with the [RecordedVoice], nothing else.
     */
    val levels: StateFlow<List<Float>>

    /** Asks for the microphone if it must, then starts. Reports [RecorderEvent.Started] or why not. */
    fun start()

    /** Stops and reports [RecorderEvent.Finished] (or [RecorderEvent.Failed]). Nothing when not recording. */
    fun stop()

    /** Stops and throws the recording away, reporting nothing. */
    fun cancel()
}

/** [onEvent] is called on the main thread. */
@Composable
expect fun rememberVoiceRecorder(onEvent: (RecorderEvent) -> Unit): VoiceRecorder

/** The settings both platforms record with, and the backend's limits (`VoiceRules`, `MediaKind.VOICE`). */
object VoiceRecording {
    /** Declared at the upload intent; the backend reads the bytes and holds the declaration to them. */
    const val CONTENT_TYPE = "audio/mp4"

    /** Owner's ruling Q3: a voice note is at most 60 seconds (the backend allows one more as grace). */
    const val MAX_DURATION_MS = 60_000L

    /**
     * Speech quality well above what is kept (Opus at 24 kbit/s, mono): a minute is about
     * 480 KB, a tenth of the backend's 5 MiB cap.
     */
    const val BIT_RATE = 64_000
    const val SAMPLE_RATE = 44_100
    const val CHANNELS = 1

    /** The backend's upload cap for a voice note (`MediaKind.VOICE`: 5 MiB). */
    const val MAX_BYTES = 5L * 1024 * 1024
}
