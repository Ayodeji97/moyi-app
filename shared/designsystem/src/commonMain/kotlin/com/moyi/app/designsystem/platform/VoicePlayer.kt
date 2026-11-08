package com.moyi.app.designsystem.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** What a voice player plays. */
sealed interface VoiceSource {
    /** Identifies the voice note: a player keeps its place only while it plays the same one. */
    val key: String

    /**
     * An entry's voice note: a signed GET of an Ogg Opus file. The URL is a credential, never
     * printed, and fetched only when play is tapped (no preload, no disk cache).
     */
    class Remote(override val key: String, val url: String) : VoiceSource {
        override fun toString(): String = "Remote(key=$key, url=(redacted))"
    }

    /** A recording not sent yet, listened back to in the composer: AAC in MPEG-4, in memory. */
    class Local(override val key: String, val voice: RecordedVoice) : VoiceSource {
        override fun toString(): String = "Local(key=$key, $voice)"
    }
}

enum class PlaybackPhase {
    /** Not started, or played to the end (back at the start). */
    Idle,

    /** Play was tapped and the voice note is being fetched or opened. */
    Loading,
    Playing,
    Paused,

    /** It could not be fetched or played. Tapping play tries again. */
    Failed,
}

/**
 * Where one player stands, for the control to draw. Every change is one of the named
 * transitions below, so the platform players differ only in what reports them.
 */
@Immutable
data class VoicePlayback(
    val phase: PlaybackPhase = PlaybackPhase.Idle,
    val positionMs: Long = 0,
    /** Null until known: from the server's `durationMs`, then from the file itself. */
    val durationMs: Long? = null,
) {
    /** The control shows Pause: playing, or about to. */
    val active: Boolean get() = phase == PlaybackPhase.Playing || phase == PlaybackPhase.Loading

    fun loading(): VoicePlayback = copy(phase = PlaybackPhase.Loading)

    /** Playing; [durationMs] from the file replaces what was known, when the file knows it. */
    fun playing(durationMs: Long?): VoicePlayback =
        copy(phase = PlaybackPhase.Playing, durationMs = durationMs?.takeIf { it > 0 } ?: this.durationMs)

    fun at(positionMs: Long): VoicePlayback {
        val end = durationMs ?: Long.MAX_VALUE
        return copy(positionMs = positionMs.coerceIn(0, end))
    }

    fun paused(): VoicePlayback = if (phase == PlaybackPhase.Idle || phase == PlaybackPhase.Failed) this else copy(phase = PlaybackPhase.Paused)

    /** Played to the end: back to the start, ready to play again. */
    fun ended(): VoicePlayback = VoicePlayback(durationMs = durationMs)

    fun failed(): VoicePlayback = copy(phase = PlaybackPhase.Failed, positionMs = 0)
}

/** One voice note's player. Only one plays at a time, app-wide: starting one pauses any other. */
interface VoicePlayer {
    val playback: StateFlow<VoicePlayback>

    /** Pause when playing; otherwise play [source] (resuming where it paused, if it is the same one). */
    fun toggle(source: VoiceSource)

    fun pause()

    /** Stops and lets go of everything: the platform player, the audio session, the bytes. */
    fun release()
}

/**
 * A player for one voice note (expect/actual), released when it leaves the composition:
 * leaving the screen stops playback.
 *
 * - **Android**: media3 `ExoPlayer`, which streams the Ogg Opus file from the signed URL
 *   as it plays (no cache), decodes Opus with the platform's codec, and holds audio focus
 *   (a call pauses it; unplugging headphones pauses it).
 * - **iOS**: AVFoundation cannot open Ogg, so the file (at most about 190 KB) is fetched
 *   into memory through an ephemeral `URLSession` (no disk cache), its Opus packets are
 *   moved into a CAF container in memory ([OggOpus.toCaf]), and `AVAudioPlayer` plays that.
 *   An interruption (a call) pauses it.
 */
@Composable
expect fun rememberVoicePlayer(): VoicePlayer

/**
 * The state, the one-at-a-time rule and the position ticker, shared by both platforms.
 * A subclass opens, resumes, pauses and releases its own player, and reports back through
 * [onReady], [onEnded] and [onFailed]; it is driven on the main thread only.
 */
abstract class BaseVoicePlayer(private val scope: CoroutineScope) : VoicePlayer {
    private val state = MutableStateFlow(VoicePlayback())
    override val playback: StateFlow<VoicePlayback> = state.asStateFlow()

    /** The voice note open in the platform player, if any. */
    private var openKey: String? = null
    private var ticker: Job? = null

    /** Opens [source] and starts it; reports [onReady] once it plays, or [onFailed]. */
    protected abstract fun open(source: VoiceSource)

    /** Plays again from where it paused, or from the start after it ended (the same source is open). */
    protected abstract fun resume()

    protected abstract fun pauseNative()

    protected abstract fun releaseNative()

    protected abstract fun positionMs(): Long

    override fun toggle(source: VoiceSource) {
        if (state.value.active) {
            pause()
            return
        }
        ActiveVoicePlayer.claim(this)
        // Still open (paused, or played to the end and rewound): play it again without fetching it again.
        if (source.key == openKey && (state.value.phase == PlaybackPhase.Paused || state.value.phase == PlaybackPhase.Idle)) {
            resume()
            state.update { it.playing(null) }
            tick()
        } else {
            openKey = source.key
            state.update { it.loading().at(0) }
            open(source)
        }
    }

    override fun pause() {
        if (!state.value.active) return
        ticker?.cancel()
        if (state.value.phase == PlaybackPhase.Loading) {
            // Not playing yet: drop what is being opened, so it does not start after the pause.
            openKey = null
            releaseNative()
            state.update { it.copy(phase = PlaybackPhase.Idle) }
        } else {
            pauseNative()
            state.update { it.at(positionMs()).paused() }
        }
    }

    override fun release() {
        ticker?.cancel()
        openKey = null
        ActiveVoicePlayer.letGo(this)
        releaseNative()
        state.update { it.ended() }
    }

    /** The platform player has started playing; [durationMs] as the file says, if it does. */
    protected fun onReady(durationMs: Long?) {
        state.update { it.playing(durationMs) }
        tick()
    }

    /**
     * Played to the end. The platform player stays open, rewound to the start by the
     * subclass, so playing it again needs no second download.
     */
    protected fun onEnded() {
        ticker?.cancel()
        state.update { it.ended() }
    }

    /** Paused by the system (a call, focus taken, headphones unplugged). */
    protected fun onPausedBySystem() {
        ticker?.cancel()
        state.update { it.at(positionMs()).paused() }
    }

    protected fun onFailed() {
        ticker?.cancel()
        openKey = null
        releaseNative()
        state.update { it.failed() }
    }

    private fun tick() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                state.update { it.at(positionMs()) }
                delay(TICK_MS)
            }
        }
    }

    private companion object {
        /** Often enough for a seconds clock to turn on time. */
        const val TICK_MS = 200L
    }
}

/** The one player allowed to play: starting another pauses it. Main thread only. */
internal object ActiveVoicePlayer {
    private var current: VoicePlayer? = null

    fun claim(player: VoicePlayer) {
        val previous = current
        current = player
        if (previous != null && previous !== player) previous.pause()
    }

    fun letGo(player: VoicePlayer) {
        if (current === player) current = null
    }
}
