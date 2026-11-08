package com.moyi.app.designsystem.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import java.io.File
import java.util.UUID

@Composable
actual fun rememberVoiceRecorder(onEvent: (RecorderEvent) -> Unit): VoiceRecorder {
    val context = LocalContext.current.applicationContext
    val currentOnEvent by rememberUpdatedState(onEvent)
    val recorder = remember(context) { AndroidVoiceRecorder(context) { currentOnEvent(it) } }
    // Asked at the tap on "Record a voice note", never before (14 §: permissions in context).
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) recorder.begin() else currentOnEvent(RecorderEvent.PermissionDenied)
    }
    recorder.askForMicrophone = { permission.launch(Manifest.permission.RECORD_AUDIO) }
    // Leaving Write mid-recording throws the recording away: nothing is left on disk.
    DisposableEffect(recorder) { onDispose { recorder.cancel() } }
    return recorder
}

/**
 * `MediaRecorder` into one file in `cacheDir/voice`, the only place a voice note is ever
 * on disk, and only while it records: [finish] reads it into memory and deletes it, and
 * [begin] first deletes anything a killed process left there.
 */
private class AndroidVoiceRecorder(
    private val context: Context,
    private val onEvent: (RecorderEvent) -> Unit,
) : VoiceRecorder {
    var askForMicrophone: () -> Unit = {}
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var focus: AudioFocusRequest? = null

    /** A call, or another app taking the microphone or the audio: stop, keeping what was said. */
    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        if (change < 0) stop()
    }

    override fun start() {
        if (recorder != null) return
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (granted) begin() else askForMicrophone()
    }

    fun begin() {
        if (recorder != null) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            onEvent(RecorderEvent.PermissionDenied)
            return
        }
        val dir = File(context.cacheDir, DIRECTORY).apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, "${UUID.randomUUID()}.m4a")
        val made = newRecorder()
        try {
            made.setAudioSource(MediaRecorder.AudioSource.MIC)
            made.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            made.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            made.setAudioChannels(VoiceRecording.CHANNELS)
            made.setAudioSamplingRate(VoiceRecording.SAMPLE_RATE)
            made.setAudioEncodingBitRate(VoiceRecording.BIT_RATE)
            made.setMaxDuration(VoiceRecording.MAX_DURATION_MS.toInt())
            made.setMaxFileSize(VoiceRecording.MAX_BYTES)
            made.setOutputFile(target.absolutePath)
            made.setOnInfoListener { _, what, _ ->
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED ||
                    what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED
                ) {
                    stop()
                }
            }
            made.setOnErrorListener { _, _, _ -> stop() }
            made.prepare()
            requestFocus()
            made.start()
        } catch (_: Exception) {
            made.release()
            target.delete()
            abandonFocus()
            onEvent(RecorderEvent.Failed)
            return
        }
        recorder = made
        file = target
        onEvent(RecorderEvent.Started)
    }

    override fun stop() {
        val made = recorder ?: return
        val target = file
        recorder = null
        file = null
        val stopped = try {
            made.stop()
            true
        } catch (_: RuntimeException) {
            // Stopped before any sound was written: there is no recording.
            false
        } finally {
            made.release()
            abandonFocus()
        }
        val voice = if (stopped && target != null) read(target) else null
        target?.delete()
        onEvent(if (voice != null) RecorderEvent.Finished(voice) else RecorderEvent.Failed)
    }

    override fun cancel() {
        val made = recorder ?: return
        val target = file
        recorder = null
        file = null
        try {
            made.stop()
        } catch (_: RuntimeException) {
            // Nothing written; it is thrown away either way.
        } finally {
            made.release()
            abandonFocus()
            target?.delete()
        }
    }

    private fun read(target: File): RecordedVoice? = try {
        val bytes = target.readBytes()
        val durationMs = MediaMetadataRetriever().run {
            try {
                setDataSource(target.absolutePath)
                extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            } finally {
                release()
            }
        }
        if (bytes.isEmpty() || durationMs == null || durationMs <= 0) null else RecordedVoice(bytes, durationMs)
    } catch (_: Exception) {
        null
    }

    @Suppress("DEPRECATION")
    private fun newRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()

    private fun requestFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                .setOnAudioFocusChangeListener(focusListener)
                .build()
            focus = request
            audio.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audio.requestAudioFocus(focusListener, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
        }
    }

    private fun abandonFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focus?.let(audio::abandonAudioFocusRequest)
            focus = null
        } else {
            @Suppress("DEPRECATION")
            audio.abandonAudioFocus(focusListener)
        }
    }

    private companion object {
        const val DIRECTORY = "voice"
    }
}
