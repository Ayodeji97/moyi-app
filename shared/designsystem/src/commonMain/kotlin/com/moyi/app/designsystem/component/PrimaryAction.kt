package com.moyi.app.designsystem.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.moyi.app.designsystem.MoyiTheme
import com.moyi.app.designsystem.theme.MoyiColors
import com.moyi.app.designsystem.platform.platformPressIndication
import com.moyi.app.designsystem.platform.reduceMotionEnabled
import com.moyi.app.designsystem.theme.MoyiMotion
import com.moyi.app.designsystem.theme.MoyiDimens

/**
 * The states of [PrimaryAction] (`tokens.md` §4: idle / sending /
 * queued-offline / disabled).
 *
 * The busy states carry the label the screen wants shown, because the label
 * is the whole of the feedback: the design system answers waiting with a
 * control that says what it is doing, not with a spinner (`states.md` §1c).
 */
@Immutable
sealed interface PrimaryActionState {
    /** Ready. Tapping runs the action. */
    data object Idle : PrimaryActionState

    /** The action is in flight. Not tappable; [label] replaces the text, e.g. "Checking". */
    data class Sending(val label: String) : PrimaryActionState

    /**
     * Accepted while offline and waiting to send. Not tappable; [label]
     * replaces the text. Visual and network behaviour are the screen's: this
     * is only the state `tokens.md` §4 names, so a screen can show it.
     */
    data class QueuedOffline(val label: String) : PrimaryActionState

    /** Not available yet, e.g. a required box is unticked. */
    data object Disabled : PrimaryActionState
}

/**
 * The one primary action on a screen (`tokens.md` §4 and §8.1).
 *
 * - `ink` fill with a `surface` label, which inverts by mode: a warm-white
 *   pill on dark, a near-black pill on light (16.26:1 / 15.18:1).
 * - Newsreader `action` label (body size, serif face). Not the sans label style.
 * - Hugs its label with a **minimum** width of 176 and a minimum height of
 *   48: a longer label or a 200 % font grows the control, never clips it.
 * - The four looks are Figma's (`docs/figma/auth-placement-spec.md` §4.1):
 *
 *   | State         | Fill            | Stroke              | Label     |
 *   |---------------|-----------------|---------------------|-----------|
 *   | Idle          | `ink`           | none                | `surface` |
 *   | Sending       | `ink`, 60 % opacity on the whole button | none | `surface` |
 *   | QueuedOffline | `surfaceRaised` | 1 `outlineStrong`   | `ink`     |
 *   | Disabled      | `surface`       | 1 `outline`         | `inkMuted`|
 *
 *   Disabled's hairline is what keeps it visible: a fill difference alone
 *   vanished in light mode (`tokens.md` §7b).
 * - **No spinner, no overlay.** Sending swaps the label, dims the button and
 *   stops taps; the change is announced politely to screen readers. Owner
 *   ruling 2026-11-08 (MoyiMotion): the swap cross-fades, the dim and the width
 *   ease over [MoyiMotion.ControlMillis], and while sending the label breathes
 *   gently, so a slow network reads as working rather than frozen. The words
 *   stay the feedback; the breath only says they are live. None of it runs under
 *   reduced motion.
 */
@Composable
fun PrimaryAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    state: PrimaryActionState = PrimaryActionState.Idle,
    /**
     * The destructive confirm (Figma 420:64, Reveal · delete · CONFIRM): `danger`
     * fill with a `surface` label (6.54:1 dark / 5.57:1 light, tokens.md §1 "no
     * onDanger"). Figma draws it as an override on the instance, not a variant.
     * Only for confirming the irreversible (SKILL.md §3).
     */
    destructive: Boolean = false,
) {
    val colors = MoyiTheme.colors
    val shapes = MoyiTheme.shapes

    val label = when (state) {
        PrimaryActionState.Idle, PrimaryActionState.Disabled -> text
        is PrimaryActionState.Sending -> state.label
        is PrimaryActionState.QueuedOffline -> state.label
    }
    val enabled = state == PrimaryActionState.Idle
    val look = colors.primaryActionLook(state, destructive)
    val reduceMotion = reduceMotionEnabled()
    // Tapping the action is the end of typing: the keyboard goes away, so whatever the
    // action leads to (Sending, an error under it, the next screen) is in view.
    val focusManager = LocalFocusManager.current
    val alpha by animateFloatAsState(look.alpha, tween(MoyiMotion.ControlMillis), label = "primaryActionAlpha")
    val labelAlpha = if (state is PrimaryActionState.Sending && !reduceMotion) {
        rememberInfiniteTransition(label = "primaryActionBreath").animateFloat(
            initialValue = 1f,
            targetValue = MoyiMotion.BreatheMinAlpha,
            animationSpec = infiniteRepeatable(tween(MoyiMotion.BreatheMillis, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "primaryActionBreathAlpha",
        ).value
    } else {
        1f
    }

    Box(
        modifier = modifier
            .widthIn(min = MoyiDimens.PrimaryAction.MinWidth)
            .heightIn(min = MoyiDimens.PrimaryAction.MinHeight)
            // A longer busy label ("Signing in") widens the pill smoothly, not in a jump.
            .animateContentSize(tween(MoyiMotion.ControlMillis))
            .alpha(alpha)
            .clip(shapes.radius)
            // Before the fill and border, so the iOS dim covers the whole
            // button and the Android ripple draws over the fill, clipped to it.
            .clickable(
                enabled = enabled,
                role = Role.Button,
                interactionSource = remember { MutableInteractionSource() },
                // The platform's press (D5), tinted with the label on Android:
                // the default ripple reads `ink`, which vanishes on the ink fill.
                indication = platformPressIndication(color = look.label),
                onClick = {
                    focusManager.clearFocus()
                    onClick()
                },
            )
            .background(look.fill)
            .border(MoyiDimens.PrimaryAction.Stroke, look.stroke, shapes.radius)
            .semantics {
                // The label change from "Check again" to "Checking" is the
                // only feedback, so it must be spoken without a focus move.
                // The label itself is the merged text; no stateDescription,
                // which would repeat it and be read twice.
                liveRegion = LiveRegionMode.Polite
            }
            .padding(
                horizontal = MoyiDimens.PrimaryAction.PaddingHorizontal,
                vertical = MoyiDimens.PrimaryAction.PaddingVertical,
            ),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = label,
            transitionSpec = {
                if (reduceMotion) {
                    EnterTransition.None togetherWith ExitTransition.None
                } else {
                    fadeIn(tween(MoyiMotion.ControlMillis)) togetherWith fadeOut(tween(MoyiMotion.ControlMillis))
                }
            },
            label = "primaryActionLabel",
        ) { shown ->
            Text(
                text = shown,
                style = MoyiTheme.type.action,
                color = look.label,
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer { this.alpha = labelAlpha },
            )
        }
    }
}

/** How a [PrimaryActionState] is painted. Pure, so the contrast test can read it. */
data class PrimaryActionLook(val fill: Color, val stroke: Color, val label: Color, val alpha: Float)

fun MoyiColors.primaryActionLook(state: PrimaryActionState, destructive: Boolean = false): PrimaryActionLook = when (state) {
    PrimaryActionState.Idle -> PrimaryActionLook(if (destructive) danger else ink, Color.Transparent, surface, 1f)
    is PrimaryActionState.Sending ->
        PrimaryActionLook(if (destructive) danger else ink, Color.Transparent, surface, MoyiDimens.PrimaryAction.SendingAlpha)
    is PrimaryActionState.QueuedOffline -> PrimaryActionLook(surfaceRaised, outlineStrong, ink, 1f)
    PrimaryActionState.Disabled -> PrimaryActionLook(surface, outline, inkMuted, 1f)
}

@Preview
@Composable
private fun PrimaryActionDarkPreview() = PrimaryActionStates(darkTheme = true)

@Preview
@Composable
private fun PrimaryActionLightPreview() = PrimaryActionStates(darkTheme = false)

@Composable
private fun PrimaryActionStates(darkTheme: Boolean) {
    PreviewSurface(darkTheme) {
        Column {
            PrimaryAction("Check again", onClick = {})
            Spacer(Modifier.height(MoyiTheme.spacing.md))
            PrimaryAction("Check again", onClick = {}, state = PrimaryActionState.Sending("Checking"))
            Spacer(Modifier.height(MoyiTheme.spacing.md))
            PrimaryAction("Create account", onClick = {}, state = PrimaryActionState.Disabled)
            Spacer(Modifier.height(MoyiTheme.spacing.md))
            PrimaryAction("Send", onClick = {}, state = PrimaryActionState.QueuedOffline("Queued"))
            Spacer(Modifier.height(MoyiTheme.spacing.md))
            PrimaryAction("Delete", onClick = {}, destructive = true)
        }
    }
}
