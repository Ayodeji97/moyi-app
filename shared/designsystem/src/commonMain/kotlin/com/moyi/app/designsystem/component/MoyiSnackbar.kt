package com.moyi.app.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import com.moyi.app.designsystem.MoyiTheme
import com.moyi.app.designsystem.platform.rememberReduceMotion
import com.moyi.app.designsystem.resources.Res
import com.moyi.app.designsystem.resources.ds_close
import com.moyi.app.designsystem.theme.MoyiDimens
import com.moyi.app.designsystem.theme.MoyiMotion
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/**
 * A short note at the foot of the screen that goes away by itself (PROPOSED,
 * docs/missing-components.md §62: the design system draws no snackbar). Single-use (the
 * already-confirmed note) until a Figma frame draws one (owner ruling 2026-11-10). One line of `body` in
 * `ink` on `surfaceRaised`, the hairline `outline` and radius 12 (tokens.md §3: a surface that
 * floats is raised, not shadowed), inset by the gutter, and held one item gap (16) above
 * where a screen's `PrimaryAction` sits (24 from the safe area, 48 tall), so it never covers
 * the main action (smoke test 2026-11-10: on Today it hid Write).
 *
 * - **No action and no close mark.** It only says what happened; nothing needs doing.
 * - **Goes away by itself** after [snackbarShowMillis] of its text, 6 to 10 seconds by length
 *   (owner ruling 2026-11-17, docs/missing-components.md §87), or longer when the platform asks
 *   for more time for text (Android's accessibility timeout setting, through
 *   [LocalAccessibilityManager]). Then [onDismiss] clears [message].
 * - **A tap closes it early** (§87): the whole note is the target, at least 48 tall, a
 *   Button to assistive technology whose action is named "Close". It leaves as it would have.
 * - **Announced** politely (a live region), so a screen reader reads it without moving focus.
 * - **Motion:** fades in over `motion.crossFade` while rising a quarter of its height on
 *   `motion.push`'s curve, and the reverse on the way out. With Reduce Motion (iOS) it only
 *   fades; Android's "Remove animations" scales both to nothing on its own.
 *
 * Fills the space it is given and draws only the note, so it never takes a touch from the
 * screen under it. Show it above everything that should not cover it (the launch splash).
 */
@Composable
fun MoyiSnackbar(message: String?, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val reduceMotion = rememberReduceMotion()
    val accessibility = LocalAccessibilityManager.current
    val dismiss by rememberUpdatedState(onDismiss)
    // Kept while the note fades out, after [message] has gone back to null.
    var shown by remember { mutableStateOf(message.orEmpty()) }
    if (message != null) shown = message

    LaunchedEffect(message) {
        if (message == null) return@LaunchedEffect
        val timeout = accessibility?.calculateRecommendedTimeoutMillis(
            originalTimeoutMillis = snackbarShowMillis(message),
            containsIcons = false,
            containsText = true,
            // The tap that closes it is a shortcut, not something the note asks for: the
            // timeout stays the one for text, so a screen reader still gets its longer time.
            containsControls = false,
        ) ?: snackbarShowMillis(message)
        delay(timeout)
        dismiss()
    }

    val spacing = MoyiTheme.spacing
    Box(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(start = spacing.gutter, end = spacing.gutter, bottom = AboveThePrimaryAction),
        contentAlignment = Alignment.BottomCenter,
    ) {
        val fade = tween<Float>(MoyiMotion.CrossFadeMillis)
        val rise = tween<IntOffset>(MoyiMotion.PushMillis, easing = FastOutSlowInEasing)
        AnimatedVisibility(
            visible = message != null,
            enter = if (reduceMotion) fadeIn(fade) else fadeIn(fade) + slideInVertically(rise) { it / 4 },
            exit = if (reduceMotion) fadeOut(fade) else fadeOut(fade) + slideOutVertically(rise) { it / 4 },
        ) {
            SnackbarNote(shown, onClick = { if (message != null) dismiss() })
        }
    }
}

@Composable
private fun SnackbarNote(text: String, onClick: () -> Unit = {}) {
    val shapes = MoyiTheme.shapes
    MoyiText(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MoyiDimens.TouchTarget)
            .clip(shapes.radius)
            .background(MoyiTheme.colors.surfaceRaised, shapes.radius)
            .border(shapes.hairline, MoyiTheme.colors.outline, shapes.radius)
            // The whole note closes it: one element, read as its text, then "Close".
            .clickable(onClickLabel = stringResource(Res.string.ds_close), role = Role.Button, onClick = onClick)
            .padding(MoyiTheme.spacing.md)
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/** The scaffold's bottom padding, a `PrimaryAction`'s height and the scaffold's item gap. */
private val AboveThePrimaryAction =
    MoyiDimens.Scaffold.PaddingVertical + MoyiDimens.PrimaryAction.MinHeight + MoyiDimens.Scaffold.ItemSpacing

/**
 * How long a note of [text] stays (owner ruling 2026-11-17, docs/missing-components.md §87;
 * was a flat 4 s, §62): time to notice it, then time to read it twice.
 *
 * - **Notice:** [SnackbarNoticeMillis], 2 s: it appears at the foot of the screen, away from
 *   where the eyes are.
 * - **Read twice:** [SnackbarMillisPerCharacter] a character. A slow, careful reader takes
 *   about 200 words a minute; at about 6 characters a word with its space that is 50 ms a
 *   character, so 100 ms reads it twice. Characters, not words, so a language that does not
 *   put spaces between words is timed the same way.
 * - **Bounds:** at least [SnackbarMinMillis] (6 s), however short, and at most
 *   [SnackbarMaxMillis] (10 s): longer than that, a note that covers the screen's foot is in
 *   the way, and anything that long belongs in a card that waits for OK.
 *
 * "Your new password is saved." (27 characters) stays 6 s; 80 characters or more stay 10 s.
 * The platform's accessibility timeout can lengthen it further (see [MoyiSnackbar]).
 */
fun snackbarShowMillis(text: String): Long =
    (SnackbarNoticeMillis + text.length * SnackbarMillisPerCharacter).coerceIn(SnackbarMinMillis, SnackbarMaxMillis)

private const val SnackbarNoticeMillis = 2_000L
private const val SnackbarMillisPerCharacter = 100L
const val SnackbarMinMillis = 6_000L
const val SnackbarMaxMillis = 10_000L

@Preview
@Composable
private fun MoyiSnackbarDarkPreview() = PreviewSurface(darkTheme = true) { SnackbarNote("Your email is already confirmed.") }

@Preview
@Composable
private fun MoyiSnackbarLightPreview() = PreviewSurface(darkTheme = false) { SnackbarNote("Your email is already confirmed.") }
