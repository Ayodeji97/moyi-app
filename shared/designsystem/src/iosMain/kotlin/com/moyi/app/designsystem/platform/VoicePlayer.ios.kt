@file:OptIn(ExperimentalForeignApi::class)

package com.moyi.app.designsystem.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioPlayerDelegateProtocol
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVAudioSessionInterruptionNotification
import platform.AVFAudio.AVAudioSessionInterruptionTypeBegan
import platform.AVFAudio.AVAudioSessionInterruptionTypeKey
import platform.AVFAudio.AVAudioSessionModeSpokenAudio
import platform.AVFAudio.setActive
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSNotification
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNumber
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.Foundation.NSURLRequestReloadIgnoringLocalCacheData
import platform.Foundation.NSURLResponse
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.dataTaskWithRequest
import platform.darwin.NSObject
import platform.darwin.NSObjectProtocol
import kotlin.coroutines.resume

@Composable
actual fun rememberVoicePlayer(): VoicePlayer {
    val scope = rememberCoroutineScope()
    val player = remember { IosVoicePlayer(scope) }
    DisposableEffect(player) { onDispose { player.release() } }
    return player
}

/**
 * `AVAudioPlayer` from memory. A remote voice note is fetched whole at the tap through an
 * ephemeral `URLSession` (no disk cache, no cookies) and played from the bytes: its AAC copy
 * (MP4, `urlAac`) as it is, as AVFoundation opens it natively; an old note without one as Ogg
 * Opus, moved into CAF first ([OggOpus.toCaf], since AVFoundation does not open Ogg). A local
 * recording (AAC in MPEG-4) plays as it is. Nothing is written to disk.
 */
private class IosVoicePlayer(private val scope: CoroutineScope) : BaseVoicePlayer(scope) {
    private var player: AVAudioPlayer? = null
    private var fetching: Job? = null
    private var interruptions: NSObjectProtocol? = null

    /** Held strongly: `AVAudioPlayer.delegate` is weak. */
    private val delegate = object : NSObject(), AVAudioPlayerDelegateProtocol {
        override fun audioPlayerDidFinishPlaying(player: AVAudioPlayer, successfully: Boolean) {
            // AVAudioPlayer rewinds to the start itself.
            letGoOfSession()
            onEnded()
        }
    }

    override fun open(source: VoiceSource) {
        fetching?.cancel()
        fetching = scope.launch {
            val made = when (source) {
                is VoiceSource.Local -> player(source.voice.bytes)
                is VoiceSource.Remote -> remotePlayer(source)
            }
            if (made == null || !startSession()) {
                onFailed()
                return@launch
            }
            made.delegate = delegate
            // enableRate must be set before prepareToPlay, or rate is ignored. AVAudioPlayer's
            // rate keeps the pitch (time-stretching), so 1.5x and 2x still sound like the voice.
            made.enableRate = true
            made.prepareToPlay()
            made.rate = speedFactor
            player = made
            if (made.play()) onReady((made.duration * MS_PER_SECOND).toLong()) else onFailed()
        }
    }

    /**
     * The first of [preferredUrls] that fetches and opens: the AAC copy plays as it is, the Ogg
     * Opus one is moved into CAF first. A note without an AAC copy, or one whose copy fails,
     * falls back to Ogg.
     */
    private suspend fun remotePlayer(source: VoiceSource.Remote): AVAudioPlayer? {
        for (url in preferredUrls(source, nativeAac = true)) {
            val bytes = fetch(url) ?: continue
            val playable = if (url == source.urlAac) bytes else withContext(Dispatchers.Default) { OggOpus.toCaf(bytes) }
            player(playable)?.let { return it }
        }
        return null
    }

    private fun player(bytes: ByteArray?): AVAudioPlayer? =
        bytes?.let { failable { AVAudioPlayer(data = it.toNSData(), error = null) } }

    override fun resume() {
        val made = player ?: return
        if (startSession()) made.play()
    }

    override fun pauseNative() {
        player?.pause()
    }

    override fun releaseNative() {
        fetching?.cancel()
        fetching = null
        player?.let {
            it.stop()
            it.delegate = null
        }
        player = null
        letGoOfSession()
    }

    override fun positionMs(): Long = player?.let { (it.currentTime * MS_PER_SECOND).toLong() } ?: 0

    /** `currentTime` moves a playing or paused AVAudioPlayer; a paused one stays paused. */
    override fun seekNative(positionMs: Long) {
        player?.currentTime = positionMs / MS_PER_SECOND
    }

    /** `rate` changes a playing AVAudioPlayer at once, from where it is. */
    override fun setSpeedNative(factor: Float) {
        player?.rate = factor
    }

    /** The whole file, in memory, or null. The URL is a credential: it is never logged. */
    private suspend fun fetch(url: String): ByteArray? {
        val address = NSURL.URLWithString(url) ?: return null
        val request = NSURLRequest.requestWithURL(address, NSURLRequestReloadIgnoringLocalCacheData, FETCH_TIMEOUT_S)
        return suspendCancellableCoroutine { continuation ->
            val task = session.dataTaskWithRequest(request) { data: NSData?, response: NSURLResponse?, error: NSError? ->
                val ok = error == null && (response as? NSHTTPURLResponse)?.statusCode?.let { it in HTTP_OK } == true
                val bytes = if (ok) data?.copyBytes() else null
                NSOperationQueue.mainQueue.addOperationWithBlock { if (continuation.isActive) continuation.resume(bytes) }
            }
            continuation.invokeOnCancellation { task.cancel() }
            task.resume()
        }
    }

    /** Playback, spoken audio: it plays with the ring switch on silent, as a voice message does. */
    private fun startSession(): Boolean {
        val audio = AVAudioSession.sharedInstance()
        AudioSessionOwner.shared.take(this)
        val ok = audio.setCategory(AVAudioSessionCategoryPlayback, mode = AVAudioSessionModeSpokenAudio, options = 0u, error = null) &&
            audio.setActive(true, error = null)
        if (ok && interruptions == null) {
            // A call pauses AVAudioPlayer; the control is told, so it shows Play.
            interruptions = NSNotificationCenter.defaultCenter.addObserverForName(
                name = AVAudioSessionInterruptionNotification,
                `object` = audio,
                queue = NSOperationQueue.mainQueue,
            ) { note: NSNotification? ->
                val type = (note?.userInfo?.get(AVAudioSessionInterruptionTypeKey) as? NSNumber)?.unsignedLongValue
                if (type == AVAudioSessionInterruptionTypeBegan) {
                    player?.pause()
                    onPausedBySystem()
                }
            }
        }
        return ok
    }

    private fun letGoOfSession() {
        interruptions?.let(NSNotificationCenter.defaultCenter::removeObserver)
        interruptions = null
        // Only if it is still the player's: Record again starts the recorder before this player is let go.
        AudioSessionOwner.shared.letGo(this)
    }

    private companion object {
        const val MS_PER_SECOND = 1000.0
        const val FETCH_TIMEOUT_S = 30.0
        val HTTP_OK = 200L..299L

        /** Ephemeral: no disk cache, no cookie store, no credential store. */
        val session: NSURLSession by lazy {
            NSURLSession.sessionWithConfiguration(
                NSURLSessionConfiguration.ephemeralSessionConfiguration.apply { URLCache = null },
            )
        }
    }
}
