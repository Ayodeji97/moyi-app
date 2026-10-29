package com.moyi.app.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import com.moyi.app.designsystem.MoyiTheme
import com.moyi.app.designsystem.icon.MoyiIcons
import com.moyi.app.designsystem.resources.Res
import com.moyi.app.designsystem.resources.ds_compose_add_photo
import com.moyi.app.designsystem.resources.ds_compose_dismiss_prompt
import com.moyi.app.designsystem.resources.ds_compose_record_voice
import com.moyi.app.designsystem.theme.MoyiDimens
import org.jetbrains.compose.resources.stringResource

/**
 * The composer (`tokens.md` §4, component 2; Figma page 78:2, instance 358 × 360).
 *
 * - **One bordered box holds everything**: the dismissible [prompt] at the top, the
 *   entry text, and along the bottom edge the media affordances and the counter
 *   (tokens.md §6, "the composer toolbar lives inside the field").
 * - **No send button.** Send is the screen's `PrimaryAction`, outside the box.
 * - The text is the `entry` style (Newsreader 34 / 1.40), the same face and size the
 *   words will have on the card, so what you write is what they will read.
 * - **Spacing is geometry, not taste**: the box pads 8 and every slot wraps 12, so
 *   text and the 24 glyphs centred in 48 targets all align at 20 from the edge
 *   (tokens.md §3, `MoyiDimens.ComposeField`). Do not pad one without the other.
 * - The [counter] is the caller's text and is shown only when non-null: the screen
 *   passes it past 400 graphemes (states.md §4). It is `inkMuted` until [invalid]
 *   (over 500), then `danger`, and the box stroke thickens to 2 so the state is not
 *   colour alone (NFR-104). Announced politely when it changes.
 * - Media buttons are icon-only 48 targets with spoken names (tokens.md §6). Text is
 *   always sufficient (NFR-103); a null callback hides the button.
 *
 * The box fills whatever height the caller gives it (at least
 * [MoyiDimens.ComposeField.MinHeight] by default) and the text scrolls inside it.
 */
@Composable
fun ComposeField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    prompt: String? = null,
    onDismissPrompt: (() -> Unit)? = null,
    counter: String? = null,
    invalid: Boolean = false,
    enabled: Boolean = true,
    onAddPhoto: (() -> Unit)? = null,
    onRecordVoice: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
) {
    val colors = MoyiTheme.colors
    val shapes = MoyiTheme.shapes
    val wrap = MoyiDimens.ComposeField.Wrap
    val strokeWidth = if (invalid) MoyiDimens.Field.StrokeEmphasis else MoyiDimens.ComposeField.Stroke
    val strokeColor = if (invalid) colors.danger else colors.outlineStrong

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MoyiDimens.ComposeField.MinHeight)
            .background(colors.surfaceRaised, shapes.radius)
            .border(strokeWidth, strokeColor, shapes.radius)
            .padding(MoyiDimens.ComposeField.BoxPadding),
    ) {
        if (prompt != null) {
            Row(verticalAlignment = Alignment.Top) {
                // The prompt is an offer, never a requirement (states.md §4).
                MoyiText(
                    text = prompt,
                    tone = TextTone.Muted,
                    modifier = Modifier.weight(1f).padding(wrap),
                )
                if (onDismissPrompt != null) {
                    IconTarget(MoyiIcons.Close, stringResource(Res.string.ds_compose_dismiss_prompt), enabled, onDismissPrompt)
                }
            }
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            textStyle = MoyiTheme.type.entry.copy(color = colors.ink),
            cursorBrush = SolidColor(colors.ink),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(wrap)
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onAddPhoto != null) {
                IconTarget(MoyiIcons.Image, stringResource(Res.string.ds_compose_add_photo), enabled, onAddPhoto)
            }
            if (onRecordVoice != null) {
                IconTarget(MoyiIcons.Mic, stringResource(Res.string.ds_compose_record_voice), enabled, onRecordVoice)
            }
            Spacer(Modifier.weight(1f))
            if (counter != null) {
                MoyiText(
                    text = counter,
                    tone = if (invalid) TextTone.Danger else TextTone.Muted,
                    modifier = Modifier
                        .padding(wrap)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }
}

@Composable
private fun IconTarget(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(MoyiDimens.ComposeField.IconTarget)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (enabled) MoyiTheme.colors.ink else MoyiTheme.colors.inkMuted,
            modifier = Modifier.size(MoyiDimens.IconGlyph),
        )
    }
}

@Preview
@Composable
private fun ComposeFieldDarkPreview() = PreviewSurface(darkTheme = true) { ComposeFieldSamples() }

@Preview
@Composable
private fun ComposeFieldLightPreview() = PreviewSurface(darkTheme = false) { ComposeFieldSamples() }

@Composable
private fun ComposeFieldSamples() {
    Column {
        ComposeField(
            value = "",
            onValueChange = {},
            prompt = "What did Tunde do today that you noticed?",
            onDismissPrompt = {},
            onAddPhoto = {},
            onRecordVoice = {},
        )
        Spacer(Modifier.size(MoyiTheme.spacing.md))
        ComposeField(
            value = "You stayed up to help me finish it.",
            onValueChange = {},
            counter = "512 / 500",
            invalid = true,
            onAddPhoto = {},
            onRecordVoice = {},
        )
    }
}
