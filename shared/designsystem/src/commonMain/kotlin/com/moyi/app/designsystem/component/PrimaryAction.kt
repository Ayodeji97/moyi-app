package com.moyi.app.designsystem.component

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
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.moyi.app.designsystem.MoyiTheme

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
 * - Disabled is `surfaceRaised` with an `outline` hairline and an `inkMuted`
 *   label. The hairline is required: in light mode `surfaceRaised` on
 *   `surface` is 1.04:1, and a fill alone made it vanish (`tokens.md` §7b).
 * - **No spinner, no animation, no overlay.** Sending swaps the label and
 *   stops taps; the change is announced politely to screen readers.
 */
@Composable
fun PrimaryAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    state: PrimaryActionState = PrimaryActionState.Idle,
) {
    val colors = MoyiTheme.colors
    val shapes = MoyiTheme.shapes
    val spacing = MoyiTheme.spacing

    val label = when (state) {
        PrimaryActionState.Idle, PrimaryActionState.Disabled -> text
        is PrimaryActionState.Sending -> state.label
        is PrimaryActionState.QueuedOffline -> state.label
    }
    val enabled = state == PrimaryActionState.Idle
    val disabledLook = state == PrimaryActionState.Disabled
    val fill = if (disabledLook) colors.surfaceRaised else colors.ink
    val content = if (disabledLook) colors.inkMuted else colors.surface
    val border = if (disabledLook) colors.outline else Color.Transparent

    Box(
        modifier = modifier
            .widthIn(min = MinWidth)
            .heightIn(min = spacing.touchTarget)
            .clip(shapes.radius)
            .background(fill)
            .border(shapes.hairline, border, shapes.radius)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = content),
                onClick = onClick,
            )
            .semantics {
                // The label change from "Check again" to "Checking" is the
                // only feedback, so it must be spoken without a focus move.
                liveRegion = LiveRegionMode.Polite
                when (state) {
                    is PrimaryActionState.Sending -> stateDescription = state.label
                    is PrimaryActionState.QueuedOffline -> stateDescription = state.label
                    PrimaryActionState.Idle, PrimaryActionState.Disabled -> Unit
                }
            }
            .padding(horizontal = spacing.lg, vertical = spacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MoyiTheme.type.action,
            color = content,
            textAlign = TextAlign.Center,
        )
    }
}

/** `tokens.md` §8.1: a floor, not a fixed width, so a label never clips. */
private val MinWidth = 176.dp

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
            PrimaryAction("Send", onClick = {}, state = PrimaryActionState.QueuedOffline("Saved"))
        }
    }
}
