package com.moyi.app.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.moyi.app.designsystem.MoyiTheme
import com.moyi.app.designsystem.platform.ProtectFromCapture
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
 * The reaction I left on the partner's entry, drawn as a pill on the card's label row
 * (docs/missing-components.md §67). The words are the caller's string resources: [text] is
 * what is drawn ("You: 🥺 Moved"), [spoken] is what a screen reader says instead ("You reacted: Moved").
 */
data class EntryCardReaction(val text: String, val spoken: String)

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
 * - **A `Partner` card protects the screen from capture** while it is shown (R3,
 *   [ProtectFromCapture]).
 *
 * TODO(design): the paper grain on the three raised variants (tokens.md §8 item 5:
 * a 256 px tile per mode, swapped at the platform layer) is not in the repository yet.
 *
 * @param text the words; ignored for [EntryCardVariant.Locked], and for
 *   [EntryCardVariant.Tombstone] the short line that stands where the words were.
 * @param protectFromCapture on by default for [EntryCardVariant.Partner]. Turn it off
 *   only for words no partner wrote (the promise screen's made-up samples), so that
 *   leaving a real partner card unprotected has to be written at the call site.
 *   A partner's photo is drawn inside the same card, so the same protection covers it.
 * @param photo the entry's photo, under the words; drawn only on Own and Partner. Not a
 *   drawn slot yet (docs/missing-components.md §40).
 * @param reaction my reaction to this entry, a display-only pill at the right of the label row
 *   (§67): drawn on a [EntryCardVariant.Partner] card only, spoken as [EntryCardReaction.spoken]
 *   at the end of the card's one stop, with no target of its own.
 * @param voice the entry's voice note, under the words and the photo; drawn only on Own and
 *   Partner. Not a drawn slot yet (§43). A partner's is protected with the card.
 */
@Composable
fun EntryCard(
    authorLabel: String,
    text: String,
    modifier: Modifier = Modifier,
    variant: EntryCardVariant = EntryCardVariant.Own,
    protectFromCapture: Boolean = variant == EntryCardVariant.Partner,
    photo: EntryPhoto? = null,
    voice: VoiceNote? = null,
    reaction: EntryCardReaction? = null,
) {
    val colors = MoyiTheme.colors
    val shapes = MoyiTheme.shapes
    val recessed = variant == EntryCardVariant.Tombstone
    // R3: wherever the partner's words are drawn, the screen cannot be captured.
    // Here rather than on each screen, so no screen can draw them and forget.
    if (protectFromCapture) ProtectFromCapture()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(if (recessed) colors.surface else colors.surfaceRaised, shapes.radius)
            .border(MoyiDimens.EntryCard.Stroke, colors.outline, shapes.radius)
            .padding(MoyiDimens.EntryCard.Padding)
            // One stop for a screen reader: "From Tunde. You stayed up…".
            .semantics(mergeDescendants = true) {},
    ) {
        if (reaction != null && variant == EntryCardVariant.Partner) {
            Row(horizontalArrangement = Arrangement.spacedBy(MoyiTheme.spacing.sm), verticalAlignment = Alignment.Top) {
                MoyiText(text = authorLabel, role = TextRole.Label, modifier = Modifier.weight(1f))
                ReactionPill(reaction)
            }
        } else {
            MoyiText(text = authorLabel, role = TextRole.Label)
        }
        when (variant) {
            EntryCardVariant.Own, EntryCardVariant.Partner -> {
                Spacer(Modifier.height(MoyiDimens.EntryCard.LabelGap))
                MoyiText(text = text, role = TextRole.Entry)
                // Under the words: text is always sufficient (NFR-103), the photo is secondary.
                if (photo != null) {
                    Spacer(Modifier.height(MoyiDimens.EntryCard.LabelGap))
                    EntryPhotoView(photo)
                }
                // Then the voice note, as secondary as the photo (NFR-103).
                if (voice != null) {
                    Spacer(Modifier.height(MoyiDimens.EntryCard.LabelGap))
                    VoiceNoteView(voice)
                }
            }
            EntryCardVariant.Tombstone -> {
                Spacer(Modifier.height(MoyiDimens.EntryCard.LabelGap))
                MoyiText(text = text, tone = TextTone.Muted)
            }
            // Nothing: not the text, its length, or whether it has media. A photo or voice note
            // passed with a locked or deleted card is not drawn either.
            EntryCardVariant.Locked -> Unit
        }
    }
}

/**
 * **PROPOSED** (§67): the same pill as the speed pill (`label` in `ink`, a `hairline` of
 * `outlineStrong`, `shapes.full`, padded `sm` across and `xs` down), but display-only: no
 * target, nothing to tap. Drawn text is cleared and [EntryCardReaction.spoken] stands in for it.
 */
@Composable
private fun ReactionPill(reaction: EntryCardReaction) {
    Text(
        text = reaction.text,
        style = MoyiTheme.type.label,
        color = MoyiTheme.colors.ink,
        maxLines = 1,
        modifier = Modifier
            .clearAndSetSemantics { contentDescription = reaction.spoken }
            .border(MoyiTheme.shapes.hairline, MoyiTheme.colors.outlineStrong, MoyiTheme.shapes.full)
            .padding(horizontal = MoyiTheme.spacing.sm, vertical = MoyiTheme.spacing.xs),
    )
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
        EntryCard(
            "From Tunde",
            "You stayed up to help me finish it, and never once made me feel slow.",
            variant = EntryCardVariant.Partner,
            reaction = EntryCardReaction("You: \uD83E\uDD79 Moved", "You reacted: Moved"),
        )
        Spacer(Modifier.height(MoyiTheme.spacing.md))
        EntryCard("From you", "The way you laughed at the bus stop.")
        Spacer(Modifier.height(MoyiTheme.spacing.md))
        EntryCard("From you", "You deleted what you wrote.", variant = EntryCardVariant.Tombstone)
        Spacer(Modifier.height(MoyiTheme.spacing.md))
        EntryCard("From Tunde", "", variant = EntryCardVariant.Locked)
    }
}
