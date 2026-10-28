package com.moyi.app.feature.today.reveal

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moyi.app.designsystem.MoyiTheme
import com.moyi.app.designsystem.component.EntryCard
import com.moyi.app.designsystem.component.EntryCardVariant
import com.moyi.app.designsystem.component.MoyiScaffold
import com.moyi.app.designsystem.presentation.ObserveAsEvents
import com.moyi.app.feature.today.common.CenteredContent
import com.moyi.app.feature.today.common.DateLabel
import com.moyi.app.feature.today.common.ScreenTitle
import com.moyi.app.feature.today.common.debugLongPress
import com.moyi.app.feature.today.common.isReducedMotionEnabled
import com.moyi.app.feature.today.resources.Res
import com.moyi.app.feature.today.resources.today_author_partner
import com.moyi.app.feature.today.resources.today_author_you
import com.moyi.app.feature.today.resources.today_revealed_title
import com.moyi.app.feature.today.today.WaitingNote
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun RevealRoot(
    onSettle: () -> Unit,
    debugTools: Boolean,
    viewModel: RevealViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            RevealEvent.Settle -> onSettle()
        }
    }
    RevealScreen(
        state = state,
        onAction = viewModel::onAction,
        reducedMotion = isReducedMotionEnabled(),
        debugTools = debugTools,
    )
}

/**
 * The reveal (Figma 179:3 before, 179:8 after; 179:23 / 179:28 under reduced
 * motion, the same geometry). **No nav row**: the date at y 24, then content.
 * After: the title at 59 and the partner's entry first, then mine, 16 apart,
 * centred in 108 to 820. No metrics, no streak, no next action: just the words.
 *
 * **The product's one animation** (SKILL.md §5). Under reduced motion it is an
 * instant-feeling cross-fade that still reads as a reveal: the before content
 * gives way to the title and both entries, nothing moves.
 *
 * TODO(motion): every duration, easing and offset here is a placeholder. The
 *  values live on the Figma motion board 180:21 (Reveal / Motion spec), which has
 *  not been read (the Figma MCP was refused, daily-loop spec §8). Replace
 *  [RevealMotion] and this transition with the board's choreography.
 */
@Composable
fun RevealScreen(
    state: RevealState,
    onAction: (RevealAction) -> Unit,
    reducedMotion: Boolean,
    debugTools: Boolean = false,
) {
    MoyiScaffold(scrollContent = false) {
        DateLabel(
            date = state.date,
            modifier = Modifier
                .fillMaxWidth()
                .debugLongPress(if (debugTools) ({ onAction(RevealAction.OnDebugReplay) }) else null),
        )
        Spacer(Modifier.height(MoyiTheme.spacing.md))
        AnimatedContent(
            targetState = state.phase,
            transitionSpec = { revealTransition(reducedMotion) },
            modifier = Modifier.weight(1f),
            label = "reveal",
        ) { phase ->
            Column(Modifier.fillMaxWidth()) {
                when (phase) {
                    RevealPhase.Before -> Before(state)
                    RevealPhase.After -> After(state)
                }
            }
        }
    }
}

/** 179:3: my entry and the waiting note, centred in 59 to 820. The same as Today · written-waiting. */
@Composable
private fun androidx.compose.foundation.layout.ColumnScope.Before(state: RevealState) {
    CenteredContent {
        EntryCard(authorLabel = stringResource(Res.string.today_author_you), text = state.myEntry)
        Spacer(Modifier.height(MoyiTheme.spacing.md))
        // Drawn as the same Waiting instance as Today (179:7); its links do nothing mid-reveal.
        WaitingNote(partnerName = state.partnerName, onEdit = {}, onDelete = {})
    }
}

/** 179:8: "Your day is open." and both entries, the partner's first. */
@Composable
private fun androidx.compose.foundation.layout.ColumnScope.After(state: RevealState) {
    // Announced once as it appears: the copy is the push's own line (copy.md table).
    ScreenTitle(
        text = stringResource(Res.string.today_revealed_title),
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
    Spacer(Modifier.height(MoyiTheme.spacing.md))
    CenteredContent {
        EntryCard(
            authorLabel = stringResource(Res.string.today_author_partner, state.partnerName),
            text = state.partnerEntry,
            variant = EntryCardVariant.Partner,
        )
        Spacer(Modifier.height(MoyiTheme.spacing.md))
        EntryCard(authorLabel = stringResource(Res.string.today_author_you), text = state.myEntry)
    }
}

/**
 * Placeholder choreography: the after content fades in while rising a little; the
 * before content fades out. Reduced motion: a plain cross-fade, no movement.
 * TODO(motion): values from the Figma motion board 180:21.
 */
private fun revealTransition(reducedMotion: Boolean): ContentTransform =
    if (reducedMotion) {
        fadeIn(tween(RevealMotion.CrossFadeMillis)) togetherWith fadeOut(tween(RevealMotion.CrossFadeMillis))
    } else {
        (fadeIn(tween(RevealMotion.RevealMillis)) + slideInVertically(tween(RevealMotion.RevealMillis)) { height -> height / RevealMotion.RiseFraction }) togetherWith
            fadeOut(tween(RevealMotion.FadeOutMillis))
    }

// ---- Previews (the two frames; the animation runs on a device) ----

@Preview
@Composable
private fun RevealBeforePreview() {
    MoyiTheme(darkTheme = true) { RevealScreen(state = RevealState(phase = RevealPhase.Before), onAction = {}, reducedMotion = false) }
}

@Preview
@Composable
private fun RevealAfterPreview() {
    MoyiTheme(darkTheme = true) { RevealScreen(state = RevealState(phase = RevealPhase.After), onAction = {}, reducedMotion = false) }
}

@Preview
@Composable
private fun RevealAfterLightPreview() {
    MoyiTheme(darkTheme = false) { RevealScreen(state = RevealState(phase = RevealPhase.After), onAction = {}, reducedMotion = true) }
}
