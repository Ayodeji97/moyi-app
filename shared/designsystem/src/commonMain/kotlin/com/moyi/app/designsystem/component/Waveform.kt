package com.moyi.app.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import com.moyi.app.designsystem.MoyiTheme
import com.moyi.app.designsystem.platform.RecordingLevels
import com.moyi.app.designsystem.theme.MoyiDimens
import kotlin.math.max
import kotlin.math.sin

/**
 * **PROPOSED, not drawn** (docs/missing-components.md §46): a voice note as a row of
 * rounded vertical bars, the way messaging apps draw a voice message, kept quiet:
 *
 * - Played bars in `ink`, the rest in `outlineStrong` (Rosewood has no accent, tokens.md §1;
 *   `outlineStrong` is the token measured at 3:1 for a graphic that carries meaning). The
 *   split is also the position, so it is never colour alone: the clock beside it says it in
 *   words, and the screen reader hears it as a position ([stateDescription]).
 * - Tap or drag anywhere in the 48 tall band to move there ([onSeek], a fraction from 0 to
 *   1). While dragging, the bars follow the finger; the player moves when the finger lifts.
 *   A screen reader adjusts it as a slider (`setProgress`).
 * - Right to left, it plays from the right.
 *
 * [levels] (0..1) are resampled to as many bars as fit ([WaveformMath.resample]).
 * [progress] is read only when the bars are drawn, so the playing position redraws them
 * without recomposing anything around them.
 */
@Composable
fun VoiceWaveform(
    levels: List<Float>,
    progress: () -> Float,
    /** Its spoken name, for example "Position in the voice note". */
    label: String,
    /** Where it is, in words, for example "0:12 of 0:45". */
    position: String,
    onSeek: ((Float) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    var dragging by remember { mutableStateOf<Float?>(null) }
    val played = MoyiTheme.colors.ink
    val unplayed = MoyiTheme.colors.outlineStrong
    val seek = if (onSeek == null) {
        Modifier
    } else {
        Modifier
            .pointerInput(onSeek, rtl) {
                detectTapGestures { onSeek(WaveformMath.seekFraction(it.x, size.width.toFloat(), rtl)) }
            }
            .pointerInput(onSeek, rtl) {
                detectHorizontalDragGestures(
                    onDragStart = { dragging = WaveformMath.seekFraction(it.x, size.width.toFloat(), rtl) },
                    onDragEnd = {
                        dragging?.let(onSeek)
                        dragging = null
                    },
                    onDragCancel = { dragging = null },
                ) { change, _ ->
                    change.consume()
                    dragging = WaveformMath.seekFraction(change.position.x, size.width.toFloat(), rtl)
                }
            }
    }
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(MoyiDimens.Waveform.Target)
            .semantics {
                contentDescription = label
                stateDescription = position
                progressBarRangeInfo = ProgressBarRangeInfo(progress().coerceIn(0f, 1f), 0f..1f)
                if (onSeek != null) {
                    setProgress { target ->
                        onSeek(target.coerceIn(0f, 1f))
                        true
                    }
                }
            }
            .then(seek),
    ) {
        val shown = (dragging ?: progress()).coerceIn(0f, 1f)
        drawBars(levels, rtl) { centre -> if (centre <= shown * size.width) played else unplayed }
    }
}

/**
 * **PROPOSED, not drawn** (docs/missing-components.md §47): the microphone's level while a
 * voice note records, drawn as it arrives, in `ink`.
 *
 * - It moves from the first frame it is shown (owner ruling 2026-11-09): time, not the
 *   microphone, drives it. Each [RecordingLevels.TICK_MS] of recording is a bar whether or
 *   not its level has arrived, and a quiet bar is never lower than a gentle idle baseline
 *   ([WaveformMath.idleLevel]), so the row is visibly alive before anyone speaks.
 * - [scrolling]: the newest bar slides in at the end, older ones move off the start,
 *   smoothly frame by frame, as messaging apps do while recording.
 * - Not [scrolling] (Reduce Motion, or Android's "Remove animations"): nothing moves. The
 *   band stands for the whole minute allowed; each bar appears in its place, left to right,
 *   and the minute still to come is the row of `outlineStrong` dots after it.
 *
 * The time is read only in the draw phase, so each frame redraws the bars and recomposes
 * nothing. Decorative to a screen reader: the clock beside it says the same in words.
 */
@Composable
fun LiveWaveform(
    levels: () -> List<Float>,
    scrolling: Boolean,
    /** How many samples the whole recording can hold ([scrolling] false lays them out on it). */
    capacity: Int,
    modifier: Modifier = Modifier,
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val heard = MoyiTheme.colors.ink
    val ahead = MoyiTheme.colors.outlineStrong
    // Milliseconds since the row appeared, which is the moment recording started.
    val elapsedMs = remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        // An infinite-animation frame: a UI test does not wait for it to finish.
        val start = withInfiniteAnimationFrameMillis { it }
        while (true) withInfiniteAnimationFrameMillis { elapsedMs.longValue = it - start }
    }
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(MoyiDimens.Waveform.Target)
            .clipToBounds()
            .clearAndSetSemantics {},
    ) {
        val count = WaveformMath.barCount(size.width, MoyiDimens.Waveform.BarWidth.toPx(), MoyiDimens.Waveform.BarGap.toPx())
        val ms = elapsedMs.longValue
        val shown = WaveformMath.liveLevels(levels(), ms)
        if (scrolling) {
            // One bar more than fits: the newest slides in from the end over its tick.
            val pitch = MoyiDimens.Waveform.BarWidth.toPx() + MoyiDimens.Waveform.BarGap.toPx()
            val shift = WaveformMath.tickFraction(ms) * pitch
            drawBarSlots(WaveformMath.latest(shown, count + 1), rtl, shift) { level -> if (level == null) ahead else heard }
        } else {
            drawBarSlots(WaveformMath.onTimeline(shown, capacity, count), rtl) { level -> if (level == null) ahead else heard }
        }
    }
}

/** Draws [levels] resampled to the bars that fit, each coloured by [colorAt] its centre x. */
private fun DrawScope.drawBars(levels: List<Float>, rtl: Boolean, colorAt: (Float) -> Color) {
    val count = WaveformMath.barCount(size.width, MoyiDimens.Waveform.BarWidth.toPx(), MoyiDimens.Waveform.BarGap.toPx())
    val bars = WaveformMath.resample(levels, count)
    val pitch = MoyiDimens.Waveform.BarWidth.toPx() + MoyiDimens.Waveform.BarGap.toPx()
    bars.forEachIndexed { index, level ->
        // The fill runs from the start edge, so colour by the distance from it.
        val fromStart = index * pitch + MoyiDimens.Waveform.BarWidth.toPx() / 2
        drawBar(index, level, rtl, colorAt(fromStart))
    }
}

/**
 * Draws one bar per slot, moved [shift] px towards the start; a null slot is the minimum
 * dot (time not recorded yet).
 */
private fun DrawScope.drawBarSlots(bars: List<Float?>, rtl: Boolean, shift: Float = 0f, colorFor: (Float?) -> Color) {
    bars.forEachIndexed { index, level -> drawBar(index, level ?: 0f, rtl, colorFor(level), shift) }
}

private fun DrawScope.drawBar(index: Int, level: Float, rtl: Boolean, color: Color, shift: Float = 0f) {
    val width = MoyiDimens.Waveform.BarWidth.toPx()
    val pitch = width + MoyiDimens.Waveform.BarGap.toPx()
    val tallest = MoyiDimens.Waveform.Height.toPx()
    val height = max(MoyiDimens.Waveform.MinBar.toPx(), level.coerceIn(0f, 1f) * tallest)
    val start = index * pitch - shift
    val x = if (rtl) size.width - start - width else start
    drawRoundRect(
        color = color,
        topLeft = Offset(x, (size.height - height) / 2),
        size = Size(width, height),
        cornerRadius = CornerRadius(width / 2),
    )
}

/** The arithmetic behind the waveforms, pure so it is tested on its own. */
object WaveformMath {
    /** How many bars of [barPx] with [gapPx] between them fit in [widthPx] (the last needs no gap). */
    fun barCount(widthPx: Float, barPx: Float, gapPx: Float): Int {
        if (widthPx < barPx || barPx <= 0f) return 0
        return ((widthPx + gapPx) / (barPx + gapPx)).toInt()
    }

    /**
     * [levels] as exactly [count] bars. Fewer bars than levels: each bar is the mean of its
     * share. (The loudest of each share was tried first: over a second of speech every share
     * reaches a peak, and a long note drew as a flat block. The mean keeps the pauses.) More
     * bars than levels: each level is repeated across its share. No levels: silent bars.
     */
    fun resample(levels: List<Float>, count: Int): List<Float> {
        if (count <= 0) return emptyList()
        if (levels.isEmpty()) return List(count) { 0f }
        return List(count) { bar ->
            val from = (bar.toLong() * levels.size / count).toInt()
            val to = max(from + 1, ((bar + 1).toLong() * levels.size / count).toInt())
            var sum = 0f
            for (i in from until to) sum += levels[i]
            sum / (to - from)
        }
    }

    /** Where a touch at [x] across [width] lands, from 0 (the start) to 1 (the end). */
    fun seekFraction(x: Float, width: Float, rtl: Boolean = false): Float {
        if (width <= 0f) return 0f
        val fraction = (x / width).coerceIn(0f, 1f)
        return if (rtl) 1f - fraction else fraction
    }

    /** A fraction of a voice note [durationMs] long, in milliseconds. */
    fun positionAt(fraction: Float, durationMs: Long): Long =
        (fraction.coerceIn(0f, 1f) * durationMs).toLong().coerceIn(0, durationMs.coerceAtLeast(0))

    /** How far through [durationMs] [positionMs] is, from 0 to 1; 0 while the length is unknown. */
    fun fractionOf(positionMs: Long, durationMs: Long?): Float =
        if (durationMs == null || durationMs <= 0) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)

    /** The newest [count] levels, newest last, padded with nulls at the start until there are enough. */
    fun latest(levels: List<Float>, count: Int): List<Float?> {
        if (count <= 0) return emptyList()
        val shown = levels.takeLast(count)
        return List(count - shown.size) { null } + shown
    }

    /**
     * What the live waveform draws [elapsedMs] into a recording: one level per
     * [RecordingLevels.TICK_MS] that has begun, so the first bar is there at 0 ms, before
     * the microphone has reported anything. A level not reported yet, or a quiet one, is
     * the [idleLevel] of its slot, so the row moves from the first frame. Never fewer than
     * the [samples] reported (the recorder can be a tick ahead of the screen's clock).
     */
    fun liveLevels(samples: List<Float>, elapsedMs: Long): List<Float> {
        val slots = max(samples.size, (elapsedMs.coerceAtLeast(0) / RecordingLevels.TICK_MS).toInt() + 1)
        return List(slots) { slot -> max(samples.getOrNull(slot) ?: 0f, idleLevel(slot)) }
    }

    /**
     * The quiet baseline of the live waveform: a gentle ripple of short bars, 3.5 to 6.5 dp
     * of the 32 dp band, the same for the same [slot] every time, so it reads as a line
     * that is listening rather than as noise. Only drawn while recording; never kept with
     * the recording, so listening back shows the real levels.
     */
    fun idleLevel(slot: Int): Float = IDLE_MID + IDLE_SWING * sin(slot * IDLE_STEP)

    /** How far through its current tick [elapsedMs] is, 0 until 1: the live waveform's slide. */
    fun tickFraction(elapsedMs: Long): Float =
        (elapsedMs.coerceAtLeast(0) % RecordingLevels.TICK_MS).toFloat() / RecordingLevels.TICK_MS

    /**
     * The recording laid out on its whole [capacity] (the minute allowed) in [count] bars:
     * the bars already recorded carry the mean level of their share so far, the rest are null.
     */
    fun onTimeline(levels: List<Float>, capacity: Int, count: Int): List<Float?> {
        if (count <= 0 || capacity <= 0) return emptyList()
        return List(count) { bar ->
            val from = (bar.toLong() * capacity / count).toInt()
            if (from >= levels.size) {
                null
            } else {
                val to = max(from + 1, ((bar + 1).toLong() * capacity / count).toInt()).coerceAtMost(levels.size)
                var sum = 0f
                for (i in from until to) sum += levels[i]
                sum / (to - from)
            }
        }
    }

    /**
     * **Interim stand-in, not the voice note's sound** (docs/backend-contract-gaps.md row
     * 31): a waveform-like shape for a voice note whose levels the phone does not have,
     * that is, any voice note from the server, which sends none. The same [seed] (the media
     * id) always gives the same shape on both platforms, so a voice note looks the same
     * each time it is seen; it says nothing about where the loud parts are.
     *
     * The levels sit between 0.2 and 0.8 and change gently from bar to bar, so the shape
     * reads as speech without claiming silence or a shout that was never measured.
     */
    fun placeholder(seed: String, count: Int = PLACEHOLDER_COUNT): List<Float> {
        if (count <= 0) return emptyList()
        var state = fnv1a(seed).toULong() or 1uL
        val raw = List(count) {
            // xorshift64: small, deterministic, the same on every platform.
            state = state xor (state shl 13)
            state = state xor (state shr 7)
            state = state xor (state shl 17)
            (state % 1000uL).toFloat() / 1000f
        }
        return List(count) { i ->
            val before = raw[(i - 1).coerceAtLeast(0)]
            val after = raw[(i + 1).coerceAtMost(count - 1)]
            val smooth = (before + 2 * raw[i] + after) / 4f
            PLACEHOLDER_LOW + smooth * (PLACEHOLDER_HIGH - PLACEHOLDER_LOW)
        }
    }

    private fun fnv1a(text: String): Long {
        var hash = FNV_OFFSET
        for (char in text) {
            hash = hash xor char.code.toLong()
            hash *= FNV_PRIME
        }
        return hash
    }

    /** About one bar per 5 dp of a phone-wide card: resampled to the bars that fit. */
    const val PLACEHOLDER_COUNT = 64
    private const val IDLE_MID = 0.155f
    private const val IDLE_SWING = 0.045f
    private const val IDLE_STEP = 0.9f
    private const val PLACEHOLDER_LOW = 0.2f
    private const val PLACEHOLDER_HIGH = 0.8f
    private const val FNV_OFFSET = -3750763034362895579L // 0xcbf29ce484222325
    private const val FNV_PRIME = 1099511628211L
}

@Preview
@Composable
private fun WaveformDarkPreview() = PreviewSurface(darkTheme = true) { WaveformSamples() }

@Preview
@Composable
private fun WaveformLightPreview() = PreviewSurface(darkTheme = false) { WaveformSamples() }

@Composable
private fun WaveformSamples() {
    val levels = WaveformMath.placeholder("preview")
    Column(Modifier.padding(MoyiTheme.spacing.md)) {
        VoiceWaveform(levels = levels, progress = { 0.35f }, label = "Position", position = "0:12 of 0:42", onSeek = {})
        LiveWaveform(levels = { levels.take(40) }, scrolling = true, capacity = 750)
        LiveWaveform(levels = { levels.take(40) }, scrolling = false, capacity = 750)
    }
}
