package com.moyi.app.designsystem.platform

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.log10

/**
 * How loud the microphone is while a voice note records, sampled every [TICK_MS] into a
 * list of levels from 0 (silence) to 1 (as loud as the meter reads). The Write screen
 * draws the list as it grows; when the recording stops, the list is kept with the
 * [RecordedVoice], so listening back draws the shape of what was actually said.
 *
 * Levels are a picture of loudness, not the sound: a few hundred numbers that cannot be
 * turned back into speech. They are never sent and never logged.
 *
 * Driven on the main thread, as the platform recorders are.
 */
class RecordingLevels(private val scope: CoroutineScope) {
    private val samples = MutableStateFlow<List<Float>>(emptyList())

    /** The levels so far, oldest first. Empty when nothing is recording. */
    val levels: StateFlow<List<Float>> = samples.asStateFlow()

    private var sampling: Job? = null

    /** Starts afresh, asking [read] for the level (0..1) every [TICK_MS]. */
    fun start(read: () -> Float) {
        sampling?.cancel()
        samples.value = emptyList()
        sampling = scope.launch {
            while (isActive) {
                delay(TICK_MS)
                val level = read().coerceIn(0f, 1f)
                samples.update { if (it.size >= MAX_SAMPLES) it else it + level }
            }
        }
    }

    /** Stops sampling and returns what was sampled. The list stays drawn until [clear]. */
    fun stop(): List<Float> {
        sampling?.cancel()
        sampling = null
        return samples.value
    }

    /** Stops and forgets: a cancelled or failed recording leaves nothing drawn. */
    fun clear() {
        stop()
        samples.value = emptyList()
    }

    companion object {
        /** About twelve a second: fast enough to follow syllables, slow enough to stay calm. */
        const val TICK_MS = 80L

        /** The most a minute's recording can hold, with room to spare: never unbounded. */
        const val MAX_SAMPLES = (VoiceRecording.MAX_DURATION_MS / TICK_MS).toInt() + 16
    }
}

/**
 * The meter's decibels (0 is full scale, iOS `AVAudioRecorder.peakPower`) as a level from 0
 * to 1: [FLOOR_DB] and quieter is 0, full scale is 1, linear in decibels between, which is
 * roughly how loudness is heard. Pure, so both platforms map the same way.
 */
fun levelFromDecibels(db: Float): Float {
    if (db.isNaN()) return 0f
    return ((db - FLOOR_DB) / -FLOOR_DB).coerceIn(0f, 1f)
}

/**
 * Android's `MediaRecorder.getMaxAmplitude` (0..32767, the peak since it was last asked) as
 * a level, through the same decibel scale as iOS so a voice looks the same on both.
 */
fun levelFromAmplitude(amplitude: Int): Float {
    if (amplitude <= 0) return 0f
    val db = 20f * log10(amplitude.coerceAtMost(MAX_AMPLITUDE).toFloat() / MAX_AMPLITUDE)
    return levelFromDecibels(db)
}

/** Quieter than this reads as silence: room noise sits around it, speech well above. */
private const val FLOOR_DB = -50f
private const val MAX_AMPLITUDE = 32_767
