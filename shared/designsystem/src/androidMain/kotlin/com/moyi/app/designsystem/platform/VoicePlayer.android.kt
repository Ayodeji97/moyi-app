package com.moyi.app.designsystem.platform

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import kotlinx.coroutines.CoroutineScope

@Composable
actual fun rememberVoicePlayer(): VoicePlayer {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val player = remember(context) { AndroidVoicePlayer(context, scope) }
    DisposableEffect(player) { onDispose { player.release() } }
    VolumeKeysControlMedia()
    return player
}

/**
 * The stream each activity's volume keys controlled before the first voice note came on
 * screen, put back when the last one leaves.
 */
private val streamBefore = HashMap<Activity, Int>()

/**
 * One hold per activity: an Archive list can show several voice notes at once, and the
 * keys go back only when the last of them leaves. Saving on the first and restoring on
 * the last (rather than each effect saving and restoring its own) keeps two overlapping
 * players from restoring each other's value out of order.
 */
private val mediaVolumeHolds = KeyedHolds<Activity>(
    onFirst = { activity ->
        streamBefore[activity] = activity.volumeControlStream
        activity.volumeControlStream = AudioManager.STREAM_MUSIC
    },
    onLast = { activity ->
        activity.volumeControlStream = streamBefore.remove(activity) ?: AudioManager.USE_DEFAULT_STREAM_TYPE
    },
)

/**
 * While a voice note is on screen, the hardware volume keys set the media volume, the
 * stream the player uses (`USAGE_MEDIA`). Without this they set the media volume only
 * while audio plays and the ringer before that, so someone turning a voice note down
 * before pressing play turns their ringer down instead.
 */
@Composable
private fun VolumeKeysControlMedia() {
    // A preview or a test host has no activity: no keys to route.
    val activity = LocalContext.current.findActivity() ?: return
    DisposableEffect(activity) {
        mediaVolumeHolds.acquire(activity)
        onDispose { mediaVolumeHolds.release(activity) }
    }
}

/**
 * media3 `ExoPlayer`, made on the first tap on play and never before (nothing is
 * preloaded). A remote voice note streams from its signed URL over plain HTTP(S) with no
 * cache (`DefaultHttpDataSource`, no `CacheDataSource`), so nothing of it is written to
 * disk. A local one plays from the bytes in memory. ExoPlayer holds audio focus while it
 * plays and pauses when it is taken (a call) or when headphones are unplugged.
 */
@OptIn(UnstableApi::class)
private class AndroidVoicePlayer(
    private val context: Context,
    scope: CoroutineScope,
) : BaseVoicePlayer(scope) {
    private var exo: ExoPlayer? = null

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            val player = exo ?: return
            when (playbackState) {
                Player.STATE_READY -> if (player.playWhenReady) onReady(player.duration.takeIf { it != C.TIME_UNSET && it > 0 })
                Player.STATE_ENDED -> {
                    player.pause()
                    player.seekTo(0)
                    onEnded()
                }
                else -> Unit
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!playWhenReady && reason != Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) onPausedBySystem()
        }

        override fun onPlayerError(error: PlaybackException) {
            // The error is not logged: its message can carry the signed URL.
            onFailed()
        }
    }

    override fun open(source: VoiceSource) {
        val player = exo ?: ExoPlayer.Builder(context)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
            .also {
                it.addListener(listener)
                exo = it
            }
        player.setMediaSource(mediaSource(source))
        player.prepare()
        player.playWhenReady = true
    }

    override fun resume() {
        exo?.play()
    }

    override fun pauseNative() {
        exo?.pause()
    }

    override fun releaseNative() {
        exo?.run {
            removeListener(listener)
            release()
        }
        exo = null
    }

    override fun positionMs(): Long = exo?.currentPosition ?: 0

    override fun seekNative(positionMs: Long) {
        exo?.seekTo(positionMs)
    }

    private fun mediaSource(source: VoiceSource): MediaSource = when (source) {
        is VoiceSource.Remote -> ProgressiveMediaSource.Factory(DefaultHttpDataSource.Factory())
            .createMediaSource(MediaItem.fromUri(source.url))
        is VoiceSource.Local -> {
            val bytes = source.voice.bytes
            ProgressiveMediaSource.Factory(DataSource.Factory { ByteArrayDataSource(bytes) })
                .createMediaSource(MediaItem.fromUri(Uri.parse(LOCAL_URI)))
        }
    }

    private companion object {
        /** A name for the bytes in memory; the data source never reads it. */
        const val LOCAL_URI = "memory://voice-note"
    }
}
