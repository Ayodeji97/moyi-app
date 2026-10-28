package com.moyi.app.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.moyi.app.designsystem.MoyiTheme
import com.moyi.app.designsystem.theme.MoyiDimens

/**
 * A state that replaces content: a heading, one line and **at most one
 * action** (`tokens.md` §4, component 5). It carries `copy.md`'s error shape
 * (what happened, what to do next); the screen writes the words.
 *
 * - The action is a [TextLink]: `ink` with an underline, never colour alone.
 * - Native width 350, not stretched to the 358 content width (Figma 310:26).
 * - No fill and no border: it is a state of the screen, not a card. The
 *   component page (72:2) was not read; this and the padding are interpreted
 *   ([MoyiDimens.StateBlock]).
 *
 * Six variants exist in Figma (Empty, Waiting, Error, Offline, Solo,
 * PartnerPending). They differ by copy and, for Waiting, a second action;
 * this builds the one-action shape, which is all onboarding uses. Waiting's
 * second action is added with the screen that needs it.
 */
@Composable
fun StateBlock(
    heading: String,
    line: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .widthIn(max = MoyiDimens.StateBlock.NativeWidth)
            .padding(MoyiDimens.StateBlock.Padding),
    ) {
        MoyiText(heading, role = TextRole.Title, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(MoyiDimens.StateBlock.Gap))
        MoyiText(line, tone = TextTone.Muted)
        if (actionLabel != null) {
            Spacer(Modifier.height(MoyiDimens.StateBlock.Gap))
            TextLink(actionLabel, onClick = onAction)
        }
    }
}

@Preview
@Composable
private fun StateBlockDarkPreview() = PreviewSurface(darkTheme = true) { StateBlockSample() }

@Preview
@Composable
private fun StateBlockLightPreview() = PreviewSurface(darkTheme = false) { StateBlockSample() }

@Composable
private fun StateBlockSample() {
    StateBlock(
        heading = "That code isn't working.",
        line = "Ask them to send you a new one.",
        actionLabel = "Enter a different code",
    )
}
