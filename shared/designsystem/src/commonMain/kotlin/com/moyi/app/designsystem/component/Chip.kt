package com.moyi.app.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import com.moyi.app.designsystem.MoyiTheme
import com.moyi.app.designsystem.theme.MoyiDimens

/**
 * `Chip` (`tokens.md` §4 row 9): one of the Archive's filters, `Idle` or `Selected`.
 * A **48 pt target wrapping a 40 pt visible pill**, so the pill stays compact and the
 * target clears NFR-101.
 *
 * - **Idle**: no fill, an `outlineStrong` border (never `outline`: the border is the
 *   only thing identifying the control, WCAG 1.4.11), the label in `ink`.
 * - **Selected**: `ink` fill and border, the label in `surface` (measured 16.26 / 15.18).
 *
 * The states differ by fill, border and label colour, so NFR-104 holds in greyscale.
 * The label is set in `label` (uppercase, from the words as written): a chip names a
 * set, like an eyebrow, rather than being a link (D14 covers links only). Spoken as
 * written, with its selected state.
 *
 * [role] is a filter's [Role.Tab] unless the caller's set is something else (the interim
 * reaction set, [ReactionChoices], uses [Role.RadioButton]). [onClickLabel], when given, is
 * what a screen reader says a double tap will do.
 *
 * [leading], when given, is drawn before the words in `body` size (an emoji, for the
 * reaction set: owner ruling 2026-11-09) and is **never spoken**: the words are the label.
 * It is set in the system face, so the platform's colour emoji font draws it.
 */
@Composable
fun Chip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    role: Role = Role.Tab,
    onClickLabel: String? = null,
    leading: String? = null,
) {
    val colors = MoyiTheme.colors
    val pill = MoyiTheme.shapes.full
    Box(
        modifier = modifier
            .heightIn(min = MoyiDimens.Chip.Target)
            .selectable(selected = selected, role = role, onClick = onClick)
            .then(
                if (onClickLabel == null) {
                    Modifier
                } else {
                    Modifier.semantics {
                        onClick(label = onClickLabel) {
                            onClick()
                            true
                        }
                    }
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .height(MoyiDimens.Chip.PillHeight)
                .background(if (selected) colors.ink else colors.surface, pill)
                .border(MoyiDimens.Chip.Stroke, if (selected) colors.ink else colors.outlineStrong, pill)
                .padding(horizontal = MoyiDimens.Chip.PaddingHorizontal),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(MoyiTheme.spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (leading != null) {
                    Text(
                        text = leading,
                        modifier = Modifier.clearAndSetSemantics { },
                        style = MoyiTheme.type.body,
                        maxLines = 1,
                    )
                }
                Text(
                    text = text,
                    modifier = Modifier.clearAndSetSemantics { this.text = AnnotatedString(text) },
                    style = MoyiTheme.type.tag,
                    color = if (selected) colors.surface else colors.ink,
                    maxLines = 1,
                )
            }
        }
    }
}

/** A row of [Chip]s, [MoyiDimens.Chip.Gap] apart, as Figma's filters row (440:1475). */
@Composable
fun ChipRow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(MoyiDimens.Chip.Gap),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Preview
@Composable
private fun ChipDarkPreview() = PreviewSurface(darkTheme = true) { ChipSamples() }

@Preview
@Composable
private fun ChipLightPreview() = PreviewSurface(darkTheme = false) { ChipSamples() }

@Composable
private fun ChipSamples() {
    ChipRow {
        Chip("All days", selected = true, onClick = {})
        Chip("Favourites", selected = false, onClick = {})
    }
}
