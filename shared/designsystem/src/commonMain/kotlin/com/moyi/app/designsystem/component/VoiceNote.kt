package com.moyi.app.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moyi.app.designsystem.icon.MoyiIcons
import com.moyi.app.designsystem.platform.PlaybackPhase
import com.moyi.app.designsystem.platform.ProtectFromCapture
import com.moyi.app.designsystem.platform.RecordedVoice
import com.moyi.app.designsystem.platform.VoicePlayback
import com.moyi.app.designsystem.platform.VoicePlayer
import com.moyi.app.designsystem.platform.VoiceRecording
import com.moyi.app.designsystem.platform.VoiceSource
import com.moyi.app.designsystem.platform.rememberVoicePlayer
import com.moyi.app.designsystem.resources.Res
import com.moyi.app.designsystem.resources.ds_compose_record_again
import com.moyi.app.designsystem.resources.ds_compose_recorded
import com.moyi.app.designsystem.resources.ds_compose_recording
import com.moyi.app.designsystem.resources.ds_compose_remove_voice
import com.moyi.app.designsystem.resources.ds_compose_stop_recording
import com.moyi.app.designsystem.resources.ds_entry_voice_not_shared
import com.moyi.app.designsystem.resources.ds_entry_voice_on_its_way
import com.moyi.app.designsystem.resources.ds_entry_voice_sensitive
import com.moyi.app.designsystem.resources.ds_voice_loading
import com.moyi.app.designsystem.resources.ds_voice_pause
import com.moyi.app.designsystem.resources.ds_voice_play
import com.moyi.app.designsystem.resources.ds_voice_progress
import com.moyi.app.designsystem.resources.ds_voice_unplayable
import com.moyi.app.designsystem.theme.MoyiDimens
import com.moyi.app.designsystem.MoyiTheme
import org.jetbrains.compose.resources.stringResource

/** How an [EntryCard]'s voice note is offered to this reader (from the server's `EntryVoiceResponse`). */
enum class VoiceNoteLook {
    /** The player. */
    Playable,

    /** Passed moderation as sensitive (Q16): the player, under a line saying so. Nothing plays before a tap anyway. */
    Sensitive,

    /** Not playable to this reader yet, for a reason never given: one muted line, no player. */
    OnItsWay,

    /** Its author's only: held back from the other member (Q10). The player if [VoiceNote.url] is set, and the line. */
    NotShared,
}

/**
 * A voice note on an [EntryCard]. [url] is a signed GET and so a credential: never printed,
 * and fetched only when play is tapped. [key] is the media id.
 */
@Immutable
data class VoiceNote(
    val key: String,
    val url: String?,
    val durationMs: Long?,
    val look: VoiceNoteLook,
) {
    override fun toString(): String = "VoiceNote(key=$key, url=${url?.let { "(redacted)" }}, durationMs=$durationMs, look=$look)"
}

/**
 * **Interim, not drawn** (docs/missing-components.md §43): the voice note inside an
 * EntryCard, under the words (and under the photo): a 48 play/pause target and the time
 * beside it, "0:12 of 0:45", in `inkMuted`. No waveform, no scrubber. While it is fetched
 * the line says so (no spinner). Leaving the screen, or the app, stops it.
 */
@Composable
fun VoiceNoteView(voice: VoiceNote, protectFromCapture: Boolean = false) {
    // R3, for a partner's voice note drawn outside an EntryCard (the Archive list's day card).
    if (protectFromCapture) ProtectFromCapture()
    when (voice.look) {
        VoiceNoteLook.OnItsWay -> MoyiText(text = stringResource(Res.string.ds_entry_voice_on_its_way), tone = TextTone.Muted)
        VoiceNoteLook.Playable -> VoicePlayerRow(voice)
        VoiceNoteLook.Sensitive -> Column {
            MoyiText(text = stringResource(Res.string.ds_entry_voice_sensitive), tone = TextTone.Muted)
            VoicePlayerRow(voice)
        }
        VoiceNoteLook.NotShared -> Column {
            if (voice.url != null) VoicePlayerRow(voice)
            MoyiText(text = stringResource(Res.string.ds_entry_voice_not_shared), tone = TextTone.Muted)
        }
    }
}

@Composable
private fun VoicePlayerRow(voice: VoiceNote) {
    val url = voice.url ?: return
    val player = rememberStoppingPlayer()
    val playback by player.playback.collectAsStateWithLifecycle()
    PlayerControls(
        playback = playback.withKnownDuration(voice.durationMs),
        onToggle = { player.toggle(VoiceSource.Remote(voice.key, url)) },
    )
}

/** A player that stops when the screen does: leaving it releases the player, backgrounding pauses it. */
@Composable
private fun rememberStoppingPlayer(): VoicePlayer {
    val player = rememberVoicePlayer()
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { player.pause() }
    return player
}

@Composable
private fun PlayerControls(playback: VoicePlayback, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            VoiceIconTarget(
                icon = if (playback.active) MoyiIcons.Pause else MoyiIcons.Play,
                description = stringResource(if (playback.active) Res.string.ds_voice_pause else Res.string.ds_voice_play),
                onClick = onToggle,
            )
            MoyiText(
                text = when (playback.phase) {
                    PlaybackPhase.Loading -> stringResource(Res.string.ds_voice_loading)
                    PlaybackPhase.Idle -> playback.durationMs?.let(::formatClock).orEmpty()
                    else -> stringResource(
                        Res.string.ds_voice_progress,
                        formatClock(playback.positionMs),
                        playback.durationMs?.let(::formatClock) ?: formatClock(playback.positionMs),
                    )
                },
                tone = TextTone.Muted,
            )
        }
        if (playback.phase == PlaybackPhase.Failed) {
            MoyiText(text = stringResource(Res.string.ds_voice_unplayable), tone = TextTone.Muted)
        }
    }
}

/** What the server said the length is, until the file says otherwise. */
private fun VoicePlayback.withKnownDuration(serverMs: Long?): VoicePlayback =
    if (durationMs == null && serverMs != null && serverMs > 0) copy(durationMs = serverMs) else this

/** Where the composer's voice note stands. */
sealed interface AttachedVoiceState {
    /** Recording: [elapsedMs] so far, out of [VoiceRecording.MAX_DURATION_MS]. */
    data class Recording(val elapsedMs: Long) : AttachedVoiceState

    /** Recorded and not sent yet: it can be listened back to, recorded again or removed. */
    data class Recorded(val voice: RecordedVoice) : AttachedVoiceState
}

/**
 * **Interim, not drawn** (docs/missing-components.md §44): the voice note being added,
 * inside the composer's box above its toolbar, beside or instead of the photo.
 *
 * - Recording: a 48 stop target and "Recording 0:12 of 1:00".
 * - Recorded: a 48 play/pause target to listen back (from memory, nothing leaves the
 *   phone), "Your voice note, 0:42", then Record again and Remove. [onRecordAgain] and
 *   [onRemove] are null while it is being sent.
 */
@Composable
fun AttachedVoice(
    state: AttachedVoiceState,
    onStop: () -> Unit,
    onRecordAgain: (() -> Unit)?,
    onRemove: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        when (state) {
            is AttachedVoiceState.Recording -> {
                VoiceIconTarget(MoyiIcons.Stop, stringResource(Res.string.ds_compose_stop_recording), onStop)
                MoyiText(
                    text = stringResource(
                        Res.string.ds_compose_recording,
                        formatClock(state.elapsedMs),
                        formatClock(VoiceRecording.MAX_DURATION_MS),
                    ),
                    tone = TextTone.Muted,
                )
            }
            is AttachedVoiceState.Recorded -> {
                val player = rememberStoppingPlayer()
                val playback by player.playback.collectAsStateWithLifecycle()
                VoiceIconTarget(
                    icon = if (playback.active) MoyiIcons.Pause else MoyiIcons.Play,
                    description = stringResource(if (playback.active) Res.string.ds_voice_pause else Res.string.ds_voice_play),
                    onClick = { player.toggle(VoiceSource.Local("listen-back-${state.voice.hashCode()}", state.voice)) },
                )
                MoyiText(
                    text = if (playback.phase == PlaybackPhase.Playing || playback.phase == PlaybackPhase.Paused) {
                        stringResource(Res.string.ds_voice_progress, formatClock(playback.positionMs), formatClock(state.voice.durationMs))
                    } else {
                        stringResource(Res.string.ds_compose_recorded, formatClock(state.voice.durationMs))
                    },
                    tone = TextTone.Muted,
                    modifier = Modifier.weight(1f),
                )
                if (onRecordAgain != null) {
                    VoiceIconTarget(MoyiIcons.RecordAgain, stringResource(Res.string.ds_compose_record_again), onRecordAgain)
                }
                if (onRemove != null) {
                    VoiceIconTarget(MoyiIcons.Close, stringResource(Res.string.ds_compose_remove_voice), onRemove)
                }
            }
        }
    }
}

/** The composer's icon target (ComposeField's): a 48 frame around a 24 glyph, tinted `ink`. */
@Composable
private fun VoiceIconTarget(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(MoyiDimens.ComposeField.IconTarget)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = MoyiTheme.colors.ink,
            modifier = Modifier.size(MoyiDimens.IconGlyph),
        )
    }
}

/** "0:07", "1:00": minutes and seconds, the seconds rounded down so the clock never runs ahead. */
fun formatClock(ms: Long): String {
    val seconds = (ms.coerceAtLeast(0) + ROUNDING_MS) / MS_PER_SECOND
    return "${seconds / SECONDS_PER_MINUTE}:${(seconds % SECONDS_PER_MINUTE).toString().padStart(2, '0')}"
}

/**
 * A recorder stopped at 60 s reports 59.98 s or 60.02 s: within this of the next whole
 * second counts as it, so the full minute reads "1:00", not "0:59".
 */
private const val ROUNDING_MS = 50L
private const val MS_PER_SECOND = 1000L
private const val SECONDS_PER_MINUTE = 60L
