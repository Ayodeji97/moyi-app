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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import com.moyi.app.designsystem.MoyiTheme
import com.moyi.app.designsystem.theme.MoyiDimens

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
 *
 * **One text setting: `body`, in the words as written** (sentence case,
 * owner ruling 2026-10-30, `docs/figma/figma-deviations.md` D14). Figma
 * draws secondary links in `label` metrics, uppercase; the app does not.
 * There is deliberately no style parameter, so a link cannot drift back to
 * uppercase: what is drawn is what is spoken.
 */
@Composable
fun TextLink(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    centered: Boolean = false,
    enabled: Boolean = true,
    /**
     * `danger` instead of `ink`, for a link that starts a destructive flow ("Delete
     * what you wrote", states.md §6 2026-09-07). The underline stays, so colour is
     * never the only cue (NFR-104).
     */
    destructive: Boolean = false,
) {
    val target = MoyiDimens.TextLink.MinTarget
    Box(
        modifier = modifier
            .heightIn(min = target)
            .widthIn(min = target)
            // One source of click semantics: `clickable` gives the role, the
            // action and the disabled state, and merges the text below into
            // this node.
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = if (centered) Alignment.Center else Alignment.CenterStart,
    ) {
        Text(
            text = text,
            style = MoyiTheme.type.body.copy(textDecoration = TextDecoration.Underline),
            color = when {
                !enabled -> MoyiTheme.colors.inkMuted
                destructive -> MoyiTheme.colors.danger
                else -> MoyiTheme.colors.ink
            },
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
        TextLink("Privacy Policy", onClick = {})
        TextLink("Delete what you wrote", onClick = {}, destructive = true, modifier = Modifier.fillMaxWidth())
    }
}
