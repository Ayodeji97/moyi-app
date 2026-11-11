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
) {
    val colors = MoyiTheme.colors
    val spacing = MoyiTheme.spacing
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
                lines.forEach { line ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MoyiText(text = line.author, role = TextRole.Label, maxLines = 1, modifier = Modifier.widthIn(max = MoyiDimens.ArchiveRow.AuthorMaxWidth))
                        Spacer(Modifier.width(spacing.sm))
                        MoyiText(
                            text = line.text,
                            role = TextRole.Action,
                            tone = if (line.muted) TextTone.Muted else TextTone.Ink,
                            maxLines = 1,
                            ellipsis = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
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
