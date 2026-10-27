package com.moyi.app.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.moyi.app.designsystem.MoyiTheme

/**
 * A form-level error placed **between the content and the action**
 * (`states.md` §1c "No connection on submit", §10 "Save failed": "the error
 * sits between the composer and the action, in `danger`").
 *
 * It never replaces the form: every failure keeps what the person typed.
 * The text follows `copy.md`'s error shape (what happened, what is kept,
 * what to do next) and is written by the screen, not here.
 *
 * Placed as Figma's no-connection frame draws it
 * (`docs/figma/auth-placement-spec.md` §3.1): a full-width, **centred**
 * `body` line in `danger`, one action-gap above the `PrimaryAction`, inside
 * the scaffold's action block, so the button does not move.
 *
 * Announced politely when it appears, so a screen-reader user who just
 * tapped the action hears why nothing moved on. Words carry the meaning;
 * `danger` is the second cue, never the only one.
 */
@Composable
fun InlineError(text: String, modifier: Modifier = Modifier) {
    MoyiText(
        text = text,
        tone = TextTone.Danger,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Preview
@Composable
private fun InlineErrorDarkPreview() = PreviewSurface(darkTheme = true) { InlineErrorSample() }

@Preview
@Composable
private fun InlineErrorLightPreview() = PreviewSurface(darkTheme = false) { InlineErrorSample() }

@Composable
private fun InlineErrorSample() {
    Column {
        InlineError("That didn't go through. Your details are still here.")
        Spacer(Modifier.height(MoyiTheme.spacing.md))
        PrimaryAction("Try again", onClick = {})
    }
}
