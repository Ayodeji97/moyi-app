package com.moyi.app.debug

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.moyi.app.designsystem.MoyiTheme
import com.moyi.app.designsystem.component.MoyiScaffold
import com.moyi.app.designsystem.component.MoyiText
import com.moyi.app.designsystem.component.TextLink
import com.moyi.app.designsystem.component.TextRole
import com.moyi.app.feature.onboarding.navigation.JoinCodeRoute
import com.moyi.app.feature.onboarding.navigation.OnboardingRoute
import com.moyi.app.feature.onboarding.navigation.PromiseRoute
import kotlinx.serialization.Serializable

/**
 * DEBUG ONLY. Registered and started only when the app is built with
 * `-PmoyiUiPreview=onboarding` (`UiPreviewConfig.UI_PREVIEW`), so the onboarding
 * screens can be clicked through and screenshotted without a backend: they
 * hold fake state, so no session is needed.
 *
 * The labels are developer text, never shown to a member, so they are not
 * string resources (CLAUDE.md's rule is for user-facing text).
 */
@Serializable
internal data object UiPreviewRoute

internal fun NavGraphBuilder.uiPreviewGraph(navController: NavController) {
    composable<UiPreviewRoute> {
        UiPreviewIndex(onOpen = { route -> navController.navigate(route) })
    }
}

private val entries: List<Pair<String, Any>> = listOf(
    "Onboarding 1: the promise" to PromiseRoute,
    "Onboarding 4 to 6: relationship type, create bond, invite" to OnboardingRoute,
    "Join: code from link (prefilled)" to JoinCodeRoute(prefilledCode = "K7Q2PD"),
    "Join: manual entry (ZZZZZZ plays cannot be used)" to JoinCodeRoute(),
)

@Composable
private fun UiPreviewIndex(onOpen: (Any) -> Unit) {
    MoyiScaffold {
        MoyiText("UI preview, debug build", role = TextRole.Label)
        Spacer(Modifier.height(MoyiTheme.spacing.lg))
        entries.forEach { (label, route) ->
            TextLink(label, onClick = { onOpen(route) }, modifier = Modifier.fillMaxWidth())
        }
    }
}
