package com.moyi.app.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.moyi.app.designsystem.MoyiTheme

/*
 * NFR-102: 200 % font scale without truncation or overlap. The failure that
 * most often ships, so it gets its own previews. Look for: the Field label
 * and helper wrapping rather than clipping, the box growing past 56, and the
 * PrimaryAction growing past 48 tall instead of cutting its label.
 *
 * A preview is a first look, not the test: tokens.md says verify 200 % on a
 * device, and the gallery in :shared does that.
 */

@Preview(widthDp = 390)
@Composable
private fun FontScale200DarkPreview() = FontScale200(darkTheme = true)

@Preview(widthDp = 390)
@Composable
private fun FontScale200LightPreview() = FontScale200(darkTheme = false)

@Composable
private fun FontScale200(darkTheme: Boolean) {
    PreviewSurface(darkTheme = darkTheme, fontScale = 2f) {
        Column {
            Field(
                value = "amara@example.com",
                onValueChange = {},
                label = "Email",
                helper = "We send one link to confirm it.",
            )
            Spacer(Modifier.height(MoyiTheme.spacing.lg))
            Field(
                value = "hunter22",
                onValueChange = {},
                label = "Password",
                type = FieldType.Secure,
                error = "This password shows up on a list of leaked passwords. Pick a different one.",
            )
            Spacer(Modifier.height(MoyiTheme.spacing.lg))
            PrimaryAction("Create account", onClick = {})
            Spacer(Modifier.height(MoyiTheme.spacing.md))
            PrimaryAction("Check again", onClick = {}, state = PrimaryActionState.Sending("Checking"))
        }
    }
}
