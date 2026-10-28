package com.moyi.app.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.moyi.app.designsystem.MoyiTheme
import com.moyi.app.designsystem.icon.MoyiIcons
import com.moyi.app.designsystem.resources.Res
import com.moyi.app.designsystem.resources.ds_invite_code_caption
import com.moyi.app.designsystem.resources.ds_invite_copied
import com.moyi.app.designsystem.resources.ds_invite_copy
import com.moyi.app.designsystem.theme.MoyiDimens
import org.jetbrains.compose.resources.stringResource

/**
 * The invite code shown to the creator until the partner accepts
 * (`tokens.md` §4, component 7). Placed as Figma's 342:53 instance, 358×195;
 * the inner layout is interpreted (see [MoyiDimens.InviteCode]).
 *
 * - **One text, not six boxes**, in the `code` style (Newsreader 26, tracking
 *   28 %), so it reads character by character, and a screen reader is given
 *   the characters spaced out so it spells them instead of guessing a word.
 * - Two states, **Idle** and **Copied**, that differ by content, not styling:
 *   the copy control's glyph and words change. Copied is not optional: a copy
 *   with no visible result gets tapped again. The change is announced politely.
 * - The copy control toggles in place, so it takes **no underline** (an
 *   underline means "goes somewhere", see [TextLink]).
 * - Raised surface: `surfaceRaised` with an `outline` hairline, radius 12, no shadow.
 */
@Composable
fun InviteCode(
    code: String,
    copied: Boolean,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MoyiTheme.colors
    val shapes = MoyiTheme.shapes
    val spelled = code.toList().joinToString(" ")
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shapes.radius)
            .background(colors.surfaceRaised)
            .border(shapes.hairline, colors.outline, shapes.radius)
            .padding(MoyiDimens.InviteCode.Padding),
    ) {
        MoyiText(stringResource(Res.string.ds_invite_code_caption), role = TextRole.Label)
        Spacer(Modifier.height(MoyiDimens.InviteCode.Gap))
        MoyiText(
            text = code,
            role = TextRole.Code,
            modifier = Modifier.clearAndSetSemantics { contentDescription = spelled },
        )
        Spacer(Modifier.height(MoyiDimens.InviteCode.Gap))
        val copyLabel = stringResource(if (copied) Res.string.ds_invite_copied else Res.string.ds_invite_copy)
        Row(
            modifier = Modifier
                .heightIn(min = MoyiDimens.InviteCode.CopyTarget)
                .clickable(role = Role.Button, onClick = onCopy)
                .semantics { liveRegion = LiveRegionMode.Polite },
            horizontalArrangement = Arrangement.spacedBy(MoyiDimens.InviteCode.CopyGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (copied) MoyiIcons.Check else MoyiIcons.Copy,
                contentDescription = null,
                tint = colors.ink,
                modifier = Modifier.size(MoyiDimens.IconGlyph),
            )
            MoyiText(copyLabel, role = TextRole.Label, tone = TextTone.Ink)
        }
    }
}

@Preview
@Composable
private fun InviteCodeDarkPreview() = PreviewSurface(darkTheme = true) { InviteCodeStates() }

@Preview
@Composable
private fun InviteCodeLightPreview() = PreviewSurface(darkTheme = false) { InviteCodeStates() }

@Composable
private fun InviteCodeStates() {
    Column {
        InviteCode(code = "K7Q2PD", copied = false, onCopy = {})
        Spacer(Modifier.height(MoyiTheme.spacing.lg))
        InviteCode(code = "K7Q2PD", copied = true, onCopy = {})
    }
}
