package com.moyi.app.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics

import androidx.compose.ui.tooling.preview.Preview
import com.moyi.app.designsystem.MoyiTheme
import com.moyi.app.designsystem.theme.MoyiDimens

/** The one link a [FactsCard] may end with: words that say where it goes, and what a tap does. */
@Immutable
data class FactsCardLink(val text: String, val onClick: () -> Unit)

/**
 * A short list of plain facts on a raised card, under a `label` heading: "Good to know" before a
 * choice that sends something somewhere (owner ruling 2026-11-17, docs/missing-components.md §89).
 * **Owner-approved new component.** Not an [EntryCard]: it holds no one's words, so it has no
 * author, no capture protection and no variants. It takes EntryCard's raised surface only:
 * `surfaceRaised`, an `outline` hairline, radius 12, padding 24 (tokens.md §3: elevation is a
 * border, not a shadow).
 *
 * - The [label] (`label` role, `inkMuted`) is a heading to assistive technology.
 * - Each fact is a `body` line in `inkMuted` after a small round mark in `inkMuted`, 8 between
 *   facts. The mark is decoration: each fact is read as its own sentence, so a fact must be one.
 * - An optional [link] ends the card, 8 under the last fact, a [TextLink] with its 48 target
 *   (an underline goes somewhere, tokens.md §1). It is the only thing in the card that acts.
 *
 * The facts are the screen's own string resources: the card writes no words of its own.
 */
@Composable
fun FactsCard(
    label: String,
    facts: List<String>,
    modifier: Modifier = Modifier,
    link: FactsCardLink? = null,
) {
    val colors = MoyiTheme.colors
    val shapes = MoyiTheme.shapes
    val spacing = MoyiTheme.spacing
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surfaceRaised, shapes.radius)
            .border(MoyiDimens.EntryCard.Stroke, colors.outline, shapes.radius)
            .padding(MoyiDimens.EntryCard.Padding),
    ) {
        MoyiText(text = label, role = TextRole.Label, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(MoyiDimens.FactsCard.LabelGap))
        Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            facts.forEach { fact -> FactLine(fact) }
        }
        if (link != null) {
            Spacer(Modifier.height(spacing.sm))
            TextLink(text = link.text, onClick = link.onClick)
        }
    }
}

/** One fact: the mark, centred on the first line (whatever the font scale), then the sentence. */
@Composable
private fun FactLine(fact: String) {
    val style = MoyiTheme.type.styleFor(TextRole.Body)
    val firstLine = with(LocalDensity.current) {
        (if (style.lineHeight.isEm) style.fontSize * style.lineHeight.value else style.lineHeight).toDp()
    }
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.height(firstLine), contentAlignment = Alignment.Center) {
            Box(Modifier.size(MoyiDimens.FactsCard.Mark).background(MoyiTheme.colors.inkMuted, CircleShape))
        }
        Spacer(Modifier.width(MoyiDimens.FactsCard.MarkGap))
        MoyiText(text = fact, tone = TextTone.Muted, modifier = Modifier.weight(1f))
    }
}

@Preview
@Composable
private fun FactsCardDarkPreview() = PreviewSurface(darkTheme = true) { FactsCardSpecimen() }

@Preview
@Composable
private fun FactsCardLightPreview() = PreviewSurface(darkTheme = false) { FactsCardSpecimen() }

@Composable
private fun FactsCardSpecimen() {
    FactsCard(
        label = "Good to know",
        facts = listOf(
            "Your words go to a search service. Photos and voice notes do not.",
            "Tunde's words stay hidden until your day opens.",
            "Either of you can turn it off.",
        ),
        link = FactsCardLink("Read the Privacy Policy", onClick = {}),
    )
}
