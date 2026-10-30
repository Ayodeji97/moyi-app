package com.moyi.app.feature.onboarding.promise

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.moyi.app.designsystem.MoyiTheme
import com.moyi.app.designsystem.component.EntryCard
import com.moyi.app.designsystem.component.EntryCardVariant
import com.moyi.app.designsystem.component.MoyiScaffold
import com.moyi.app.designsystem.component.MoyiText
import com.moyi.app.designsystem.component.PrimaryAction
import com.moyi.app.designsystem.component.TextLink
import com.moyi.app.designsystem.component.TextLinkStyle
import com.moyi.app.designsystem.component.TextRole
import com.moyi.app.designsystem.component.TextTone
import com.moyi.app.feature.onboarding.resources.Res
import com.moyi.app.feature.onboarding.resources.promise_action
import com.moyi.app.feature.onboarding.resources.promise_have_account
import com.moyi.app.feature.onboarding.resources.promise_line
import com.moyi.app.feature.onboarding.resources.promise_reciprocity
import com.moyi.app.feature.onboarding.resources.promise_sample_entry_a
import com.moyi.app.feature.onboarding.resources.promise_sample_entry_b
import com.moyi.app.feature.onboarding.resources.promise_sample_from_a
import com.moyi.app.feature.onboarding.resources.promise_sample_from_b
import com.moyi.app.feature.onboarding.resources.promise_sample_label
import com.moyi.app.feature.onboarding.resources.promise_samples_a11y
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
 * Onboarding 1 · the promise (Figma 358:45; spec §3.1; owner ruling
 * 2026-10-30, variant C). A root screen: no back, no eyebrow.
 *
 * The block (heading, line, two sample entries) is centred in the room above
 * the action block ([MoyiScaffold]'s `contentArrangement`), so on a tall phone
 * it does not hang from the top with an empty middle; when it is too tall for
 * the room (a small phone, 200 % text) it top-aligns and scrolls. "Begin"
 * keeps its hug width (Figma 99:34); "I already have an account" is set in
 * sentence case ([TextLinkStyle.Body]) on this screen only.
 */
@Composable
fun PromiseScreen(
    onBegin: () -> Unit,
    onHaveAccount: () -> Unit,
) {
    val spacing = MoyiTheme.spacing
    MoyiScaffold(
        contentArrangement = Arrangement.Center,
        action = {
            PrimaryAction(stringResource(Res.string.promise_action), onClick = onBegin)
            Spacer(Modifier.height(spacing.lg))
            TextLink(
                stringResource(Res.string.promise_have_account),
                onClick = onHaveAccount,
                style = TextLinkStyle.Body,
                centered = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) {
        MoyiText(
            stringResource(Res.string.promise_reciprocity),
            role = TextRole.Title,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(spacing.md))
        MoyiText(stringResource(Res.string.promise_line), tone = TextTone.Muted)
        Spacer(Modifier.height(spacing.lg))
        SampleEntries()
    }
}

/**
 * Two sample entries, one from each person, under an "An example" label:
 * typography, not an illustration (states.md §1). The pair is one stop for a
 * screen reader, announced as examples so no one takes them for real entries
 * (states.md §1 row 1); the cards' own merged semantics are replaced.
 */
@Composable
private fun SampleEntries() {
    val spacing = MoyiTheme.spacing
    val fromA = stringResource(Res.string.promise_sample_from_a)
    val entryA = stringResource(Res.string.promise_sample_entry_a)
    val fromB = stringResource(Res.string.promise_sample_from_b)
    val entryB = stringResource(Res.string.promise_sample_entry_b)
    val spoken = stringResource(Res.string.promise_samples_a11y, fromA, entryA, fromB, entryB)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(PromiseTags.SAMPLES)
            .clearAndSetSemantics { contentDescription = spoken },
    ) {
        MoyiText(stringResource(Res.string.promise_sample_label), role = TextRole.Label, tone = TextTone.Muted)
        Spacer(Modifier.height(spacing.md))
        EntryCard(fromA, entryA, variant = EntryCardVariant.Partner)
        Spacer(Modifier.height(spacing.md))
        EntryCard(fromB, entryB, variant = EntryCardVariant.Partner)
    }
}

/** Test tags for the promise screen's UI tests. */
internal object PromiseTags {
    const val SAMPLES = "promise.samples"
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
