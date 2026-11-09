package com.moyi.app.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import com.moyi.app.designsystem.theme.MoyiDimens

/**
 * **Interim, not in the design system** (docs/missing-components.md §45): the reactions I
 * can leave on the partner's revealed entry (FR-048, ADR-0037). `states.md` and `tokens.md`
 * draw no reaction and no control for one (ADR-0037 question 9); until they do, this is the
 * most conservative stand-in, built only from what the system has:
 *
 * - one [Chip] per reaction, as **words** (whether each is a glyph or a word is the
 *   design's to decide), wrapping onto a second line at large text sizes;
 * - **at most one is selected** ([selected]): tapping another replaces it, tapping the
 *   selected one takes it back. No confirmation, no undo toast, as the FavouriteToggle;
 * - **no count, no time, no "seen"**: there is nothing here but which one, if any;
 * - spoken as a group of radio buttons, each by its word with its selected state, and
 *   [takeBackLabel] as what a double tap on the selected one does (a radio button cannot
 *   usually be cleared, so the screen reader is told).
 *
 * The labels and what a choice means are the caller's: this draws a list of words.
 */
@Composable
fun ReactionChoices(
    labels: List<String>,
    selected: Int?,
    onChoose: (Int) -> Unit,
    takeBackLabel: String,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(MoyiDimens.Chip.Gap),
    ) {
        labels.forEachIndexed { index, label ->
            Chip(
                text = label,
                selected = index == selected,
                onClick = { onChoose(index) },
                role = Role.RadioButton,
                onClickLabel = takeBackLabel.takeIf { index == selected },
            )
        }
    }
}

@Preview
@Composable
private fun ReactionChoicesDarkPreview() = PreviewSurface(darkTheme = true) { ReactionChoicesSamples() }

@Preview
@Composable
private fun ReactionChoicesLightPreview() = PreviewSurface(darkTheme = false) { ReactionChoicesSamples() }

@Composable
private fun ReactionChoicesSamples() {
    // Made-up words for the preview only; the real ones are the caller's string resources.
    val labels = listOf("Heart", "Thank you", "Smile", "Moved", "Me too")
    Column {
        ReactionChoices(labels = labels, selected = null, onChoose = {}, takeBackLabel = "Take back")
        ReactionChoices(labels = labels, selected = 1, onChoose = {}, takeBackLabel = "Take back")
    }
}
