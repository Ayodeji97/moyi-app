package com.moyi.app.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.moyi.app.designsystem.MoyiTheme
import com.moyi.app.designsystem.platform.ProtectFromCapture
import com.moyi.app.designsystem.theme.MoyiDimens

/**
 * One entry's line on an [ArchiveDayRow]: who wrote it, and its first line.
 * [muted] is for a line that is not the person's words (a tombstone, a "Photo" label).
 */
@Immutable
data class ArchiveRowLine(val author: String, val text: String, val muted: Boolean = false)

/**
 * **PROPOSED** (docs/missing-components.md §76). A day in the Archive list as a compact row
 * instead of a card (owner ruling 2026-11-11): no fill, no border, a `outline` hairline
 * underneath, and the whole row opens the day.
 *
 * - **Date column** (36 wide, growing with the font scale): the day number in `title` (serif)
 *   over the short weekday in `label` (a date, so capitals are allowed: tokens.md §2, #80).
 * - **Lines**: one per entry, an author in `label` then the first line of the words in
 *   `action` (the serif at body size), cut to one line with an ellipsis.
 * - **Chips**: small outline pills in sentence case (`tag`) for what is not in the line:
 *   photo, voice note, favourite. Decorative, never a target; the row is the target.
 *
 * The row is **one element for a screen reader**, spoken as [description]; the caller builds
 * it. The row is at least 48 tall (NFR-101).
 *
 * [protectFromCapture]: the caller sets it when a line holds the partner's words (R3,
 * [ProtectFromCapture]). Off by default, because a row of only my own words may be captured.
 */
@Composable
fun ArchiveDayRow(
    dayNumber: String,
    weekday: String,
    lines: List<ArchiveRowLine>,
    chips: List<String>,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    protectFromCapture: Boolean = false,
) {
    val colors = MoyiTheme.colors
    val spacing = MoyiTheme.spacing
    // R3: shared, ref-counted with every other holder; protection ends when the last leaves.
    if (protectFromCapture) ProtectFromCapture()
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = MoyiDimens.TouchTarget)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(vertical = spacing.md)
                .clearAndSetSemantics { contentDescription = description },
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.widthIn(min = MoyiDimens.ArchiveRow.DateColumn), horizontalAlignment = Alignment.CenterHorizontally) {
                MoyiText(text = dayNumber, role = TextRole.Title, tone = TextTone.Ink, textAlign = TextAlign.Center)
                MoyiText(text = weekday, role = TextRole.Label, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.width(spacing.md))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                ArchiveLines(lines)
                if (chips.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        chips.forEach { MetaChip(it) }
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(MoyiDimens.ArchiveRow.Hairline).background(colors.outline))
    }
}

/**
 * The lines of one row, with the words of every line starting at the same x. The author
 * labels differ in width ("YOU" against "ERMB"), so each line is not a plain Row: a [Layout]
 * measures every label, takes the widest (never more than [MoyiDimens.ArchiveRow.AuthorMaxShare]
 * of the width, so a long name ellipsises instead of crowding the words out at a large font
 * scale), and starts all the words after it. The column grows with the font because it is
 * measured, not a fixed dp. Each line is as tall as its taller part, the label centred in it.
 */
@Composable
private fun ArchiveLines(lines: List<ArchiveRowLine>) {
    val gap = MoyiTheme.spacing.sm
    val rowGap = MoyiTheme.spacing.xs
    Layout(
        content = {
            lines.forEach { line ->
                MoyiText(text = line.author, role = TextRole.Label, maxLines = 1, ellipsis = true)
                MoyiText(
                    text = line.text,
                    role = TextRole.Action,
                    tone = if (line.muted) TextTone.Muted else TextTone.Ink,
                    maxLines = 1,
                    ellipsis = true,
                )
            }
        },
    ) { measurables, constraints ->
        val gapPx = gap.roundToPx()
        val rowGapPx = rowGap.roundToPx()
        val cap = (constraints.maxWidth * MoyiDimens.ArchiveRow.AuthorMaxShare).toInt()
        val labels = measurables.filterIndexed { i, _ -> i % 2 == 0 }.map { it.measure(Constraints(maxWidth = cap)) }
        val labelWidth = labels.maxOfOrNull { it.width } ?: 0
        val textRoom = (constraints.maxWidth - labelWidth - gapPx).coerceAtLeast(0)
        val texts = measurables.filterIndexed { i, _ -> i % 2 == 1 }.map { it.measure(Constraints(maxWidth = textRoom)) }
        val heights = labels.indices.map { maxOf(labels[it].height, texts[it].height) }
        val height = heights.sum() + rowGapPx * (heights.size - 1).coerceAtLeast(0)
        layout(constraints.maxWidth.coerceAtLeast(0), height) {
            var y = 0
            labels.indices.forEach { i ->
                labels[i].placeRelative(0, y + (heights[i] - labels[i].height) / 2)
                texts[i].placeRelative(labelWidth + gapPx, y + (heights[i] - texts[i].height) / 2)
                y += heights[i] + rowGapPx
            }
        }
    }
}

/** A small static pill: `outline` border, sentence-case `tag` text. Not interactive. */
@Composable
private fun MetaChip(text: String) {
    Box(
        Modifier
            .height(MoyiDimens.ArchiveRow.ChipHeight)
            .border(MoyiDimens.ArchiveRow.ChipStroke, MoyiTheme.colors.outlineStrong, MoyiTheme.shapes.full)
            .padding(horizontal = MoyiTheme.spacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        MoyiText(text = text, role = TextRole.Tag, maxLines = 1)
    }
}

@Preview
@Composable
private fun ArchiveDayRowDarkPreview() = RowSpecimen(darkTheme = true)

@Preview
@Composable
private fun ArchiveDayRowLightPreview() = RowSpecimen(darkTheme = false)

@Composable
private fun RowSpecimen(darkTheme: Boolean) {
    PreviewSurface(darkTheme) {
        Column {
            ArchiveDayRow(
                dayNumber = "11",
                weekday = "Wed",
                lines = listOf(
                    ArchiveRowLine("You", "Tea in the garden before the rain came in and everyone went inside"),
                    ArchiveRowLine("Ermb", "Photo", muted = true),
                ),
                chips = listOf("Photo", "Favourite"),
                description = "Wednesday, 11 November. You: Tea in the garden. Ermb: Photo.",
                onClick = {},
            )
            ArchiveDayRow(
                dayNumber = "9",
                weekday = "Mon",
                lines = listOf(ArchiveRowLine("You", "A short one")),
                chips = emptyList(),
                description = "Monday, 9 November. You: A short one.",
                onClick = {},
            )
        }
    }
}
