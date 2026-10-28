package com.moyi.app.feature.onboarding.promise

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.moyi.app.designsystem.MoyiTheme
import com.moyi.app.designsystem.component.MoyiScaffold
import com.moyi.app.designsystem.component.MoyiText
import com.moyi.app.designsystem.component.PrimaryAction
import com.moyi.app.designsystem.component.TextLink
import com.moyi.app.designsystem.component.TextRole
import com.moyi.app.designsystem.component.TextTone
import com.moyi.app.feature.onboarding.resources.Res
import com.moyi.app.feature.onboarding.resources.promise_action
import com.moyi.app.feature.onboarding.resources.promise_have_account
import com.moyi.app.feature.onboarding.resources.promise_line
import com.moyi.app.feature.onboarding.resources.promise_reciprocity
import com.moyi.app.feature.onboarding.resources.promise_sample_a11y
import com.moyi.app.feature.onboarding.resources.promise_sample_entry
import com.moyi.app.feature.onboarding.resources.promise_sample_label
import org.jetbrains.compose.resources.stringResource

/** The promise has no state, so no ViewModel: two taps, both navigation. */
@Composable
fun PromiseRoot(
    onBegin: () -> Unit,
    onHaveAccount: () -> Unit,
) {
    PromiseScreen(onBegin = onBegin, onHaveAccount = onHaveAccount)
}

/**
 * Onboarding 1 · the promise (Figma 358:45; spec §3.1). A root screen: no
 * back, no eyebrow. The content starts 48 down, so the canonical sentence
 * sits at 72; the line 16 under it; the sample entry 24 under that. The
 * action block holds "Begin" (to sign up) and "I already have an account"
 * (to sign in), 24 apart.
 */
@Composable
fun PromiseScreen(
    onBegin: () -> Unit,
    onHaveAccount: () -> Unit,
) {
    val spacing = MoyiTheme.spacing
    MoyiScaffold(
        action = {
            PrimaryAction(stringResource(Res.string.promise_action), onClick = onBegin)
            Spacer(Modifier.height(spacing.lg))
            TextLink(
                stringResource(Res.string.promise_have_account),
                onClick = onHaveAccount,
                centered = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) {
        Spacer(Modifier.height(spacing.xxl))
        MoyiText(
            stringResource(Res.string.promise_reciprocity),
            role = TextRole.Title,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(spacing.md))
        MoyiText(stringResource(Res.string.promise_line), tone = TextTone.Muted)
        Spacer(Modifier.height(spacing.lg))
        SampleEntry()
    }
}

/**
 * The sample entry, set in `entry` type: typography, not an illustration
 * (states.md §1). Announced as an example, never as a real entry.
 *
 * A local stand-in drawn with EntryCard's surface and padding, because
 * `EntryCard` is PR #8's component.
 * TODO(ui): replace with `EntryCard` (own variant) once PR #8 lands.
 */
@Composable
private fun SampleEntry() {
    val colors = MoyiTheme.colors
    val shapes = MoyiTheme.shapes
    val entry = stringResource(Res.string.promise_sample_entry)
    val spoken = stringResource(Res.string.promise_sample_a11y, entry)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shapes.radius)
            .background(colors.surfaceRaised)
            .border(shapes.hairline, colors.outline, shapes.radius)
            .padding(MoyiTheme.spacing.lg)
            .clearAndSetSemantics { contentDescription = spoken },
    ) {
        MoyiText(stringResource(Res.string.promise_sample_label), role = TextRole.Label)
        Spacer(Modifier.height(MoyiTheme.spacing.md))
        MoyiText(entry, role = TextRole.Entry)
    }
}

@Preview
@Composable
private fun PromiseScreenDarkPreview() {
    MoyiTheme(darkTheme = true) { PromiseScreen(onBegin = {}, onHaveAccount = {}) }
}

@Preview
@Composable
private fun PromiseScreenLightPreview() {
    MoyiTheme(darkTheme = false) { PromiseScreen(onBegin = {}, onHaveAccount = {}) }
}
