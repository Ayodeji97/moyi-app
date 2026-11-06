package com.moyi.app.feature.bond.ending

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.moyi.app.designsystem.MoyiTheme
import com.moyi.app.designsystem.component.MoyiScaffold
import com.moyi.app.designsystem.component.MoyiText
import com.moyi.app.designsystem.component.PrimaryAction
import com.moyi.app.designsystem.component.ScreenHeader
import com.moyi.app.designsystem.component.TextTone
import com.moyi.app.feature.bond.resources.Res
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
 * The frames' "Export your archive" and the link into this bond's Archive are owed (no
 * export endpoint in the contract copy; the Archive is on another branch). The one action
 * is starting a new bond, which pushes onboarding, so back from there returns here.
 */
@Composable
fun BondArchivedScreen(
    partnerName: String?,
    onStartNewBond: () -> Unit,
) {
    MoyiScaffold(
        action = {
            PrimaryAction(text = stringResource(Res.string.archived_new_bond), onClick = onStartNewBond)
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
    MoyiTheme(darkTheme = true) { BondArchivedScreen(partnerName = "Tunde", onStartNewBond = {}) }
}

@Preview
@Composable
private fun BondArchivedLightPreview() {
    MoyiTheme(darkTheme = false) { BondArchivedScreen(partnerName = "Tunde", onStartNewBond = {}) }
}
