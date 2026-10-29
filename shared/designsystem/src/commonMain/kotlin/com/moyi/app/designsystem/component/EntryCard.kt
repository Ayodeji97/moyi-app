package com.moyi.app.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.moyi.app.designsystem.MoyiTheme
import com.moyi.app.designsystem.theme.MoyiDimens

/** The four variants of [EntryCard] (`tokens.md` §4, component 1). */
enum class EntryCardVariant {
    /** What the reader wrote. */
    Own,

    /** What the other member wrote, once the day is open (or SOLO, FR-063). */
    Partner,

    /**
     * **A security boundary** (BR-8 / T-01): no text, no count, no timestamp, no
     * media flag. Only the author label is drawn. Today never uses it, because Today
     * shows no partner status of any kind (states.md §3); it exists for Archive.
     */
    Locked,

    /**
     * A revealed entry its author deleted (states.md §5, Appendix). Recessed to
     * `surface`: its flatness reads as absence. The author label is the caller's,
     * and on your own deleted entry it must read "From you" (states.md Appendix).
     */
    Tombstone,
}

/**
 * An entry: an author label over the words (`tokens.md` §4, component 1).
 *
 * - The words are the `entry` style, Newsreader 34 / 1.40, at **one size at every
 *   length** (tokens.md §2): the card hugs the text, and the screen decides whether
 *   to centre it or scroll it.
 * - `surfaceRaised` with an `outline` hairline, radius 12, padding 24, a 16 gap
 *   between label and words. No shadow (tokens.md §3). Numbers in `MoyiDimens.EntryCard`.
 * - **No timestamp slot and no reaction slot**: a timestamp at reveal is a metric
 *   (`14` §3), and the reaction slot is owed (ADR-0037, states.md §5a).
 * - Read as one unit by screen readers (label, then words).
 *
 * TODO(design): the paper grain on the three raised variants (tokens.md §8 item 5:
 * a 256 px tile per mode, swapped at the platform layer) is not in the repository yet.
 *
 * @param text the words; ignored for [EntryCardVariant.Locked], and for
 *   [EntryCardVariant.Tombstone] the short line that stands where the words were.
 */
@Composable
fun EntryCard(
    authorLabel: String,
    text: String,
    modifier: Modifier = Modifier,
    variant: EntryCardVariant = EntryCardVariant.Own,
) {
    val colors = MoyiTheme.colors
    val shapes = MoyiTheme.shapes
    val recessed = variant == EntryCardVariant.Tombstone
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(if (recessed) colors.surface else colors.surfaceRaised, shapes.radius)
            .border(MoyiDimens.EntryCard.Stroke, colors.outline, shapes.radius)
            .padding(MoyiDimens.EntryCard.Padding)
            // One stop for a screen reader: "From Tunde. You stayed up…".
            .semantics(mergeDescendants = true) {},
    ) {
        MoyiText(text = authorLabel, role = TextRole.Label)
        when (variant) {
            EntryCardVariant.Own, EntryCardVariant.Partner -> {
                Spacer(Modifier.height(MoyiDimens.EntryCard.LabelGap))
                MoyiText(text = text, role = TextRole.Entry)
            }
            EntryCardVariant.Tombstone -> {
                Spacer(Modifier.height(MoyiDimens.EntryCard.LabelGap))
                MoyiText(text = text, tone = TextTone.Muted)
            }
            // Nothing: not the text, its length, or whether it has media.
            EntryCardVariant.Locked -> Unit
        }
    }
}

@Preview
@Composable
private fun EntryCardDarkPreview() = PreviewSurface(darkTheme = true) { EntryCardSamples() }

@Preview
@Composable
private fun EntryCardLightPreview() = PreviewSurface(darkTheme = false) { EntryCardSamples() }

@Composable
private fun EntryCardSamples() {
    Column {
        EntryCard("From Tunde", "You stayed up to help me finish it, and never once made me feel slow.", variant = EntryCardVariant.Partner)
        Spacer(Modifier.height(MoyiTheme.spacing.md))
        EntryCard("From you", "The way you laughed at the bus stop.")
        Spacer(Modifier.height(MoyiTheme.spacing.md))
        EntryCard("From you", "You deleted what you wrote.", variant = EntryCardVariant.Tombstone)
        Spacer(Modifier.height(MoyiTheme.spacing.md))
        EntryCard("From Tunde", "", variant = EntryCardVariant.Locked)
    }
}
