package com.moyi.app.designsystem.component

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.moyi.app.designsystem.MoyiTheme
import com.moyi.app.designsystem.platform.platformPressIndication
import com.moyi.app.designsystem.theme.MoyiColors
import com.moyi.app.designsystem.theme.MoyiDimens

/**
 * An outlined secondary action: a bordered button for an action that changes
 * something here, such as "Edit what you wrote" and "Delete what you wrote" on
 * Today.
 *
 * **Owner ruling 2026-11-06 (PR 30 smoke test).** Drawn as [TextLink], these
 * read as plain underlined text, not as actions. The ruling: an outlined
 * button, label and border in `ink`, or in `danger` when [destructive]. No
 * accent colour (Rosewood removed accent on 2026-09-03). The design system
 * documents in the parent repository (`tokens.md`, `states.md`) do not list
 * this component yet; the owner updates them.
 *
 * - No fill: the surface shows through. The border and the shape are the cue
 *   that it is a control, so colour is never the only cue (NFR-104).
 * - `body` in the words as written, centred, like [TextLink] (D14): what is
 *   drawn is what is spoken.
 * - At least 48 tall and 48 wide (NFR-101); pass `Modifier.fillMaxWidth()` for
 *   the full-width placement. A longer label or a 200 % font grows the
 *   control, never clips it.
 * - Disabled: label and border in `inkMuted`, not tappable.
 * - [TextLink] stays for real navigation links: an underline means it goes
 *   somewhere.
 */
@Composable
fun SecondaryAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** `danger` label and border, for an action that deletes ("Delete what you wrote"). */
    destructive: Boolean = false,
) {
    val shapes = MoyiTheme.shapes
    val color = MoyiTheme.colors.secondaryActionColor(enabled, destructive)
    Box(
        modifier = modifier
            .widthIn(min = MoyiDimens.SecondaryAction.MinTarget)
            .heightIn(min = MoyiDimens.SecondaryAction.MinTarget)
            .clip(shapes.radius)
            // One source of click semantics: the role, the action and the
            // disabled state, with the text below merged into this node.
            .clickable(
                enabled = enabled,
                role = Role.Button,
                interactionSource = remember { MutableInteractionSource() },
                indication = platformPressIndication(color = color),
                onClick = onClick,
            )
            .border(MoyiDimens.SecondaryAction.Stroke, color, shapes.radius)
            .padding(
                horizontal = MoyiDimens.SecondaryAction.PaddingHorizontal,
                vertical = MoyiDimens.SecondaryAction.PaddingVertical,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MoyiTheme.type.body,
            color = color,
            textAlign = TextAlign.Center,
        )
    }
}

/** The one colour of a [SecondaryAction], used for both label and border. Pure, so a test can read it. */
fun MoyiColors.secondaryActionColor(enabled: Boolean, destructive: Boolean): Color = when {
    !enabled -> inkMuted
    destructive -> danger
    else -> ink
}

@Preview
@Composable
private fun SecondaryActionDarkPreview() = PreviewSurface(darkTheme = true) { SecondaryActionSamples() }

@Preview
@Composable
private fun SecondaryActionLightPreview() = PreviewSurface(darkTheme = false) { SecondaryActionSamples() }

@Preview(widthDp = 390)
@Composable
private fun SecondaryActionFontScale200DarkPreview() =
    PreviewSurface(darkTheme = true, fontScale = 2f) { SecondaryActionSamples() }

@Preview(widthDp = 390)
@Composable
private fun SecondaryActionFontScale200LightPreview() =
    PreviewSurface(darkTheme = false, fontScale = 2f) { SecondaryActionSamples() }

@Composable
private fun SecondaryActionSamples() {
    Column {
        SecondaryAction("Edit what you wrote", onClick = {}, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(MoyiTheme.spacing.sm))
        SecondaryAction("Delete what you wrote", onClick = {}, destructive = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(MoyiTheme.spacing.sm))
        SecondaryAction("Delete what you wrote", onClick = {}, destructive = true, enabled = false, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(MoyiTheme.spacing.sm))
        SecondaryAction("Edit", onClick = {})
    }
}
