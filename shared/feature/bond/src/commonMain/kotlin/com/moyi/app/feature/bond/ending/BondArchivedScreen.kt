package com.moyi.app.feature.bond.ending

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.moyi.app.designsystem.MoyiTheme
import com.moyi.app.designsystem.component.MoyiScaffold
import com.moyi.app.designsystem.component.MoyiText
import com.moyi.app.designsystem.component.PrimaryAction
import com.moyi.app.designsystem.component.ScreenHeader
import com.moyi.app.designsystem.component.TextLink
import com.moyi.app.designsystem.component.TextTone
import com.moyi.app.feature.bond.resources.Res
import com.moyi.app.feature.bond.resources.archived_account
import com.moyi.app.feature.bond.resources.archived_eyebrow
import com.moyi.app.feature.bond.resources.archived_heading
import com.moyi.app.feature.bond.resources.archived_line
import com.moyi.app.feature.bond.resources.archived_line_unnamed
import com.moyi.app.feature.bond.resources.archived_new_bond
import org.jetbrains.compose.resources.stringResource

/**
 * Bond · archived (Figma 399:21 READ ONLY, 614:1832 BLOCKED; states.md §9): where a
 * signed-in person lands when their bond has ended and they are in no open one.
 *
 * **No back** (SKILL.md: back would mean un-leaving), so it is a root: the eyebrow at 24,
 * the heading at 107, the line 16 under it. It states what states.md §9 says a person
 * needs: read only for both, the archive stays, and what the other person wrote stays
 * readable unless they remove it.
 *
 * **READ ONLY and BLOCKED are one screen here.** They must render from the same response
 * (states.md §9, `26` §2.1), and the only thing that told them apart, READ ONLY's "Close
 * the box", is refused on every archived bond (ADR-0030), so it is offered on neither.
 * Nothing on this screen can tell a blocked person they were blocked.
 *
 * The link into this bond's Archive is owed (the Archive is on another branch). The primary
 * action is starting a new bond, which pushes onboarding, so back from there returns here.
 * Under it, PROPOSED (docs/missing-components.md §57), **Your account**: a person here has
 * no Bond settings, and Account is where they export their archive (the frames' "Export
 * your archive") or delete their account. It pushes Account; back returns here.
 */
@Composable
fun BondArchivedScreen(
    partnerName: String?,
    onStartNewBond: () -> Unit,
    onOpenAccount: () -> Unit,
) {
    MoyiScaffold(
        action = {
            PrimaryAction(text = stringResource(Res.string.archived_new_bond), onClick = onStartNewBond)
            Spacer(Modifier.height(MoyiTheme.spacing.lg))
            TextLink(
                stringResource(Res.string.archived_account),
                onClick = onOpenAccount,
                centered = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) {
        ScreenHeader(eyebrow = stringResource(Res.string.archived_eyebrow), heading = stringResource(Res.string.archived_heading))
        Spacer(Modifier.height(MoyiTheme.spacing.md))
        MoyiText(
            partnerName?.let { stringResource(Res.string.archived_line, it) } ?: stringResource(Res.string.archived_line_unnamed),
            tone = TextTone.Muted,
        )
    }
}

@Preview
@Composable
private fun BondArchivedPreview() {
    MoyiTheme(darkTheme = true) { BondArchivedScreen(partnerName = "Tunde", onStartNewBond = {}, onOpenAccount = {}) }
}

@Preview
@Composable
private fun BondArchivedLightPreview() {
    MoyiTheme(darkTheme = false) { BondArchivedScreen(partnerName = "Tunde", onStartNewBond = {}, onOpenAccount = {}) }
}
