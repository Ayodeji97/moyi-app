package com.moyi.app.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import com.moyi.app.designsystem.MoyiTheme

/**
 * A secondary action: `ink` with an underline (`tokens.md` §1).
 *
 * There is no link colour. **An underline means it goes somewhere**, so use
 * this only for something that navigates or starts a flow ("I forgot my
 * password", "Use a different address"). A control that toggles in place or
 * opens an OS surface takes no underline and is not a `TextLink`.
 *
 * The touch target is at least 48 x 48 even when the words are short.
 */
@Composable
fun TextLink(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val target = MoyiTheme.spacing.touchTarget
    Box(
        modifier = modifier
            .heightIn(min = target)
            .widthIn(min = target)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = text,
            style = MoyiTheme.type.body.copy(textDecoration = TextDecoration.Underline),
            color = if (enabled) MoyiTheme.colors.ink else MoyiTheme.colors.inkMuted,
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
        TextLink("I forgot my password", onClick = {})
        TextLink("Use a different address", onClick = {})
    }
}
