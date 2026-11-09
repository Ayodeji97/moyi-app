package com.moyi.app.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.moyi.app.designsystem.MoyiTheme

/**
 * **Interim, not in the design system** (docs/missing-components.md §45, owner ruling 6 of
 * 2026-11-09): the reaction the partner left on my entry, under my [EntryCard]. More visible
 * than the muted line it replaces, built only from Rosewood tokens, no new colour:
 *
 * - a pill (`shapes.full`) in `surfaceRaised` with an `outline` hairline, the EntryCard's own
 *   pair, so it reads as belonging to the card above it and not as a control (a control's
 *   border is `outlineStrong`, and this has no target and does nothing when tapped);
 * - the [emoji], then [text] in `body` and `ink` (the muted line was `inkMuted`), padded
 *   `md` across and `sm` down, the two `sm` apart; [text] wraps at large sizes, never cut;
 * - **spoken as [text] only**: the emoji is drawn, not read. Not a live region and nothing
 *   announces it when it arrives (ADR-0037 decision 10: no "seen" signal).
 */
@Composable
fun ReactionBadge(emoji: String, text: String, modifier: Modifier = Modifier) {
    val colors = MoyiTheme.colors
    val pill = MoyiTheme.shapes.full
    Row(
        modifier = modifier
            .semantics(mergeDescendants = true) { }
            .background(colors.surfaceRaised, pill)
            .border(MoyiTheme.shapes.hairline, colors.outline, pill)
            .padding(horizontal = MoyiTheme.spacing.md, vertical = MoyiTheme.spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(MoyiTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = emoji, modifier = Modifier.clearAndSetSemantics { }, style = MoyiTheme.type.body, maxLines = 1)
        Text(text = text, style = MoyiTheme.type.body, color = colors.ink)
    }
}

@Preview
@Composable
private fun ReactionBadgeDarkPreview() = PreviewSurface(darkTheme = true) { ReactionBadgeSamples() }

@Preview
@Composable
private fun ReactionBadgeLightPreview() = PreviewSurface(darkTheme = false) { ReactionBadgeSamples() }

@Composable
private fun ReactionBadgeSamples() {
    // Made-up sentences for the preview only; the real ones are the caller's string resources.
    Column(verticalArrangement = Arrangement.spacedBy(MoyiTheme.spacing.sm)) {
        ReactionBadge(emoji = "❤️", text = "Tunde sent a heart.")
        ReactionBadge(emoji = "🤝", text = "Tunde is grateful for that too.")
    }
}
