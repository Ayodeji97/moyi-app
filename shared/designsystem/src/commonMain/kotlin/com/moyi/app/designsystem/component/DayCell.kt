package com.moyi.app.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import com.moyi.app.designsystem.MoyiTheme
import com.moyi.app.designsystem.theme.MoyiDimens

/**
 * What a streak day was, as the calendar draws it (tokens.md §4 component 4). Each state
 * differs by **shape**, so the calendar reads in greyscale (NFR-104), and the screen's key
 * gives each its label.
 *
 * tokens.md also lists `solo`. It has **no drawn shape and no key entry yet** (states.md
 * §7, ruled 2026-10-06 and not drawn), so it is not a state here: a solo day is drawn as
 * [Missed] until the design system draws it.
 */
enum class DayCellState {
    /** Both wrote: a filled disc in `ink`, the numeral in `surface`. */
    Complete,

    /** A rest day: a dashed ring in `outlineStrong` (states.md §7: `outline` is 1.65:1 and invisible). */
    Frozen,

    /** Today, still open: a solid ring in `ink`. */
    Today,

    /** No entry: the bare numeral and nothing else (states.md §7, "left blank"). */
    Missed,
}

/**
 * A streak calendar unit. [numeral] is the day of the month; the key's swatch passes null
 * and [MoyiDimens.DayCell.KeySize]. The cell carries no semantics of its own: the caller
 * says what the day was in words, so a screen reader never hears a bare number.
 */
@Composable
fun DayCell(
    state: DayCellState,
    numeral: String?,
    modifier: Modifier = Modifier,
    size: Dp = MoyiDimens.DayCell.Size,
) {
    val colors = MoyiTheme.colors
    Box(
        modifier = modifier
            .size(size)
            .drawBehind {
                val stroke = MoyiDimens.DayCell.Stroke.toPx()
                val ring = this.size.minDimension / 2 - stroke / 2
                when (state) {
                    DayCellState.Complete -> drawCircle(colors.ink)
                    DayCellState.Today -> drawCircle(colors.ink, radius = ring, style = Stroke(width = stroke))
                    DayCellState.Frozen -> {
                        val dash = MoyiDimens.DayCell.Dash.toPx()
                        drawCircle(
                            colors.outlineStrong,
                            radius = ring,
                            style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash))),
                        )
                    }
                    DayCellState.Missed -> Unit
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (numeral != null) {
            Text(
                text = numeral,
                style = MoyiTheme.type.body,
                color = when (state) {
                    DayCellState.Complete -> colors.surface
                    DayCellState.Missed -> colors.inkMuted
                    DayCellState.Frozen, DayCellState.Today -> colors.ink
                },
            )
        }
    }
}

@Preview
@Composable
private fun DayCellDarkPreview() = PreviewSurface(darkTheme = true) { DayCellSamples() }

@Preview
@Composable
private fun DayCellLightPreview() = PreviewSurface(darkTheme = false) { DayCellSamples() }

@Composable
private fun DayCellSamples() {
    Row(horizontalArrangement = Arrangement.spacedBy(MoyiTheme.spacing.sm)) {
        DayCell(DayCellState.Complete, "12")
        DayCell(DayCellState.Frozen, "13")
        DayCell(DayCellState.Missed, "14")
        DayCell(DayCellState.Today, "15")
        DayCell(DayCellState.Complete, null, size = MoyiDimens.DayCell.KeySize)
        DayCell(DayCellState.Frozen, null, size = MoyiDimens.DayCell.KeySize)
        DayCell(DayCellState.Today, null, size = MoyiDimens.DayCell.KeySize)
    }
}
