package com.moyi.app.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import com.moyi.app.designsystem.MoyiTheme
import com.moyi.app.designsystem.theme.MoyiDimens
import com.moyi.app.designsystem.icon.MoyiIcons
import com.moyi.app.designsystem.resources.Res
import com.moyi.app.designsystem.resources.ds_back
import org.jetbrains.compose.resources.stringResource

/**
 * The screen frame (`tokens.md` §4, component 6): the `surface` ground,
 * the platform's safe areas, the side gutter, and an optional back.
 *
 * - Insets are [WindowInsets.safeDrawing]: status and navigation bars, the
 *   display cutout and the keyboard (IME). They are OS guarantees, not
 *   spacing tokens (`tokens.md` §3).
 * - The content scrolls. At 200 % font scale (NFR-102) almost every form is
 *   taller than the screen, and a form that cannot scroll truncates.
 * - [onBack] draws a 48 x 48 back with Lucide `chevron-left`, mirrored under
 *   RTL. Leave it null on root screens (Today, Onboarding 1, the
 *   unverified gate): `SKILL.md` §9 lists the screens that carry no back.
 *   System back (predictive back, edge swipe) is the platform's either way.
 * - No title bar, no navigation bar or rail: the product has no persistent
 *   chrome. A screen's heading is part of its content.
 */
@Composable
fun MoyiScaffold(
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MoyiTheme.colors.surface)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        if (onBack != null) {
            BackAffordance(onBack)
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = MoyiDimens.Scaffold.Gutter,
                    vertical = MoyiDimens.Scaffold.ContentPaddingVertical,
                ),
            content = content,
        )
    }
}

@Composable
private fun BackAffordance(onBack: () -> Unit) {
    Box(
        modifier = Modifier
            // The glyph sits on the gutter line; its 48 target reaches past it.
            .padding(start = MoyiDimens.Scaffold.Gutter)
            .offset(x = -(MoyiDimens.Scaffold.BackTarget - MoyiDimens.IconGlyph) / 2)
            .size(MoyiDimens.Scaffold.BackTarget)
            .clickable(role = Role.Button, onClick = onBack),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = MoyiIcons.ChevronLeft,
            contentDescription = stringResource(Res.string.ds_back),
            tint = MoyiTheme.colors.ink,
        )
    }
}


@Preview
@Composable
private fun MoyiScaffoldDarkPreview() = ScaffoldSample(darkTheme = true)

@Preview
@Composable
private fun MoyiScaffoldLightPreview() = ScaffoldSample(darkTheme = false)

@Composable
private fun ScaffoldSample(darkTheme: Boolean) {
    MoyiTheme(darkTheme = darkTheme) {
        MoyiScaffold(onBack = {}) {
            MoyiText("Check your email", role = TextRole.Title)
            MoyiText("We sent a link to amara@example.com. Open it to finish setting up.", tone = TextTone.Muted)
        }
    }
}
