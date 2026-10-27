package com.moyi.app.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.moyi.app.designsystem.icon.MoyiIcons
import com.moyi.app.designsystem.resources.Res
import com.moyi.app.designsystem.resources.ds_back
import com.moyi.app.designsystem.theme.MoyiDimens
import org.jetbrains.compose.resources.stringResource

/**
 * The screen frame (`tokens.md` §4, component 6), placed as the Figma auth
 * screens draw it (`docs/figma/auth-placement-spec.md` §2):
 *
 * - `surface` ground. Inside the platform's safe areas
 *   ([WindowInsets.safeDrawing]: status and navigation bars, cutout and the
 *   keyboard), a padding of 16 at the sides and 24 at top and bottom. The
 *   24 is added inside the insets (D6, as ruled).
 * - Three stacked parts, 16 apart: an optional header holding the 48 x 48
 *   back target with a 24 chevron and **no title**; the scrolling content;
 *   an optional [action] block pinned to the bottom, centred, which is
 *   where `PrimaryAction`, an `InlineError` and a secondary `TextLink` go.
 * - The content scrolls: at 200 % font scale almost every form is taller
 *   than the screen, and a form that cannot scroll truncates (NFR-102).
 *   The action block stays visible, as in Figma's keyboard-up frames.
 * - Leave [onBack] null on root screens (Today, Onboarding 1, the unverified
 *   gate); `SKILL.md` §9 lists them. System back is the platform's either way.
 * - No navigation bar or rail: the product has no persistent chrome.
 */
@Composable
fun MoyiScaffold(
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    action: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val itemSpacing = MoyiDimens.Scaffold.ItemSpacing
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MoyiTheme.colors.surface)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(
                horizontal = MoyiDimens.Scaffold.Gutter,
                vertical = MoyiDimens.Scaffold.PaddingVertical,
            ),
    ) {
        if (onBack != null) {
            BackAffordance(onBack)
            Spacer(Modifier.height(itemSpacing))
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            content = content,
        )
        if (action != null) {
            Spacer(Modifier.height(itemSpacing))
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                content = action,
            )
        }
    }
}

@Composable
private fun BackAffordance(onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .size(MoyiDimens.Scaffold.BackTarget)
            .clickable(role = Role.Button, onClick = onBack),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = MoyiIcons.ChevronLeft,
            contentDescription = stringResource(Res.string.ds_back),
            tint = MoyiTheme.colors.ink,
            modifier = Modifier.size(MoyiDimens.IconGlyph),
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
        MoyiScaffold(
            onBack = {},
            action = {
                PrimaryAction("Check again", onClick = {})
                Spacer(Modifier.height(MoyiTheme.spacing.lg))
                TextLink("Use a different address", onClick = {}, centered = true, modifier = Modifier.fillMaxWidth())
            },
        ) {
            MoyiText("New account", role = TextRole.Label)
            Spacer(Modifier.height(MoyiTheme.spacing.xxxl))
            MoyiText("Check your email", role = TextRole.Title)
            Spacer(Modifier.height(MoyiTheme.spacing.md))
            MoyiText("We sent a link to amara@example.com. Open it to finish setting up.", tone = TextTone.Muted)
        }
    }
}
