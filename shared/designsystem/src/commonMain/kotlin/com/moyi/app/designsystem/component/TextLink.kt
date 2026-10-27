package com.moyi.app.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.toUpperCase
import androidx.compose.ui.tooling.preview.Preview
import com.moyi.app.designsystem.MoyiTheme
import com.moyi.app.designsystem.theme.MoyiDimens

/** The two text settings Figma uses for links (`docs/figma/auth-placement-spec.md` D2). */
enum class TextLinkStyle {
    /** Secondary actions ("I forgot my password"): `label` metrics, uppercase. The default. */
    Label,

    /** Links set in a body line, such as "Terms" and "Privacy Policy". */
    Body,
}

/**
 * A secondary action: `ink` with an underline (`tokens.md` §1).
 *
 * There is no link colour. **An underline means it goes somewhere**, so use
 * this only for something that navigates or starts a flow. A control that
 * toggles in place or opens an OS surface takes no underline and is not a
 * `TextLink`.
 *
 * Placement follows Figma §3: a secondary link is a full-width 48 target
 * (pass `Modifier.fillMaxWidth()`), with its text centred in the action
 * block ([centered]) or start-aligned under a form ("I forgot my
 * password"). The target is never smaller than 48 x 48.
 */
@Composable
fun TextLink(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: TextLinkStyle = TextLinkStyle.Label,
    centered: Boolean = false,
    enabled: Boolean = true,
) {
    val target = MoyiDimens.TextLink.MinTarget
    val type = MoyiTheme.type
    val isLabel = style == TextLinkStyle.Label
    Box(
        modifier = modifier
            .heightIn(min = target)
            .widthIn(min = target)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            // Spoken in its written casing, not letter by letter.
            .clearAndSetSemantics {
                this.text = AnnotatedString(text)
                role = Role.Button
                if (enabled) onClick { onClick(); true }
            },
        contentAlignment = if (centered) Alignment.Center else Alignment.CenterStart,
    ) {
        Text(
            text = if (isLabel) text.toUpperCase(Locale.current) else text,
            style = (if (isLabel) type.label else type.body).copy(textDecoration = TextDecoration.Underline),
            color = if (enabled) MoyiTheme.colors.ink else MoyiTheme.colors.inkMuted,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
        )
    }
}

@Preview
@Composable
private fun TextLinkDarkPreview() = PreviewSurface(darkTheme = true) { TextLinkSamples() }

@Preview
@Composable
private fun TextLinkLightPreview() = PreviewSurface(darkTheme = false) { TextLinkSamples() }

@Composable
private fun TextLinkSamples() {
    Column {
        TextLink("I forgot my password", onClick = {}, modifier = Modifier.fillMaxWidth())
        TextLink("Create an account", onClick = {}, centered = true, modifier = Modifier.fillMaxWidth())
        TextLink("Privacy Policy", onClick = {}, style = TextLinkStyle.Body)
    }
}
