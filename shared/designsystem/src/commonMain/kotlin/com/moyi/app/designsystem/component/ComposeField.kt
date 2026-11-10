package com.moyi.app.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Constraints
import com.moyi.app.designsystem.platform.rememberReduceMotion
import com.moyi.app.designsystem.theme.MoyiMotion
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import com.moyi.app.designsystem.MoyiTheme
import com.moyi.app.designsystem.icon.MoyiIcons
import com.moyi.app.designsystem.resources.Res
import com.moyi.app.designsystem.resources.ds_compose_add_photo
import com.moyi.app.designsystem.resources.ds_compose_dismiss_prompt
import com.moyi.app.designsystem.resources.ds_compose_record_voice
import com.moyi.app.designsystem.theme.MoyiDimens
import org.jetbrains.compose.resources.stringResource

/**
 * The composer (`tokens.md` §4, component 2; Figma page 78:2, instance 358 × 360).
 *
 * - **One bordered box holds everything**: the dismissible [prompt] at the top, the
 *   entry text, and along the bottom edge the media affordances and the counter
 *   (tokens.md §6, "the composer toolbar lives inside the field").
 * - **No send button.** Send is the screen's `PrimaryAction`, outside the box.
 * - The text is the `entry` style (Newsreader 34 / 1.40), the same face and size the
 *   words will have on the card, so what you write is what they will read.
 * - **Spacing is geometry, not taste**: the box pads 8 and every slot wraps 12, so
 *   text and the 24 glyphs centred in 48 targets all align at 20 from the edge
 *   (tokens.md §3, `MoyiDimens.ComposeField`). Do not pad one without the other.
 * - The [counter] is the caller's text and is shown only when non-null: the screen
 *   passes it past 400 graphemes (states.md §4). It is `inkMuted` until [invalid]
 *   (over 500), then `danger`, and the box stroke thickens to 2 so the state is not
 *   colour alone (NFR-104). Announced politely when it changes.
 * - Media buttons are icon-only 48 targets with spoken names (tokens.md §6). Text is
 *   always sufficient (NFR-103); a null callback hides the button.
 *
 * The box fills whatever height the caller gives it (at least
 * [MoyiDimens.ComposeField.MinHeight] by default). **The words are never squeezed**
 * (docs/missing-components.md §66, the owner's iPhone, 2026-11-10): the text keeps at least
 * three lines of `entry` ([MoyiDimens.ComposeField.MinTextLines]) and takes whatever room the
 * rest leaves; when that is not enough, everything inside the box scrolls as one column
 * ([ComposerColumn]) and the line with the cursor is kept in view as you type.
 *
 * What is attached ([attachment]) is laid out by [AttachmentsLayout]: stacked at full size,
 * or, while [compactAttachments] (by default, while the keyboard is up), compact on one row,
 * so the words keep the room above the keyboard (PROPOSED, §66). The slot is told which.
 *
 * Words it opens with (a restored draft, my entry being edited) have the cursor after
 * their last character, ready to carry on, exactly as [Field] does.
 */
@Composable
fun ComposeField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    prompt: String? = null,
    onDismissPrompt: (() -> Unit)? = null,
    counter: String? = null,
    invalid: Boolean = false,
    enabled: Boolean = true,
    onAddPhoto: (() -> Unit)? = null,
    onRecordVoice: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
    /**
     * What is attached, inside the box above its toolbar: emit each attachment (the photo,
     * then the voice note) as a sibling; [AttachmentsLayout] places them. `compact` says
     * whether they are to step back (§66).
     */
    attachment: (@Composable (compact: Boolean) -> Unit)? = null,
    /** Whether the attachments are drawn compact. By default, while the keyboard is up. */
    compactAttachments: Boolean = rememberImeVisible(),
) {
    val colors = MoyiTheme.colors
    val shapes = MoyiTheme.shapes
    val wrap = MoyiDimens.ComposeField.Wrap
    val strokeWidth = if (invalid) MoyiDimens.Field.StrokeEmphasis else MoyiDimens.ComposeField.Stroke
    val strokeColor = if (invalid) colors.danger else colors.outlineStrong
    val scroll = rememberScrollState()
    val reduceMotion = rememberReduceMotion()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MoyiDimens.ComposeField.MinHeight)
            .clip(shapes.radius)
            .background(colors.surfaceRaised, shapes.radius)
            .border(strokeWidth, strokeColor, shapes.radius)
            .padding(MoyiDimens.ComposeField.BoxPadding),
    ) {
        // The height inside the box: the room the column fills before it has to scroll.
        val viewport = if (constraints.hasBoundedHeight) constraints.maxHeight else constraints.minHeight
        ComposerColumn(
            viewport = viewport,
            modifier = Modifier.fillMaxWidth().verticalScroll(scroll),
        ) {
            if (prompt != null) {
                Row(verticalAlignment = Alignment.Top) {
                    // The prompt is an offer, never a requirement (states.md §4).
                    MoyiText(
                        text = prompt,
                        tone = TextTone.Muted,
                        modifier = Modifier.weight(1f).padding(wrap),
                    )
                    if (onDismissPrompt != null) {
                        IconTarget(MoyiIcons.Close, stringResource(Res.string.ds_compose_dismiss_prompt), enabled, onDismissPrompt)
                    }
                }
            }
            // Field's pattern, for the same reasons (see Field): a TextFieldValue so prefilled words
            // open with the cursor at their end; otherwise exactly the String overload, which shows
            // the caller's text, keeps its own cursor and composition, and reports real changes only.
            var fieldValueState by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
            val fieldValue = fieldValueState.copy(text = value)
            SideEffect {
                if (fieldValue.selection != fieldValueState.selection || fieldValue.composition != fieldValueState.composition) {
                    fieldValueState = fieldValue
                }
            }
            var lastText by remember(value) { mutableStateOf(value) }
            var textLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
            var focused by remember { mutableStateOf(false) }
            val caretInView = remember { BringIntoViewRequester() }
            var textHeight by remember { mutableIntStateOf(0) }
            var attachmentsHeight by remember { mutableIntStateOf(0) }
            val wrapPx = with(LocalDensity.current) { wrap.roundToPx() }
            val showAttachmentsWithCaret = compactAttachments && attachment != null
            // The line being written stays on screen: after every keystroke, cursor move or change of
            // room (the keyboard rising, the attachments stepping back), the column scrolls the
            // cursor's line into view. The text field asks for this itself on focus; this covers the rest.
            // With the keyboard up, the compact row under the words comes into view with the line,
            // whole, when both fit; when they do not, the line wins (§66).
            LaunchedEffect(fieldValue.selection, textLayout, focused, viewport, showAttachmentsWithCaret, textHeight, attachmentsHeight) {
                val layout = textLayout
                if (!focused || layout == null) return@LaunchedEffect
                val offset = fieldValue.selection.end.coerceIn(0, layout.layoutInput.text.length)
                val caret = layout.getCursorRect(offset)
                val rowBottom = (textHeight + wrapPx + attachmentsHeight).toFloat()
                val withRow = showAttachmentsWithCaret && attachmentsHeight > 0 && rowBottom - caret.top <= viewport
                caretInView.bringIntoView(if (withRow) caret.copy(bottom = maxOf(caret.bottom, rowBottom)) else caret)
            }
            BasicTextField(
                value = fieldValue,
                onValueChange = { next ->
                    fieldValueState = next
                    val changed = lastText != next.text
                    lastText = next.text
                    if (changed) onValueChange(next.text)
                },
                enabled = enabled,
                textStyle = MoyiTheme.type.entry.copy(color = colors.ink),
                cursorBrush = SolidColor(colors.ink),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                // Never fewer than three lines of the words, however much is attached (§66).
                minLines = MoyiDimens.ComposeField.MinTextLines,
                onTextLayout = { textLayout = it },
                modifier = Modifier
                    .layoutId(ComposerSlot.Text)
                    .fillMaxWidth()
                    .padding(wrap)
                    // After the wrap: the requester's box is the text's own, so the cursor's rect needs no offset.
                    .bringIntoViewRequester(caretInView)
                    .onSizeChanged { textHeight = it.height }
                    .onFocusChanged { focused = it.isFocused }
                    .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
            )
            // Interim (docs/missing-components.md §41): what is attached sits above the toolbar, inside the box.
            if (attachment != null) {
                AttachmentsLayout(
                    compact = compactAttachments,
                    modifier = Modifier
                        .onSizeChanged { attachmentsHeight = it.height }
                        // The change of size eases on `motion.control`; with Reduce Motion it is a cut.
                        .then(if (reduceMotion) Modifier else Modifier.animateContentSize(tween(MoyiMotion.ControlMillis)))
                        // Compact, the row starts where the words start and keeps `xs` above and
                        // below, so its frames sit whole inside the box (§66).
                        .then(
                            if (compactAttachments) {
                                Modifier.padding(start = wrap, top = MoyiTheme.spacing.xs, bottom = MoyiTheme.spacing.xs)
                            } else {
                                Modifier
                            },
                        ),
                ) { attachment(compactAttachments) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onAddPhoto != null) {
                    IconTarget(MoyiIcons.Image, stringResource(Res.string.ds_compose_add_photo), enabled, onAddPhoto)
                }
                if (onRecordVoice != null) {
                    IconTarget(MoyiIcons.Mic, stringResource(Res.string.ds_compose_record_voice), enabled, onRecordVoice)
                }
                Spacer(Modifier.weight(1f))
                if (counter != null) {
                    MoyiText(
                        text = counter,
                        tone = if (invalid) TextTone.Danger else TextTone.Muted,
                        modifier = Modifier
                            .padding(wrap)
                            .semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
        }
    }
}

private enum class ComposerSlot { Text }

/**
 * **PROPOSED** (docs/missing-components.md §66, design review of the owner's screenshot; owner
 * ruling 2026-11-10: only while the keyboard is up): when [framed], an attachment sits in its own
 * frame, a `hairline` of the decorative `outline` on `shapes.radius`, so its remove control is
 * visibly inside the thing it removes and is not read as the prompt's dismiss (the same glyph).
 * Not a control's border: the frame itself does nothing. Unframed, it draws nothing at all,
 * so the full-size layout is main's.
 *
 * Always the same Box, framed or not, so what is inside keeps its state (a voice note playing).
 */
@Composable
internal fun AttachmentFrame(framed: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val frame = if (framed) Modifier.border(MoyiTheme.shapes.hairline, MoyiTheme.colors.outline, MoyiTheme.shapes.radius) else Modifier
    Box(modifier.then(frame)) { content() }
}

/**
 * The composer's inside, top to bottom: the prompt, the words, the attachments, the toolbar.
 * Everything but the words takes its own height; the words take the rest of [viewport] (the
 * box's inner height), and never less than their own three lines. When the parts add up to
 * more than the viewport the column is simply taller than it, and the caller's scroll moves
 * it: the words are never clipped to make the rest fit (§66).
 */
@Composable
private fun ComposerColumn(viewport: Int, modifier: Modifier, content: @Composable () -> Unit) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val loose = constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
        val text = measurables.indexOfFirst { it.layoutId == ComposerSlot.Text }
        val placeables = arrayOfNulls<Placeable>(measurables.size)
        var others = 0
        measurables.forEachIndexed { index, measurable ->
            if (index != text) {
                val placeable = measurable.measure(loose)
                placeables[index] = placeable
                others += placeable.height
            }
        }
        if (text >= 0) {
            placeables[text] = measurables[text].measure(loose.copy(minHeight = (viewport - others).coerceAtLeast(0)))
        }
        val height = placeables.sumOf { it?.height ?: 0 }
        layout(constraints.maxWidth, height) {
            var y = 0
            placeables.forEach { placeable ->
                if (placeable != null) {
                    placeable.place(0, y)
                    y += placeable.height
                }
            }
        }
    }
}

/**
 * **PROPOSED, not drawn** (docs/missing-components.md §66): where the composer's attachments go.
 *
 * - Not [compact]: one under the other, each the full width, as §41 and §44 place them.
 * - [compact] (the keyboard is up): side by side on one row, each as wide as it needs,
 *   [MoyiTheme.spacing] `sm` apart and centred on the row, so the photo's thumbnail and the voice
 *   note's chip together take one 48 high line. If they do not fit across, they stack, so nothing
 *   is cut.
 *
 * One layout for both, so a voice note being listened to keeps playing when the keyboard comes or goes.
 */
@Composable
fun AttachmentsLayout(compact: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val gap = MoyiTheme.spacing.sm
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val placeables = measurables.map { it.measure(loose) }
        val gapPx = gap.roundToPx()
        val across = placeables.sumOf { it.width } + gapPx * (placeables.size - 1).coerceAtLeast(0)
        val width = constraints.maxWidth
        if (compact && across <= width) {
            val height = placeables.maxOfOrNull { it.height } ?: 0
            layout(width, height) {
                var x = 0
                placeables.forEach {
                    it.place(x, (height - it.height) / 2)
                    x += it.width + gapPx
                }
            }
        } else {
            // Full size, as main draws them; compact but too wide, the frames keep `sm` between them.
            val stackGap = if (compact) gapPx else 0
            layout(width, placeables.sumOf { it.height } + stackGap * (placeables.size - 1).coerceAtLeast(0)) {
                var y = 0
                placeables.forEach {
                    it.place(0, y)
                    y += it.height + stackGap
                }
            }
        }
    }
}

@Composable
private fun IconTarget(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(MoyiDimens.ComposeField.IconTarget)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (enabled) MoyiTheme.colors.ink else MoyiTheme.colors.inkMuted,
            modifier = Modifier.size(MoyiDimens.IconGlyph),
        )
    }
}

@Preview
@Composable
private fun ComposeFieldDarkPreview() = PreviewSurface(darkTheme = true) { ComposeFieldSamples() }

@Preview
@Composable
private fun ComposeFieldLightPreview() = PreviewSurface(darkTheme = false) { ComposeFieldSamples() }

@Composable
private fun ComposeFieldSamples() {
    Column {
        ComposeField(
            value = "",
            onValueChange = {},
            prompt = "What did Tunde do today that you noticed?",
            onDismissPrompt = {},
            onAddPhoto = {},
            onRecordVoice = {},
        )
        Spacer(Modifier.size(MoyiTheme.spacing.md))
        ComposeField(
            value = "You stayed up to help me finish it.",
            onValueChange = {},
            counter = "512 / 500",
            invalid = true,
            onAddPhoto = {},
            onRecordVoice = {},
        )
    }
}
