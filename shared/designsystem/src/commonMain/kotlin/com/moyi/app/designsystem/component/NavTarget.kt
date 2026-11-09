package com.moyi.app.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import com.moyi.app.designsystem.MoyiTheme
import com.moyi.app.designsystem.icon.MoyiIcons
import com.moyi.app.designsystem.theme.MoyiDimens

/**
 * One glyph that goes somewhere: a 48 x 48 target ([MoyiDimens.TouchTarget]) holding a 24
 * Lucide glyph in `ink`, announced as a button by [contentDescription]. Today's `nav` row
 * draws two (Figma 159:2: Archive and Bond settings); it is the same target here, made
 * shareable so the account target can use it (docs/missing-components.md §57).
 */
@Composable
fun NavTarget(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(MoyiDimens.TouchTarget)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = MoyiTheme.colors.ink, modifier = Modifier.size(MoyiDimens.IconGlyph))
    }
}

/**
 * **PROPOSED** (docs/missing-components.md §57; owner ruling 2026-11-09): a root screen's top
 * row holding one [NavTarget] at the end, for [MoyiScaffold]'s `header` slot. The design
 * system has no top bar ("no persistent chrome"); this is Today's `nav` row (48 tall, targets
 * at the end) with nothing at the start. Used for the account target ([MoyiIcons.User]) on
 * the screens a person with no open bond lands on.
 */
@Composable
fun TrailingNavRow(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(MoyiDimens.NavRow.Height),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavTarget(icon, contentDescription, onClick)
    }
}

@Preview
@Composable
private fun TrailingNavRowPreview() {
    MoyiTheme(darkTheme = true) {
        MoyiScaffold(header = { TrailingNavRow(MoyiIcons.User, "Your account", onClick = {}) }) {
            ScreenHeader(eyebrow = "Account", heading = "Check your email")
        }
    }
}
