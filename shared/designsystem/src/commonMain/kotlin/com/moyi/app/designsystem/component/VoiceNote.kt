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
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.testTag
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
 * note inside an EntryCard, under the words (and under the photo). One line (§68): a 48
 * play/pause target, the [VoiceWaveform] (tap or drag to move through it), then the clock in
 * `inkMuted`, how far it has played of its length: "0:12 of 0:42" ("0:00 of 0:42" before it
 * plays; owner ruling 2026-11-09), and the speed pill. While it is fetched a line under it says so (no spinner). Leaving the screen, or
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
 * One [VoiceRow] (§68): play or pause, the waveform, its clock ("0:12 of 0:42") and the
 * [SpeedPill]; then, at the end of the row, [trailing] (the composer's Record again and
 * Remove). The clock and the pill move under the bars only when the bars would get too narrow.
 * The lines for loading and failure go under it.
 *
 * [compact] (PROPOSED, docs/missing-components.md §66: the composer with the keyboard up): one
 * 48 line, as wide as it needs, of play/pause, the clock and [trailing]; no waveform and no
 * speed pill. The clock then carries what the waveform says to a screen reader ([label], and
 * the position as its state), so nothing it hears changes.
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
    compact: Boolean = false,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val duration = playback.durationMs
    val clock = voiceClock(playback)
    if (compact) {
        Column(modifier) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                VoiceIconTarget(
                    icon = if (playback.active) MoyiIcons.Pause else MoyiIcons.Play,
                    description = stringResource(if (playback.active) Res.string.ds_voice_pause else Res.string.ds_voice_play),
                    onClick = onToggle,
                )
                MoyiText(
                    text = clock,
                    tone = TextTone.Muted,
                    maxLines = 1,
                    modifier = Modifier.clearAndSetSemantics {
                        contentDescription = label
                        stateDescription = clock
                        progressBarRangeInfo = ProgressBarRangeInfo(WaveformMath.fractionOf(playback.positionMs, duration), 0f..1f)
                    },
                )
                // Remove sits a clear `md` past the clock, never beside play: one slip must not
                // throw a recording away (review of §66). Both stay 48 targets, apart.
                Spacer(Modifier.width(MoyiTheme.spacing.md))
                trailing()
            }
            PlaybackLine(playback.phase)
        }
        return
    }
    Column(modifier) {
        VoiceRow(
            play = { VoicePlayTarget(playing = playback.active, onClick = onToggle) },
            waveform = {
                VoiceWaveform(
                    levels = levels,
                    progress = { WaveformMath.fractionOf(playback.positionMs, duration) },
                    label = label,
                    position = clock,
                    // Seeking needs the length; the server sends it with every playable voice note.
                    onSeek = duration?.let { length -> { fraction: Float -> onSeek(WaveformMath.positionAt(fraction, length)) } },
                )
            },
            // "0:12 of 0:42". It repeats the waveform's position, which a screen reader already
            // hears. Tabular figures, so the line keeps its width as the seconds change and the
            // bars never shift while it plays.
            clock = {
                Text(
                    text = clock,
                    style = MoyiTheme.type.body.copy(fontFeatureSettings = TABULAR_FIGURES),
                    color = MoyiTheme.colors.inkMuted,
                    modifier = Modifier.clearAndSetSemantics { testTag = VOICE_CLOCK_TAG },
                )
            },
            pill = { SpeedPill(speed = speed, onClick = onCycleSpeed) },
            trailing = { Row(verticalAlignment = Alignment.CenterVertically, content = trailing) },
        )
        PlaybackLine(playback.phase)
    }
}

/** Finds the drawn clock ("0:12 of 0:42") in tests; it is not spoken (the waveform says it). */
const val VOICE_CLOCK_TAG = "voiceClock"

/** Finds the voice note's row (play, bars, clock, pill and any trailing controls) in tests. */
const val VOICE_ROW_TAG = "voiceRow"

private const val TABULAR_FIGURES = "tnum"

/**
 * **PROPOSED** (docs/missing-components.md §68, owner request 2026-11-10): the voice note on one
 * 48 line: [play], the [waveform] taking what is left, then the [clock] and the [pill], then
 * [trailing] (the composer's Record again and Remove).
 *
 * When that would leave the bars narrower than [MoyiDimens.Waveform.MinWidth] (large text, or
 * the composer's two extra targets on a narrow phone), the clock and the pill move under the
 * bars instead, starting where the bars start; and the pill under the clock if even that line
 * cannot hold both. Nothing is squeezed or clipped: the text keeps its natural width (up to the
 * line), and the bars keep a usable band to tap and drag.
 */
@Composable
private fun VoiceRow(
    play: @Composable () -> Unit,
    waveform: @Composable () -> Unit,
    clock: @Composable () -> Unit,
    pill: @Composable () -> Unit,
    trailing: @Composable () -> Unit,
) {
    val gapPx = with(LocalDensity.current) { MoyiDimens.Waveform.Gap.roundToPx() }
    val minBarsPx = with(LocalDensity.current) { MoyiDimens.Waveform.MinWidth.roundToPx() }
    val clockToPillPx = with(LocalDensity.current) { MoyiTheme.spacing.sm.roundToPx() }
    Layout(
        contents = listOf(play, waveform, clock, pill, trailing),
        modifier = Modifier.semantics { testTag = VOICE_ROW_TAG },
    ) { (playM, waveM, clockM, pillM, trailM), constraints ->
        val width = constraints.maxWidth
        val loose = Constraints(maxWidth = width)
        val playP = playM.first().measure(loose)
        val trailP = trailM.first().measure(Constraints(maxWidth = (width - playP.width).coerceAtLeast(0)))
        // Under the bars, a line runs from where the bars start to the row's end.
        val underWidth = (width - playP.width).coerceAtLeast(0)
        val clockP = clockM.first().measure(Constraints(maxWidth = underWidth))
        val pillP = pillM.first().measure(Constraints(maxWidth = underWidth))
        val besideBars = width - playP.width - trailP.width - gapPx - clockP.width - clockToPillPx - pillP.width
        val oneLine = besideBars >= minBarsPx
        val barsWidth = if (oneLine) besideBars else (width - playP.width - trailP.width - gapPx).coerceAtLeast(0)
        val waveP = waveM.first().measure(Constraints.fixedWidth(barsWidth))
        val lineHeight = maxOf(playP.height, waveP.height, trailP.height, if (oneLine) maxOf(clockP.height, pillP.height) else 0)
        // Under the bars: the clock and the pill side by side if they fit, the pill under the clock if not.
        val pillBesideClock = clockP.width + clockToPillPx + pillP.width <= underWidth
        val underHeight = when {
            oneLine -> 0
            pillBesideClock -> maxOf(clockP.height, pillP.height)
            else -> clockP.height + pillP.height
        }
        layout(width, lineHeight + underHeight) {
            fun centred(h: Int) = (lineHeight - h) / 2
            playP.placeRelative(0, centred(playP.height))
            waveP.placeRelative(playP.width, centred(waveP.height))
            trailP.placeRelative(width - trailP.width, centred(trailP.height))
            if (oneLine) {
                val clockX = playP.width + waveP.width + gapPx
                clockP.placeRelative(clockX, centred(clockP.height))
                pillP.placeRelative(clockX + clockP.width + clockToPillPx, centred(pillP.height))
            } else if (pillBesideClock) {
                clockP.placeRelative(playP.width, lineHeight + (underHeight - clockP.height) / 2)
                // Under the bars' end, as the pill sat before; never over the clock.
                val pillX = maxOf(playP.width + clockP.width + clockToPillPx, width - trailP.width - pillP.width)
                pillP.placeRelative(pillX, lineHeight + (underHeight - pillP.height) / 2)
            } else {
                clockP.placeRelative(playP.width, lineHeight)
                pillP.placeRelative(playP.width, lineHeight + clockP.height)
            }
        }
    }
}

/**
 * **PROPOSED** (docs/missing-components.md §68): play or pause, visibly a button but lighter
 * than the words it sits under: a 40 circle with a `hairline` of `outlineStrong` and no fill
 * (the border Rosewood gives a control, as [Chip] and the [SpeedPill]), the 24 glyph in `ink`,
 * in a 48 target. Spoken "Play the voice note" or "Pause the voice note", a button.
 */
@Composable
private fun VoicePlayTarget(playing: Boolean, onClick: () -> Unit) {
    val colors = MoyiTheme.colors
    Box(
        modifier = Modifier
            .size(MoyiDimens.VoicePlay.Target)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(MoyiDimens.VoicePlay.Circle)
                .border(MoyiTheme.shapes.hairline, colors.outlineStrong, MoyiTheme.shapes.full),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (playing) MoyiIcons.Pause else MoyiIcons.Play,
                contentDescription = stringResource(if (playing) Res.string.ds_voice_pause else Res.string.ds_voice_play),
                tint = colors.ink,
                modifier = Modifier.size(MoyiDimens.IconGlyph),
            )
        }
    }
}

/** The line under a player while it fetches, or when it cannot play. */
@Composable
private fun PlaybackLine(phase: PlaybackPhase) {
    when (phase) {
        PlaybackPhase.Loading -> MoyiText(text = stringResource(Res.string.ds_voice_loading), tone = TextTone.Muted)
        PlaybackPhase.Failed -> MoyiText(text = stringResource(Res.string.ds_voice_unplayable), tone = TextTone.Muted)
        else -> Unit
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
 * - [compact] (PROPOSED, §66: the keyboard is up): recording keeps trash, the dot with its clock
 *   and the tick, without the live waveform; recorded is a chip of play/pause, the clock and
 *   Remove (Record again comes back with the full row). The player is the same one either way,
 *   so listening back carries on through the change.
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
    compact: Boolean = false,
) {
    // With the keyboard up, in its own frame, so Remove reads as the voice note's, not the prompt's dismiss (§66).
    AttachmentFrame(framed = compact, modifier = modifier) { AttachedVoiceContent(state, onStop, onDiscard, onRecordAgain, onRemove, recordingLevels, removeRestoresSent, compact) }
}

@Composable
private fun AttachedVoiceContent(
    state: AttachedVoiceState,
    onStop: () -> Unit,
    onDiscard: () -> Unit,
    onRecordAgain: (() -> Unit)?,
    onRemove: (() -> Unit)?,
    recordingLevels: () -> List<Float>,
    removeRestoresSent: Boolean,
    compact: Boolean,
) {
    when (state) {
        is AttachedVoiceState.Recording -> Row(verticalAlignment = Alignment.CenterVertically) {
            val reduceMotion = rememberReduceMotion()
            // Start: throw it away, at once (an unsent recording, states.md §4: no confirmation).
            VoiceIconTarget(MoyiIcons.Trash, stringResource(Res.string.ds_compose_discard_recording), onDiscard)
            if (!compact) {
                LiveWaveform(
                    levels = recordingLevels,
                    scrolling = !reduceMotion,
                    capacity = (VoiceRecording.MAX_DURATION_MS / RecordingLevels.TICK_MS).toInt(),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(MoyiDimens.Waveform.Gap))
            }
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
                compact = compact,
            ) {
                if (onRecordAgain != null && !compact) {
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
 * [compact] (§66): the chip of play/pause and the clock, with Record again, its one control.
 */
@Composable
fun SentVoice(voice: VoiceNote, onRecordAgain: (() -> Unit)?, modifier: Modifier = Modifier, compact: Boolean = false) {
    AttachmentFrame(framed = compact, modifier = modifier) { SentVoiceContent(voice, onRecordAgain, compact) }
}

@Composable
private fun SentVoiceContent(voice: VoiceNote, onRecordAgain: (() -> Unit)?, compact: Boolean) {
    val again: @Composable RowScope.() -> Unit = {
        if (onRecordAgain != null) {
            VoiceIconTarget(MoyiIcons.RecordAgain, stringResource(Res.string.ds_compose_record_again), onRecordAgain)
        }
    }
    val url = voice.url
    if (url == null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
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
        compact = compact,
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
/** Finds the drawn speed pill in tests; its text is not in the semantics tree. */
const val SPEED_PILL_VISIBLE_TAG = "speedPillVisible"

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
                    // Cleared, not hidden: a hidden node still joins a merged parent's label
                    // (the EntryCard reads it), a cleared one adds nothing (as the clock's). The tag
                    // is for tests to find the drawn pill by; it is not spoken.
                    .clearAndSetSemantics { testTag = SPEED_PILL_VISIBLE_TAG }
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
