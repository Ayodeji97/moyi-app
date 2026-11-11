package com.moyi.app.feature.auth.reset

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.moyi.app.designsystem.MoyiTheme
import com.moyi.app.designsystem.component.MoyiScaffold
import com.moyi.app.designsystem.component.MoyiText
import com.moyi.app.designsystem.component.ScreenHeader
import com.moyi.app.designsystem.component.TextLink
import com.moyi.app.designsystem.component.TextTone
import com.moyi.app.feature.auth.resources.Res
import com.moyi.app.feature.auth.resources.login_eyebrow
import com.moyi.app.feature.auth.resources.reset_sent_action
import com.moyi.app.feature.auth.resources.reset_sent_line
import com.moyi.app.feature.auth.resources.verify_heading
import org.jetbrains.compose.resources.stringResource

/**
 * Reset link sent (Account 3, Figma 345:23), NON-DISCLOSING: the same words
 * whether or not the address has an account (states.md §1b). That is why it
 * takes no address and has no ViewModel: there is nothing here that could
 * vary with the answer, and nothing to load.
 *
 * Placed per auth-placement-spec §3.4: heading and line as one block 16
 * apart; the action block holds one centred secondary link and no
 * PrimaryAction. The link opens Choose a new password, where the six-digit
 * code from the email is typed (or the link pasted, docs/missing-components.md §58).
 */
@Composable
fun ResetLinkSentScreen(
    onBack: () -> Unit,
    onChooseNewPassword: () -> Unit,
) {
    val spacing = MoyiTheme.spacing
    MoyiScaffold(
        onBack = onBack,
        action = {
            TextLink(
                stringResource(Res.string.reset_sent_action),
                onClick = onChooseNewPassword,
                centered = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) {
        ScreenHeader(stringResource(Res.string.login_eyebrow), stringResource(Res.string.verify_heading))
        Spacer(Modifier.height(spacing.md))
        MoyiText(stringResource(Res.string.reset_sent_line), tone = TextTone.Muted)
    }
}

@Preview
@Composable
private fun ResetLinkSentScreenPreview() {
    MoyiTheme(darkTheme = true) {
        ResetLinkSentScreen(onBack = {}, onChooseNewPassword = {})
    }
}
