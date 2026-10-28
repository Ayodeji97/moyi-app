package com.moyi.app.feature.onboarding.join

import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import com.moyi.app.designsystem.MoyiTheme
import com.moyi.app.designsystem.component.MoyiScaffold
import com.moyi.app.designsystem.component.MoyiText
import com.moyi.app.designsystem.component.StateBlock
import com.moyi.app.designsystem.component.TextRole
import com.moyi.app.feature.onboarding.common.TopThenCentered
import com.moyi.app.feature.onboarding.resources.Res
import com.moyi.app.feature.onboarding.resources.join_eyebrow
import com.moyi.app.feature.onboarding.resources.join_failed_action
import com.moyi.app.feature.onboarding.resources.join_failed_heading
import com.moyi.app.feature.onboarding.resources.join_failed_line
import org.jetbrains.compose.resources.stringResource

/** No state and no ViewModel: one sentence for four causes, and two ways out. */
@Composable
fun JoinCannotBeUsedRoot(
    onEnterDifferentCode: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    JoinCannotBeUsedScreen(onEnterDifferentCode = onEnterDifferentCode, onBack = onNavigateBack)
}

/**
 * Join · cannot be used (Figma 310:23; spec §3.8). Back and eyebrow as on
 * confirm, then a StateBlock at its native 350, centred in the content
 * under the eyebrow. The same words for expired, revoked, full, never
 * existed and a third join attempt (FR-024): nothing may hint which.
 */
@Composable
fun JoinCannotBeUsedScreen(
    onEnterDifferentCode: () -> Unit,
    onBack: () -> Unit,
) {
    MoyiScaffold(
        onBack = onBack,
        contentArrangement = TopThenCentered(MoyiTheme.spacing.md),
    ) {
        MoyiText(stringResource(Res.string.join_eyebrow), role = TextRole.Label)
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            StateBlock(
                heading = stringResource(Res.string.join_failed_heading),
                line = stringResource(Res.string.join_failed_line),
                actionLabel = stringResource(Res.string.join_failed_action),
                onAction = onEnterDifferentCode,
            )
        }
    }
}

@Preview
@Composable
private fun JoinCannotBeUsedDarkPreview() {
    MoyiTheme(darkTheme = true) { JoinCannotBeUsedScreen(onEnterDifferentCode = {}, onBack = {}) }
}

@Preview
@Composable
private fun JoinCannotBeUsedLightPreview() {
    MoyiTheme(darkTheme = false) { JoinCannotBeUsedScreen(onEnterDifferentCode = {}, onBack = {}) }
}
