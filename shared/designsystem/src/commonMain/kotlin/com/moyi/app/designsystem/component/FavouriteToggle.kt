package com.moyi.app.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.moyi.app.designsystem.MoyiTheme
import com.moyi.app.designsystem.icon.MoyiIcons
import com.moyi.app.designsystem.resources.Res
import com.moyi.app.designsystem.resources.ds_favourite_add
import com.moyi.app.designsystem.resources.ds_favourite_remove
import com.moyi.app.designsystem.theme.MoyiDimens
import org.jetbrains.compose.resources.stringResource

/**
 * `FavouriteToggle` (`tokens.md` §4 row 10, `states.md` §6): the mark *I* put on one
 * entry in the Archive day view. Two states, changed **in place** by a tap, with no
 * confirmation and no undo toast:
 *
 * - **Idle** ([marked] false): the outline bookmark and "Add to favourites".
 * - **Marked**: the filled bookmark and "Remove from favourites".
 *
 * The states differ by **shape** and words, never colour (NFR-104). The glyph is `ink`
 * (the measured pairing "ink on surface (bookmark glyph)"), the label `inkMuted`. The
 * label says what a tap will do; the glyph says what is. It is the control's spoken
 * name, and the row is a polite live region, so the change is announced.
 *
 * Not a [TextLink]: it toggles in place, so it takes no underline (TextLink's rule).
 * The label is `body`, in the words as written (sentence case, as D14 has every action).
 *
 * Whose mark it is, is the caller's business: this draws one boolean. **Only ever pass
 * the signed-in person's own mark**; the partner's favourites are never shown (§6).
 */
@Composable
fun FavouriteToggle(
    marked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(if (marked) Res.string.ds_favourite_remove else Res.string.ds_favourite_add)
    Row(
        modifier = modifier
            .heightIn(min = MoyiDimens.FavouriteToggle.Target)
            .clickable(role = Role.Button, onClick = onToggle)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(MoyiDimens.FavouriteToggle.Gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (marked) MoyiIcons.BookmarkFilled else MoyiIcons.Bookmark,
            contentDescription = null,
            tint = MoyiTheme.colors.ink,
            modifier = Modifier.size(MoyiDimens.IconGlyph),
        )
        MoyiText(label, tone = TextTone.Muted)
    }
}

@Preview
@Composable
private fun FavouriteToggleDarkPreview() = PreviewSurface(darkTheme = true) { FavouriteToggleStates() }

@Preview
@Composable
private fun FavouriteToggleLightPreview() = PreviewSurface(darkTheme = false) { FavouriteToggleStates() }

@Composable
private fun FavouriteToggleStates() {
    Column {
        FavouriteToggle(marked = false, onToggle = {})
        FavouriteToggle(marked = true, onToggle = {})
    }
}
