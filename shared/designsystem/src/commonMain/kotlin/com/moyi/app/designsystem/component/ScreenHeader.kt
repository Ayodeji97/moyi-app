package com.moyi.app.designsystem.component

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.moyi.app.designsystem.MoyiTheme
import com.moyi.app.designsystem.theme.MoyiDimens

/**
 * The eyebrow and heading a form opens with, placed by whether the screen has
 * a back target (figma-deviations.md D1, option (b), owner ruling 2026-10-29).
 *
 * - **Root screen** (no back): the eyebrow in `label`, [rootGap] (64 on a
 *   resting form) above the heading. The heading lands at 107, as in Figma.
 * - **Under a back target**: no eyebrow; the heading already says where you
 *   are. The heading sits 24 below the back target (`spacing.lg`, the
 *   between-blocks step): the scaffold's 16 plus 8 here. That puts it at 96,
 *   the line Write's composer already starts on (Figma 181:3), so every
 *   screen with a back target begins its content at the same height, and it
 *   is 75 higher than the 171 Figma drew. 32 was the other candidate: it
 *   lands at 104, close to the root's 107, but matches nothing else and
 *   leaves more of the top empty, which was the complaint.
 *
 * The heading is a semantic heading, so a screen reader can jump to it. Every
 * screen passes its eyebrow; the scaffold decides whether it shows, so a screen
 * that becomes a root (sign in, relationship type) gets it back on its own.
 *
 * It replaces auth's `AuthHeader` and onboarding's `OnboardingHeader`, which
 * were the same three lines; Delete entry is the third feature to use it.
 */
@Composable
fun ScreenHeader(
    eyebrow: String,
    heading: String,
    rootGap: Dp = MoyiTheme.spacing.xxxl,
) {
    if (LocalScaffoldHasBack.current) {
        Spacer(Modifier.height(MoyiTheme.spacing.lg - MoyiDimens.Scaffold.ItemSpacing))
    } else {
        MoyiText(eyebrow, role = TextRole.Label)
        Spacer(Modifier.height(rootGap))
    }
    MoyiText(heading, role = TextRole.Title, modifier = Modifier.semantics { heading() })
}
