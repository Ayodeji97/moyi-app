package com.moyi.app.designsystem.component

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import com.moyi.app.designsystem.MoyiTheme
import com.moyi.app.designsystem.icon.MoyiIcons
import com.moyi.app.designsystem.platform.DismissKeyboardOnTapAndDrag
import com.moyi.app.designsystem.platform.TaskPresentsAsSheet
import com.moyi.app.designsystem.resources.Res
import com.moyi.app.designsystem.resources.ds_back
import com.moyi.app.designsystem.resources.ds_cancel
import com.moyi.app.designsystem.resources.ds_close
import com.moyi.app.designsystem.theme.MoyiDimens
import org.jetbrains.compose.resources.stringResource

/**
 * The screen frame (`tokens.md` §4, component 6), placed as the Figma auth
 * screens draw it (`docs/figma/auth-placement-spec.md` §2):
 *
 * - `surface` ground. Inside the platform's safe areas
 *   ([WindowInsets.safeDrawing]: status and navigation bars, cutout and the
 *   keyboard), a padding of 16 at the sides and 24 at top and bottom. The
 *   24 is added inside the insets (D6, as ruled). While the keyboard is
 *   up the bottom 24 becomes 16, the gap Figma draws between the button
 *   and the keyboard (362:1105, 362:1284).
 * - Three stacked parts, 16 apart: an optional header holding the 48 x 48
 *   back target with the platform's 24 back glyph ([MoyiIcons.Back]: arrow on
 *   Android, chevron on iOS, D2) and **no title**; the scrolling content;
 *   an optional [action] block pinned to the bottom, centred, which is
 *   where `PrimaryAction`, an `InlineError` and a secondary `TextLink` go.
 * - The content scrolls: at 200 % font scale almost every form is taller
 *   than the screen, and a form that cannot scroll truncates (NFR-102).
 *   The action block stays visible, as in Figma's keyboard-up frames,
 *   unless it would take over half the height or leave the content less
 *   than 120: then content and actions scroll together ([ContentAndAction]).
 * - Leave [onBack] null on root screens (Today, Onboarding 1, the unverified
 *   gate); `SKILL.md` §9 lists them. System back is the platform's either way.
 * - A task (Write, D10) passes [onClose] instead: it closes rather than going
 *   back. Android draws an X in the same 48 target (full-screen dialog style);
 *   iOS draws Cancel and treats a swipe down on the header as Cancel (sheet
 *   style). The screen answers system back itself (`NoArrowBackHandler`).
 * - No navigation bar or rail: the product has no persistent chrome.
 * - Whether the screen has a back target is provided to the content
 *   ([LocalScaffoldHasBack]), so [ScreenHeader] drops the eyebrow under a back
 *   target and keeps it on a root screen (D1) without each screen deciding.
 * - On iOS, a tap outside a field or a drag of the content puts the keyboard
 *   away (D6, [DismissKeyboardOnTapAndDrag]). Android leaves it to Back.
 * - [contentArrangement], when set, makes the content at least the height of
 *   its viewport and arranges the children in it (a block centred when it fits),
 *   whether the actions are pinned or scroll with it. Left null, the content
 *   is top-aligned, as before.
 */
@Composable
fun MoyiScaffold(
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    /**
     * Closes a task (D10). Ignored when [onBack] is set: a screen goes back or
     * closes, never both.
     */
    onClose: (() -> Unit)? = null,
    action: (@Composable ColumnScope.() -> Unit)? = null,
    contentArrangement: Arrangement.Vertical? = null,
    /**
     * A root screen's own top row in place of the back target: Today's `nav` row
     * (date, then Archive and Bond settings). Pinned above the content, 16 above
     * it like the back. Ignored when [onBack] is set: a screen has one or the other.
     */
    header: (@Composable () -> Unit)? = null,
    /**
     * False when the screen lays out and scrolls its content itself, for example
     * to centre it vertically when it fits (Today, Reveal). The content then gets
     * the full remaining height with no scroll from here.
     */
    scrollContent: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val itemSpacing = MoyiDimens.Scaffold.ItemSpacing
    val imeVisible = rememberImeVisible()
    val focusManager = LocalFocusManager.current
    CompositionLocalProvider(LocalScaffoldHasBack provides (onBack != null || onClose != null)) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(MoyiTheme.colors.surface)
                .then(if (DismissKeyboardOnTapAndDrag) Modifier.clearFocusOnTap(focusManager) else Modifier)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(
                    start = MoyiDimens.Scaffold.Gutter,
                    end = MoyiDimens.Scaffold.Gutter,
                    top = MoyiDimens.Scaffold.PaddingVertical,
                    // At rest, 24 inside the safe area (D6). With the keyboard up
                    // the action frame sits flush on it (362:1105, 181:3: frame
                    // 472 to 544, keyboard from 544) but the button inside it ends
                    // at 528, so 16 stays between the button and the keyboard
                    // (measured on the composer, PR #8). safeDrawing already
                    // includes the IME inset.
                    bottom = if (imeVisible) MoyiDimens.Scaffold.KeyboardGap else MoyiDimens.Scaffold.PaddingVertical,
                ),
        ) {
            if (onBack != null) {
                BackAffordance(onBack)
                Spacer(Modifier.height(itemSpacing))
            } else if (onClose != null) {
                CloseAffordance(onClose)
                Spacer(Modifier.height(itemSpacing))
            } else if (header != null) {
                header()
                Spacer(Modifier.height(itemSpacing))
            }
            if (!scrollContent) {
                // The screen lays out and scrolls its content itself (Today, Write,
                // Reveal), so it gets the bounded room left and the actions stay
                // pinned below it. No stacking fallback: content that scrolls
                // itself cannot be measured at its full height.
                Column(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = contentArrangement ?: Arrangement.Top,
                    content = content,
                )
                if (action != null) {
                    Spacer(Modifier.height(itemSpacing))
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        content = action,
                    )
                }
            } else if (action == null && contentArrangement == null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberKeyboardDismissingScrollState()),
                    content = content,
                )
            } else if (action == null) {
                // The content is at least as tall as the space it scrolls in, so the
                // arrangement has room to centre a block that fits (Join · cannot be
                // used, 310:23), and it still scrolls when it does not (200 % font).
                BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                    val viewport = maxHeight
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberKeyboardDismissingScrollState())
                            .heightIn(min = viewport),
                        verticalArrangement = contentArrangement ?: Arrangement.Top,
                        content = content,
                    )
                }
            } else {
                ContentAndAction(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    action = action,
                    contentArrangement = contentArrangement ?: Arrangement.Top,
                    content = content,
                )
            }
        }
    }
}

/**
 * The content and the action block, 16 apart, in the height left below the
 * header.
 *
 * Normally the action block is pinned to the bottom and only the content
 * scrolls. When the two cannot share the height (keyboard up on a short
 * screen, landscape, 200 % font) pinning would squeeze the content to
 * nothing and clip the actions, so then content and actions scroll together
 * as one column ([shouldStackActions]).
 *
 * A [SubcomposeLayout], because the choice needs the action block's height
 * before the content is laid out. The content keeps its slot either way,
 * so switching (say, when the keyboard opens) does not reset its state or
 * take the focus away from a field.
 *
 * The content column is never shorter than the room above the actions, in
 * both modes, so [contentArrangement] can place its children in that room.
 */
@Composable
private fun ContentAndAction(
    modifier: Modifier,
    action: @Composable ColumnScope.() -> Unit,
    contentArrangement: Arrangement.Vertical,
    content: @Composable ColumnScope.() -> Unit,
) {
    val outerScroll = rememberKeyboardDismissingScrollState()
    val innerScroll = rememberKeyboardDismissingScrollState()
    BoxWithConstraints(modifier) {
        val viewport = constraints.maxHeight
        val spacing = MoyiDimens.Scaffold.ItemSpacing
        val minContent = MoyiDimens.Scaffold.MinContentHeight
        SubcomposeLayout(Modifier.fillMaxWidth().verticalScroll(outerScroll)) { incoming ->
            val loose = incoming.copy(minHeight = 0, maxHeight = Constraints.Infinity)
            val actionPlaceable = subcompose(ScaffoldSlot.Action) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    content = action,
                )
            }.map { it.measure(loose) }
            val actionHeight = actionPlaceable.maxOfOrNull { it.height } ?: 0
            val gap = spacing.roundToPx()
            val stacked = shouldStackActions(viewport, actionHeight, gap, minContent.roundToPx())
            val room = (viewport - gap - actionHeight).coerceAtLeast(0)
            val contentPlaceable = subcompose(ScaffoldSlot.Content) {
                Column(
                    // Pinned: the content scrolls on its own in the room left.
                    // Stacked: it takes its full height and the outer scroll moves both.
                    modifier = if (stacked) Modifier.fillMaxWidth() else Modifier.fillMaxWidth().verticalScroll(innerScroll),
                    verticalArrangement = contentArrangement,
                    content = content,
                )
            }.map {
                it.measure(
                    if (stacked) loose.copy(minHeight = room) else loose.copy(minHeight = room, maxHeight = room),
                )
            }
            val contentHeight = contentPlaceable.maxOfOrNull { it.height } ?: 0
            layout(incoming.maxWidth, contentHeight + gap + actionHeight) {
                contentPlaceable.forEach { it.place(0, 0) }
                actionPlaceable.forEach { it.place(0, contentHeight + gap) }
            }
        }
    }
}

private enum class ScaffoldSlot { Content, Action }

/**
 * Whether the action block must scroll with the content instead of being
 * pinned below it, given the [available] height for both and the [gap]
 * between them (all in px): when the actions would take more than half of
 * it, or leave the content less than [minContent].
 */
internal fun shouldStackActions(available: Int, action: Int, gap: Int, minContent: Int): Boolean =
    action * 2 > available || available - gap - action < minContent

/**
 * Whether the soft keyboard is showing. Read through [derivedStateOf] so the
 * scaffold recomposes when the answer flips, not on every frame of the
 * keyboard's slide.
 */
@Composable
private fun rememberImeVisible(): Boolean {
    val ime = WindowInsets.ime
    val density = LocalDensity.current
    val visible by remember(ime, density) { derivedStateOf { ime.getBottom(density) > 0 } }
    return visible
}

/**
 * Whether the enclosing [MoyiScaffold] draws a back target. Read by
 * [ScreenHeader]: under a back target the eyebrow goes (D1). False outside a
 * scaffold, so a header in a preview or a test reads as a root screen's.
 */
internal val LocalScaffoldHasBack = staticCompositionLocalOf { false }

/**
 * iOS (D6): a tap that nothing inside took, a tap on the ground or on plain
 * text, clears the focus, and with it the keyboard. A field, a button or a
 * link consumes its own tap, so this never fires for them: the gesture only
 * sees a tap no child consumed.
 */
private fun Modifier.clearFocusOnTap(focusManager: FocusManager): Modifier =
    pointerInput(focusManager) { detectTapGestures { focusManager.clearFocus() } }

/**
 * A scroll state for the scaffold's own scrolling. On iOS (D6) a drag of the
 * content clears the focus, like `UIScrollView.keyboardDismissMode = .onDrag`.
 *
 * It listens for the person's drag only ([DragInteraction.Start]): the scroll
 * that brings a newly focused field into view is not a drag, so focusing a
 * field never dismisses the keyboard it just raised.
 */
@Composable
private fun rememberKeyboardDismissingScrollState(): ScrollState {
    val state = rememberScrollState()
    if (DismissKeyboardOnTapAndDrag) {
        val focusManager = LocalFocusManager.current
        LaunchedEffect(state, focusManager) {
            state.interactionSource.interactions.collect { interaction ->
                if (interaction is DragInteraction.Start) focusManager.clearFocus()
            }
        }
    }
    return state
}

@Composable
private fun BackAffordance(onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .size(MoyiDimens.Scaffold.BackTarget)
            .clickable(role = Role.Button, onClick = onBack),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = MoyiIcons.Back,
            contentDescription = stringResource(Res.string.ds_back),
            tint = MoyiTheme.colors.ink,
            modifier = Modifier.size(MoyiDimens.IconGlyph),
        )
    }
}

/**
 * The header row of a task (D10), the same 48 high as the back target's.
 *
 * - Android: the Material close glyph in a 48 x 48 target at the start, as a
 *   full-screen dialog draws it.
 * - iOS: Cancel at the start, as a sheet's navigation bar places it, set as a
 *   [TextLink] (the design system's text control; it has no bar button). The whole
 *   row also takes a swipe down past [MoyiDimens.Scaffold.SwipeDownToClose] as
 *   Cancel. The row does not follow the finger: the sheet's slide is the only
 *   motion it spends (SKILL.md §5), and it is gone under Reduce Motion.
 */
@Composable
private fun CloseAffordance(onClose: () -> Unit) {
    if (!TaskPresentsAsSheet) {
        Box(
            modifier = Modifier
                .size(MoyiDimens.Scaffold.BackTarget)
                .clickable(role = Role.Button, onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = MoyiIcons.CloseTask,
                contentDescription = stringResource(Res.string.ds_close),
                tint = MoyiTheme.colors.ink,
                modifier = Modifier.size(MoyiDimens.IconGlyph),
            )
        }
        return
    }
    val currentOnClose by rememberUpdatedState(onClose)
    val threshold = MoyiDimens.Scaffold.SwipeDownToClose
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MoyiDimens.Scaffold.BackTarget)
            .pointerInput(threshold) {
                val distance = threshold.toPx()
                var dragged = 0f
                detectVerticalDragGestures(
                    onDragStart = { dragged = 0f },
                    onDragEnd = { if (dragged > distance) currentOnClose() },
                    onDragCancel = { dragged = 0f },
                ) { change, amount ->
                    dragged += amount
                    change.consume()
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        TextLink(text = stringResource(Res.string.ds_cancel), onClick = onClose)
    }
}

@Preview
@Composable
private fun MoyiScaffoldDarkPreview() = ScaffoldSample(darkTheme = true)

@Preview
@Composable
private fun MoyiScaffoldLightPreview() = ScaffoldSample(darkTheme = false)

/** A short viewport (keyboard up, landscape, 200 % font): content and actions scroll together. */
@Preview(widthDp = 360, heightDp = 400, fontScale = 2f)
@Composable
private fun MoyiScaffoldShortViewportPreview() = ScaffoldSample(darkTheme = true)

@Composable
private fun ScaffoldSample(darkTheme: Boolean) {
    MoyiTheme(darkTheme = darkTheme) {
        MoyiScaffold(
            onBack = {},
            action = {
                PrimaryAction("Check again", onClick = {})
                Spacer(Modifier.height(MoyiTheme.spacing.lg))
                TextLink("Use a different address", onClick = {}, centered = true, modifier = Modifier.fillMaxWidth())
            },
        ) {
            MoyiText("New account", role = TextRole.Label)
            Spacer(Modifier.height(MoyiTheme.spacing.xxxl))
            MoyiText("Check your email", role = TextRole.Title)
            Spacer(Modifier.height(MoyiTheme.spacing.md))
            MoyiText("We sent a link to amara@example.com. Open it to finish setting up.", tone = TextTone.Muted)
        }
    }
}
