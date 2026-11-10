package com.moyi.app.designsystem.component

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.material3.Text
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.Constraints
import com.moyi.app.designsystem.platform.PlaybackSpeed
import com.moyi.app.designsystem.resources.ds_voice_speed
import com.moyi.app.designsystem.resources.ds_voice_speed_1
import com.moyi.app.designsystem.resources.ds_voice_speed_1_5
import com.moyi.app.designsystem.resources.ds_voice_speed_2
import com.moyi.app.designsystem.resources.ds_voice_speed_spoken
import com.moyi.app.designsystem.resources.ds_voice_speed_spoken_normal
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moyi.app.designsystem.icon.MoyiIcons
import com.moyi.app.designsystem.platform.PlaybackPhase
import com.moyi.app.designsystem.platform.ProtectFromCapture
import com.moyi.app.designsystem.platform.RecordedVoice
import com.moyi.app.designsystem.platform.RecordingLevels
import com.moyi.app.designsystem.platform.VoicePlayback
import com.moyi.app.designsystem.platform.VoicePlayer
import com.moyi.app.designsystem.platform.VoiceRecording
import com.moyi.app.designsystem.platform.VoiceSource
import com.moyi.app.designsystem.platform.rememberReduceMotion
import com.moyi.app.designsystem.platform.rememberVoicePlayer
import com.moyi.app.designsystem.resources.Res
import com.moyi.app.designsystem.resources.ds_compose_record_again
import com.moyi.app.designsystem.resources.ds_compose_recorded
import com.moyi.app.designsystem.resources.ds_compose_recording
import com.moyi.app.designsystem.resources.ds_compose_keep_sent_voice
import com.moyi.app.designsystem.resources.ds_compose_remove_voice
import com.moyi.app.designsystem.resources.ds_compose_sent_voice
import com.moyi.app.designsystem.resources.ds_compose_discard_recording
import com.moyi.app.designsystem.resources.ds_compose_keep_recording
import com.moyi.app.designsystem.resources.ds_entry_voice_not_shared
import com.moyi.app.designsystem.resources.ds_entry_voice_on_its_way
import com.moyi.app.designsystem.resources.ds_entry_voice_sensitive
import com.moyi.app.designsystem.resources.ds_voice_loading
import com.moyi.app.designsystem.resources.ds_voice_pause
import com.moyi.app.designsystem.resources.ds_voice_play
import com.moyi.app.designsystem.resources.ds_voice_position
import com.moyi.app.designsystem.resources.ds_voice_progress
import com.moyi.app.designsystem.resources.ds_voice_unplayable
import com.moyi.app.designsystem.theme.MoyiDimens
import com.moyi.app.designsystem.theme.MoyiMotion
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
 * **Interim, not drawn** (docs/missing-components.md §43, the waveform §46): the voice
 * note inside an EntryCard, under the words (and under the photo). A 48 play/pause target,
 * the [VoiceWaveform] beside it (tap or drag to move through it), and under the bars the
 * clock in `inkMuted`, how far it has played of its length: "0:12 of 0:42" ("0:00 of 0:42"
 * before it plays; owner ruling 2026-11-09). While it is fetched a line under it says so (no spinner). Leaving the screen, or
 * the app, stops it.
 *
 * The server sends no levels for a voice note (docs/backend-contract-gaps.md row 31), so
 * the bars are [WaveformMath.placeholder], a stand-in shape seeded by the media id: honest
 * about position and length, not about loudness.
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
    val speed by player.speed.collectAsStateWithLifecycle()
    val levels = remember(voice.key) { WaveformMath.placeholder(voice.key) }
    PlayerControls(
        playback = playback.withKnownDuration(voice.durationMs),
        levels = levels,
        label = stringResource(Res.string.ds_voice_position),
        onToggle = { player.toggle(VoiceSource.Remote(voice.key, url)) },
        onSeek = player::seekTo,
        speed = speed,
        onCycleSpeed = player::cycleSpeed,
    )
}

/** A player that stops when the screen does: leaving it releases the player, backgrounding pauses it. */
@Composable
private fun rememberStoppingPlayer(): VoicePlayer {
    val player = rememberVoicePlayer()
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { player.pause() }
    return player
}

/**
 * Play or pause, the waveform with its clock ("0:12 of 0:42") under it and the [SpeedPill]
 * at the clock's end; then, at the end of the row, [trailing] (the composer's Record again
 * and Remove). The lines for loading and failure go under it.
 */
@Composable
private fun PlayerControls(
    playback: VoicePlayback,
    levels: List<Float>,
    label: String,
    onToggle: () -> Unit,
    onSeek: (Long) -> Unit,
    speed: PlaybackSpeed,
    onCycleSpeed: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val duration = playback.durationMs
    val clock = voiceClock(playback)
    Column(modifier) {
        // Top-aligned: the play target, the waveform's band and the trailing targets are all
        // 48 and share a centre line; the clock sits under the bars, as messaging apps put it.
        Row(verticalAlignment = Alignment.Top) {
            VoiceIconTarget(
                icon = if (playback.active) MoyiIcons.Pause else MoyiIcons.Play,
                description = stringResource(if (playback.active) Res.string.ds_voice_pause else Res.string.ds_voice_play),
                onClick = onToggle,
            )
            Column(Modifier.weight(1f)) {
                VoiceWaveform(
                    levels = levels,
                    progress = { WaveformMath.fractionOf(playback.positionMs, duration) },
                    label = label,
                    position = clock,
                    // Seeking needs the length; the server sends it with every playable voice note.
                    onSeek = duration?.let { length -> { fraction: Float -> onSeek(WaveformMath.positionAt(fraction, length)) } },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // "0:12 of 0:42". It repeats the waveform's position, which a screen reader already hears.
                    MoyiText(text = clock, tone = TextTone.Muted, modifier = Modifier.weight(1f).clearAndSetSemantics {})
                    SpeedPill(speed = speed, onClick = onCycleSpeed)
                }
            }
            trailing()
        }
        when (playback.phase) {
            PlaybackPhase.Loading -> MoyiText(text = stringResource(Res.string.ds_voice_loading), tone = TextTone.Muted)
            PlaybackPhase.Failed -> MoyiText(text = stringResource(Res.string.ds_voice_unplayable), tone = TextTone.Muted)
            else -> Unit
        }
    }
}

/**
 * "0:12 of 0:42": how far it has played, of its length (owner ruling 2026-11-09). Before the
 * first play it reads "0:00 of 0:42" (or where a seek put it); copy.md and states.md name no
 * shorter form. Only the position while the length is not known yet.
 */
@Composable
private fun voiceClock(playback: VoicePlayback): String {
    val duration = playback.durationMs ?: return formatClock(playback.positionMs)
    return stringResource(Res.string.ds_voice_progress, formatClock(playback.positionMs), formatClock(duration))
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
 * **Interim, not drawn** (docs/missing-components.md §44, the waveforms §46 and §47): the
 * voice note being added, inside the composer's box above its toolbar, beside or instead
 * of the photo.
 *
 * - Recording (owner ruling 2026-11-09, §49, laid out as messaging apps do): a 48 trash
 *   target that throws the recording away at once ([onDiscard]), the [LiveWaveform] of the
 *   microphone's level ([recordingLevels], read only when it is drawn; it moves from the
 *   first frame, before any sound), a `danger` [RecordingDot] with "0:12 of 1:00", then a
 *   48 tick target that stops and keeps it ([onStop]). With Reduce Motion the waveform
 *   does not scroll (it fills the minute in place) and the dot does not pulse.
 * - Recorded: a 48 play/pause target to listen back (from memory, nothing leaves the
 *   phone), the [VoiceWaveform] of what was actually recorded (seekable), the clock, then
 *   Record again and Remove. [onRecordAgain] and [onRemove] are null while it is being sent.
 */
@Composable
fun AttachedVoice(
    state: AttachedVoiceState,
    onStop: () -> Unit,
    onDiscard: () -> Unit,
    onRecordAgain: (() -> Unit)?,
    onRemove: (() -> Unit)?,
    modifier: Modifier = Modifier,
    recordingLevels: () -> List<Float> = { emptyList() },
    /** In Edit, where removing this new one puts the entry's own back: [onRemove] is named "Keep the voice note you had" (§60). */
    removeRestoresSent: Boolean = false,
) {
    when (state) {
        is AttachedVoiceState.Recording -> Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
            val reduceMotion = rememberReduceMotion()
            // Start: throw it away, at once (an unsent recording, states.md §4: no confirmation).
            VoiceIconTarget(MoyiIcons.Trash, stringResource(Res.string.ds_compose_discard_recording), onDiscard)
            LiveWaveform(
                levels = recordingLevels,
                scrolling = !reduceMotion,
                capacity = (VoiceRecording.MAX_DURATION_MS / RecordingLevels.TICK_MS).toInt(),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(MoyiDimens.Waveform.Gap))
            val spoken = stringResource(
                Res.string.ds_compose_recording,
                formatClock(state.elapsedMs),
                formatClock(VoiceRecording.MAX_DURATION_MS),
            )
            // The dot and the clock are one thing to a screen reader: "Recording 0:12 of 1:00".
            // The bars are not read.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clearAndSetSemantics { contentDescription = spoken },
            ) {
                RecordingDot(pulsing = !reduceMotion)
                Spacer(Modifier.width(MoyiTheme.spacing.xs))
                MoyiText(
                    text = stringResource(
                        Res.string.ds_voice_progress,
                        formatClock(state.elapsedMs),
                        formatClock(VoiceRecording.MAX_DURATION_MS),
                    ),
                    tone = TextTone.Muted,
                )
            }
            // End: stop and keep it to listen back to. A tick, not a send arrow: Send still sends the entry.
            VoiceIconTarget(MoyiIcons.Check, stringResource(Res.string.ds_compose_keep_recording), onStop)
        }
        is AttachedVoiceState.Recorded -> {
            val player = rememberStoppingPlayer()
            val playback by player.playback.collectAsStateWithLifecycle()
            val speed by player.speed.collectAsStateWithLifecycle()
            val voice = state.voice
            val key = "listen-back-${voice.hashCode()}"
            val levels = remember(voice) { voice.levels.ifEmpty { WaveformMath.placeholder(key) } }
            PlayerControls(
                playback = if (playback.durationMs == null) playback.copy(durationMs = voice.durationMs) else playback,
                levels = levels,
                label = stringResource(Res.string.ds_compose_recorded, formatClock(voice.durationMs)),
                onToggle = { player.toggle(VoiceSource.Local(key, voice)) },
                onSeek = player::seekTo,
                speed = speed,
                onCycleSpeed = player::cycleSpeed,
                modifier = modifier,
            ) {
                if (onRecordAgain != null) {
                    VoiceIconTarget(MoyiIcons.RecordAgain, stringResource(Res.string.ds_compose_record_again), onRecordAgain)
                }
                if (onRemove != null) {
                    VoiceIconTarget(
                        MoyiIcons.Close,
                        stringResource(if (removeRestoresSent) Res.string.ds_compose_keep_sent_voice else Res.string.ds_compose_remove_voice),
                        onRemove,
                    )
                }
            }
        }
    }
}

/**
 * **PROPOSED, not drawn** (docs/missing-components.md §60): in Edit, the voice note already
 * on the entry, laid out as a recorded one ([AttachedVoice]): play it from the server, the
 * waveform and its clock, then Record again ([onRecordAgain]; null while saving), which
 * records a new one to go in its place. It has **no remove**: the contract cannot take a
 * voice note off an entry (docs/backend-contract-gaps.md row 41). One the server has not
 * finished with (no [VoiceNote.url]) is the card's muted line, with Record again beside it.
 */
@Composable
fun SentVoice(voice: VoiceNote, onRecordAgain: (() -> Unit)?, modifier: Modifier = Modifier) {
    val again: @Composable RowScope.() -> Unit = {
        if (onRecordAgain != null) {
            VoiceIconTarget(MoyiIcons.RecordAgain, stringResource(Res.string.ds_compose_record_again), onRecordAgain)
        }
    }
    val url = voice.url
    if (url == null) {
        Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
            MoyiText(
                text = stringResource(Res.string.ds_entry_voice_on_its_way),
                tone = TextTone.Muted,
                modifier = Modifier.weight(1f).padding(MoyiDimens.ComposeField.Wrap),
            )
            again()
        }
        return
    }
    val player = rememberStoppingPlayer()
    val playback by player.playback.collectAsStateWithLifecycle()
    val speed by player.speed.collectAsStateWithLifecycle()
    val levels = remember(voice.key) { WaveformMath.placeholder(voice.key) }
    PlayerControls(
        playback = playback.withKnownDuration(voice.durationMs),
        levels = levels,
        label = stringResource(Res.string.ds_compose_sent_voice),
        onToggle = { player.toggle(VoiceSource.Remote(voice.key, url)) },
        onSeek = player::seekTo,
        speed = speed,
        onCycleSpeed = player::cycleSpeed,
        modifier = modifier,
        trailing = again,
    )
}

/**
 * **PROPOSED, not drawn** (docs/missing-components.md §64, owner request 2026-11-10): how
 * fast the voice note plays, "1×", "1.5×" or "2×", at the end of its clock line, on every
 * voice note and before play too. A tap moves it on ([PlaybackSpeed.next]); the speed is the
 * session's, so every pill shows the same one.
 *
 * Built from Rosewood tokens only, matched to its neighbours: the `label` style (the clock's
 * small companion, not a second clock) in `ink`, inside a `shapes.full` pill with a
 * `hairline` of `outlineStrong` (a control's border, as [Chip]'s, never the decorative
 * `outline`), no fill, padded `sm` across and `xs` down. The pill keeps the clock line's
 * height; its 48 target ([MoyiDimens.SpeedPill.Target], NFR-101) reaches past it above and
 * below, so it is drawn over the waveform band's empty bottom edge and the line under it
 * without moving either.
 *
 * Spoken "Playback speed, 1.5 times" ("Playback speed, normal" at 1x), a button.
 */
@Composable
private fun SpeedPill(speed: PlaybackSpeed, onClick: () -> Unit) {
    val colors = MoyiTheme.colors
    val pill = MoyiTheme.shapes.full
    val value = stringResource(
        when (speed) {
            PlaybackSpeed.Normal -> Res.string.ds_voice_speed_1
            PlaybackSpeed.OneAndAHalf -> Res.string.ds_voice_speed_1_5
            PlaybackSpeed.Twice -> Res.string.ds_voice_speed_2
        },
    )
    val spoken = if (speed == PlaybackSpeed.Normal) {
        stringResource(Res.string.ds_voice_speed_spoken_normal)
    } else {
        stringResource(Res.string.ds_voice_speed_spoken, value)
    }
    val targetPx = with(LocalDensity.current) { MoyiDimens.SpeedPill.Target.roundToPx() }
    Layout(
        content = {
            // The visible pill: drawn, never spoken (the target below says it).
            Text(
                text = stringResource(Res.string.ds_voice_speed, value),
                style = MoyiTheme.type.label,
                color = colors.ink,
                maxLines = 1,
                modifier = Modifier
                    .semantics { hideFromAccessibility() }
                    .border(MoyiTheme.shapes.hairline, colors.outlineStrong, pill)
                    .padding(horizontal = MoyiTheme.spacing.sm, vertical = MoyiTheme.spacing.xs),
            )
            // The 48 target: nothing drawn, it takes the tap and is what a screen reader finds.
            Box(
                Modifier
                    .clickable(role = Role.Button, onClick = onClick)
                    .clearAndSetSemantics {
                        contentDescription = spoken
                        role = Role.Button
                        onClick { onClick(); true }
                    },
            )
        },
    ) { measurables, constraints ->
        val visible = measurables[0].measure(constraints.copy(minWidth = 0, minHeight = 0))
        val width = maxOf(visible.width, targetPx)
        val height = maxOf(visible.height, targetPx)
        val target = measurables[1].measure(Constraints.fixed(width, height))
        // As tall as the pill, so the clock line keeps its height; as wide as the target.
        layout(width, visible.height) {
            visible.place((width - visible.width) / 2, 0)
            // Centred on the pill, reaching above and below it: Compose hit-tests a child
            // outside its parent's bounds, so the whole 48 takes the tap.
            target.place(0, (visible.height - height) / 2)
        }
    }
}

/**
 * **PROPOSED** (docs/missing-components.md §49, owner ruling 2026-11-09): the mark that the
 * microphone is open, a dot in `danger` before the recording clock. It breathes on
 * `motion.breathe` (900 ms each way, alpha 1.0 to 0.55, tokens.md §3a), animated in the
 * draw phase so nothing recomposes; with Reduce Motion it holds still. Its meaning is
 * spoken by the clock it sits in ("Recording 0:12 of 1:00"), so it has no name of its own.
 */
@Composable
private fun RecordingDot(pulsing: Boolean) {
    val color = MoyiTheme.colors.danger
    val alpha = if (pulsing) {
        val breath by rememberInfiniteTransition(label = "recording").animateFloat(
            initialValue = 1f,
            targetValue = MoyiMotion.BreatheMinAlpha,
            animationSpec = infiniteRepeatable(tween(MoyiMotion.BreatheMillis, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "recording dot",
        )
        ({ breath })
    } else {
        ({ 1f })
    }
    Canvas(Modifier.size(MoyiDimens.Waveform.RecordingDot)) {
        drawCircle(color = color, alpha = alpha())
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
