package com.moyi.app.feature.auth.sessionended

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
import com.moyi.app.feature.auth.resources.Res
import com.moyi.app.feature.auth.resources.login_action
import com.moyi.app.feature.auth.resources.login_eyebrow
import com.moyi.app.feature.auth.resources.session_ended_heading
import com.moyi.app.feature.auth.resources.session_ended_line
import org.jetbrains.compose.resources.stringResource

/**
 * Why the person was signed out, when the server saw this sign-in used from two
 * places at once (`401 TOKEN_REUSE_DETECTED`, ADR-0021). Shown for that reason
 * only: an expired or revoked token has no such story, and goes straight to sign
 * in with the neutral line instead (`routeAfterSessionEnd`).
 *
 * No Figma frame exists. Placed as auth-placement-spec §7 proposes, the shape of
 * the unverified gate (345:46): a root screen with no back, the eyebrow and
 * heading, one line, and the PrimaryAction "Sign in" pinned at the bottom.
 *
 * Stateless, so no ViewModel: there is nothing to load and one thing to do.
 */
@Composable
fun SessionEndedScreen(onSignIn: () -> Unit) {
    MoyiScaffold(
        action = { PrimaryAction(text = stringResource(Res.string.login_action), onClick = onSignIn) },
    ) {
        ScreenHeader(
            eyebrow = stringResource(Res.string.login_eyebrow),
            heading = stringResource(Res.string.session_ended_heading),
        )
        Spacer(Modifier.height(MoyiTheme.spacing.md))
        MoyiText(stringResource(Res.string.session_ended_line), tone = TextTone.Muted)
    }
}

@Preview
@Composable
private fun SessionEndedDarkPreview() {
    MoyiTheme(darkTheme = true) { SessionEndedScreen(onSignIn = {}) }
}

@Preview
@Composable
private fun SessionEndedLightPreview() {
    MoyiTheme(darkTheme = false) { SessionEndedScreen(onSignIn = {}) }
}
