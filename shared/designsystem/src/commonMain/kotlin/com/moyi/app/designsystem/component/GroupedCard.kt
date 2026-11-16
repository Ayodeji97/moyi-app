package com.moyi.app.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonSkippableComposable
import androidx.compose.runtime.currentRecomposeScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.moyi.app.designsystem.MoyiTheme
import com.moyi.app.designsystem.icon.MoyiIcons
import com.moyi.app.designsystem.theme.MoyiDimens

/**
 * What a [GroupedCard] lets its caller write: a list of items, each one composable.
 * The card, not the caller, draws the hairline between two items, so a conditional item
 * (`if (partnerName != null) row(...)`) never leaves a stray divider.
 */
interface GroupedCardScope {
    /** Any content as one item of the card. */
    fun item(content: @Composable () -> Unit)

    /** One [GroupedRow] as one item. */
    fun row(
        label: String,
        modifier: Modifier = Modifier,
        value: String? = null,
        onClick: (() -> Unit)? = null,
        showChevron: Boolean = onClick != null,
        destructive: Boolean = false,
        enabled: Boolean = true,
        error: String? = null,
        onClickLabel: String? = null,
    ) = item {
        GroupedRow(label, modifier, value, onClick, showChevron, destructive, enabled, error, onClickLabel)
    }
}

/**
 * A rounded, raised card holding rows separated by `outline` hairlines: the one shape
 * Bond settings, Account and Notifications share. `surfaceRaised` with a hairline
 * `outline` border and the 12 radius (`tokens.md` §3: elevation is a border, not a shadow).
 *
 * A slot API: the caller describes the items in [content], a composable block (so it can
 * read string resources and state directly); the card lays them out and puts a divider
 * between neighbours. See [GroupedRow] for the row it is made for.
 */
// Why it is never skipped, and why a stray item() call recomposes the card: [content] is
// collected into `items` here and drawn below, in this function's own pass. Compose gives
// [content] a recompose scope of its own, so when the caller recomposes with new values,
// a skippable card was skipped (the same remembered lambda came in) while [content] re-ran
// alone, adding fresh rows to a list nobody draws again: the screen kept the old ones
// (Bond settings' reveal time after a save, the iOS smoke test of 2026-11-16).
// - Never skipped: whenever the caller runs, the card collects and draws again.
// - An item() outside the card's own pass (content re-ran alone, e.g. after reading a
//   State) asks for the card's pass, which draws what content now says.
@NonSkippableComposable
@Composable
fun GroupedCard(modifier: Modifier = Modifier, content: @Composable GroupedCardScope.() -> Unit) {
    val items = mutableListOf<@Composable () -> Unit>()
    val card = currentRecomposeScope
    var collecting = true
    object : GroupedCardScope {
        override fun item(content: @Composable () -> Unit) {
            if (collecting) items += content else card.invalidate()
        }
    }.content()
    collecting = false
    val colors = MoyiTheme.colors
    val shape = MoyiTheme.shapes.radius
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surfaceRaised, shape)
            .border(MoyiTheme.shapes.hairline, colors.outline, shape)
            .padding(horizontal = MoyiTheme.spacing.md),
    ) {
        items.forEachIndexed { index, item ->
            if (index > 0) Box(Modifier.fillMaxWidth().height(MoyiTheme.shapes.hairline).background(colors.outline))
            item()
        }
    }
}

/**
 * One row of a [GroupedCard]: [label] at the start, the optional [value] at the end, and
 * a chevron when the row opens another screen. Rules (owner ruling 2026-11-11):
 *
 * - **Chevron** ([showChevron], on by default whenever there is an [onClick]): the row
 *   opens another screen.
 * - **No mark** on a row that only shows a value (no [onClick]), or one that acts in place.
 * - **No underline**: an underline stays only for a link inside a sentence.
 *
 * The row is one element to assistive technology: label, value, and Button role when it
 * can be pressed ([onClickLabel] says what a press does when the label alone does not).
 * [destructive] is `danger`; the chevron and the position in a card marked Ending carry
 * the rest. [error] sits under the row, input kept.
 */
@Composable
fun GroupedRow(
    label: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    showChevron: Boolean = onClick != null,
    destructive: Boolean = false,
    enabled: Boolean = true,
    error: String? = null,
    onClickLabel: String? = null,
) {
    GroupedRowLayout(
        label = label,
        value = value,
        showChevron = showChevron,
        destructive = destructive,
        error = error,
        modifier = if (onClick != null) {
            modifier.clickable(enabled = enabled, onClickLabel = onClickLabel, role = Role.Button, onClick = onClick)
        } else {
            modifier.semantics(mergeDescendants = true) {}
        },
    )
}

/**
 * A [GroupedRow] that switches in place (Rest days): a switch to assistive technology,
 * its state spoken as the [value] says it, and no chevron.
 */
@Composable
fun GroupedToggleRow(
    label: String,
    value: String,
    checked: Boolean,
    onToggle: () -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    error: String? = null,
) {
    GroupedRowLayout(
        label = label,
        value = value,
        showChevron = false,
        destructive = false,
        error = error,
        modifier = modifier
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = { onToggle() })
            .semantics {
                stateDescription = value
                onClick(label = hint) {
                    onToggle()
                    true
                }
            },
    )
}

@Composable
private fun GroupedRowLayout(
    label: String,
    value: String?,
    showChevron: Boolean,
    destructive: Boolean,
    error: String?,
    modifier: Modifier,
) {
    val colors = MoyiTheme.colors
    val body = MoyiTheme.type.body
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = MoyiDimens.TouchTarget)
                .padding(vertical = MoyiTheme.spacing.md),
            horizontalArrangement = Arrangement.spacedBy(MoyiTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = body,
                color = if (destructive) colors.danger else colors.ink,
            )
            if (value != null) {
                Text(value, style = body, color = colors.ink, textAlign = TextAlign.End)
            }
            if (showChevron) {
                Icon(
                    imageVector = MoyiIcons.ChevronRight,
                    contentDescription = null,
                    tint = if (destructive) colors.danger else colors.inkMuted,
                    modifier = Modifier.size(MoyiDimens.IconGlyph),
                )
            }
        }
        if (error != null) {
            InlineError(error)
            Spacer(Modifier.height(MoyiTheme.spacing.sm))
        }
    }
}

@Preview
@Composable
private fun GroupedCardDarkPreview() = GroupedCardSpecimen(darkTheme = true)

@Preview
@Composable
private fun GroupedCardLightPreview() = GroupedCardSpecimen(darkTheme = false)

@Composable
private fun GroupedCardSpecimen(darkTheme: Boolean) {
    MoyiTheme(darkTheme = darkTheme) {
        Column(Modifier.background(MoyiTheme.colors.surface).padding(MoyiTheme.spacing.md)) {
            GroupedCard {
                row("Relationship type", value = "My partner", onClick = {})
                row("Reveal time", value = "21:00")
                row("Leave this bond", onClick = {}, destructive = true)
            }
        }
    }
}
