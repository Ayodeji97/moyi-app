package com.moyi.app.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.DpOffset
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.moyi.app.designsystem.MoyiTheme
import com.moyi.app.designsystem.icon.MoyiIcons
import com.moyi.app.designsystem.theme.MoyiDimens

/**
 * One row of a [MoreMenu]. [destructive] draws it in `danger` (the label still says what it
 * does, so colour is never the only cue, NFR-104). A disabled row is drawn but cannot be
 * chosen (a delete already in flight).
 */
data class MoreMenuItem(
    val label: String,
    val onClick: () -> Unit,
    val destructive: Boolean = false,
    val enabled: Boolean = true,
)

/**
 * **PROPOSED** (docs/missing-components.md §75; owner approved a menu on 2026-11-11): a quiet
 * "More" control and the small menu it opens. The control is a 48 x 48 target
 * ([MoyiDimens.TouchTarget]) holding the three-dot glyph in `inkMuted`, spoken as a button by
 * [contentDescription]. The menu is a `surfaceRaised` panel with an `outline` hairline, the
 * `radius` corners and no shadow (tokens.md §3), its rows 48 tall in the `body` style, `ink`
 * or `danger`, each announced as a button.
 *
 * Choosing a row closes the menu first, then calls its [MoreMenuItem.onClick]; a tap outside,
 * or Back on Android, closes it and does nothing. With no [items] nothing is drawn, so a
 * caller never shows a "More" that opens an empty panel.
 *
 * The open/closed state is the control's own (it survives rotation); what a row does is
 * the caller's, so anything irreversible still asks first in its own dialog.
 */
@Composable
fun MoreMenu(
    items: List<MoreMenuItem>,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    val colors = MoyiTheme.colors
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box(modifier) {
        Box(
            modifier = Modifier
                .size(MoyiDimens.TouchTarget)
                .clickable(role = Role.Button, onClick = { expanded = true }),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                MoyiIcons.MoreHorizontal,
                contentDescription = contentDescription,
                tint = colors.inkMuted,
                modifier = Modifier.size(MoyiDimens.IconGlyph),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = MoyiTheme.shapes.radius,
            containerColor = colors.surfaceRaised,
            tonalElevation = MoyiDimens.MoreMenu.Elevation,
            shadowElevation = MoyiDimens.MoreMenu.Elevation,
            border = BorderStroke(MoyiDimens.MoreMenu.Stroke, colors.outline),
            // Anchored to the glyph: the panel's right edge lines up with the 48 target's, and it
            // opens a little below it, so it reads as hanging from the dots instead of
            // starting over the entry text (the default aligns the panel's START with the target).
            offset = DpOffset(
                x = MoyiDimens.TouchTarget - MoyiDimens.MoreMenu.Width,
                y = MoyiDimens.MoreMenu.GapBelow,
            ),
            modifier = Modifier.width(MoyiDimens.MoreMenu.Width),
        ) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = {
                        MoyiText(
                            text = item.label,
                            tone = if (item.destructive) TextTone.Danger else TextTone.Ink,
                        )
                    },
                    onClick = {
                        expanded = false
                        item.onClick()
                    },
                    enabled = item.enabled,
                    modifier = Modifier
                        .heightIn(min = MoyiDimens.TouchTarget)
                        .semantics { role = Role.Button },
                )
            }
        }
    }
}

@Preview
@Composable
private fun MoreMenuPreview() {
    MoyiTheme(darkTheme = false) {
        MoreMenu(
            items = listOf(MoreMenuItem("Edit", {}), MoreMenuItem("Delete", {}, destructive = true)),
            contentDescription = "More options for your entry",
        )
    }
}
