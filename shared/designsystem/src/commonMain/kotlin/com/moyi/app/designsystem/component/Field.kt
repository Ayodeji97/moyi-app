package com.moyi.app.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import com.moyi.app.designsystem.MoyiTheme
import com.moyi.app.designsystem.theme.MoyiDimens
import com.moyi.app.designsystem.icon.MoyiIcons
import com.moyi.app.designsystem.resources.Res
import com.moyi.app.designsystem.resources.ds_password_hide
import com.moyi.app.designsystem.resources.ds_password_show
import com.moyi.app.designsystem.theme.MoyiColors
import org.jetbrains.compose.resources.stringResource

/**
 * The `Field` types built so far. `tokens.md` §4 defines five
 * (`Text` / `Secure` / `Select` / `Code` / `Multiline`); `Multiline` is added
 * when a screen needs it.
 *
 * - [Code]: one field set in the `code` style (28 % tracking), box 65 tall,
 *   **not six boxes** (six boxes announce as six controls, break paste and
 *   fail at 200 % scale). Capitalises characters, no autocorrect.
 * - [Select]: the box is a button showing the chosen value with a trailing
 *   chevron; tapping it opens a picker the screen owns (`onClick`).
 */
enum class FieldType { Text, Secure, Code, Select }

/** `Field`'s four visual states (`tokens.md` §4). Derived, never passed in. */
enum class FieldState { Idle, Focus, Error, Disabled }

/** Disabled wins over Error (a disabled field cannot be corrected), and Error over Focus. */
fun fieldStateOf(enabled: Boolean, hasError: Boolean, focused: Boolean): FieldState = when {
    !enabled -> FieldState.Disabled
    hasError -> FieldState.Error
    focused -> FieldState.Focus
    else -> FieldState.Idle
}

/** The box's stroke for a state: colour token and weight. */
data class FieldStroke(val width: Dp, val color: Color)

/** Figma §4.2: the box is `surfaceRaised`, and `surface` when Disabled. */
fun MoyiColors.fieldFill(state: FieldState): Color = if (state == FieldState.Disabled) surface else surfaceRaised

/** The value's colour: `ink`, and `inkMuted` when Disabled (Figma §4.2). */
fun MoyiColors.fieldText(state: FieldState): Color = if (state == FieldState.Disabled) inkMuted else ink

/**
 * Focus and Error change the stroke **weight**, not only the colour
 * (NFR-104: no information by colour alone), so both survive greyscale and
 * colour blindness. Focus is `ink`, not an accent (`tokens.md` §4).
 *
 * Disabled is `outline`, as `tokens.md` §4/§7b and the Figma component's own
 * description say. The Figma variants bind `outlineStrong` instead
 * (`docs/figma/auth-placement-spec.md` D1); the ruling is that tokens.md
 * wins on colour. WCAG 1.4.11 exempts inactive controls, and the `surface`
 * fill and muted text carry the state too.
 */
fun MoyiColors.fieldStroke(state: FieldState): FieldStroke = when (state) {
    FieldState.Idle -> FieldStroke(MoyiDimens.Field.StrokeIdle, outlineStrong)
    FieldState.Focus -> FieldStroke(MoyiDimens.Field.StrokeEmphasis, ink)
    FieldState.Error -> FieldStroke(MoyiDimens.Field.StrokeEmphasis, danger)
    FieldState.Disabled -> FieldStroke(MoyiDimens.Field.StrokeDisabled, outline)
}

/**
 * The one input control (`tokens.md` §4, component 8).
 *
 * - The [label] is always visible above the box. No placeholder stands in
 *   for it: a placeholder disappears the moment someone types.
 * - The box is `surfaceRaised`, at least 56 tall, radius 12, padding 16;
 *   it grows with the font. Every number is in `MoyiDimens.Field`.
 * - [error], when set, replaces [helper], is drawn in `danger`, and is
 *   exposed to screen readers as the field's error.
 * - [FieldType.Secure] hides the text and carries an eye toggle: a second
 *   target 48 wide and the box's full 56 tall, with a spoken label that
 *   says what a tap will do.
 * - Keyboard type, capitalisation and IME action pass through
 *   [keyboardOptions] and [keyboardActions].
 *
 * State is hoisted: the caller owns [value]. The only state held here is
 * the focus and whether a secure value is revealed, which are Compose's.
 */
@Composable
fun Field(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    type: FieldType = FieldType.Text,
    helper: String? = null,
    error: String? = null,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    onClick: () -> Unit = {},
) {
    if (type == FieldType.Select) {
        SelectField(value, label, modifier, helper, error, enabled, onClick)
        return
    }
    val colors = MoyiTheme.colors
    val shapes = MoyiTheme.shapes
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    var revealed by rememberSaveable { mutableStateOf(false) }

    val state = fieldStateOf(enabled = enabled, hasError = error != null, focused = focused)
    val stroke = colors.fieldStroke(state)
    val textColor = colors.fieldText(state)
    val secure = type == FieldType.Secure
    val code = type == FieldType.Code
    val effectiveKeyboard = when {
        keyboardOptions != KeyboardOptions.Default -> keyboardOptions
        secure -> KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false)
        // Capitals and digits: auto-capitalise, never a numeric pad (states.md §2).
        code -> KeyboardOptions(
            capitalization = KeyboardCapitalization.Characters,
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Ascii,
        )
        else -> keyboardOptions
    }

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .semantics { if (error != null) error(error) },
        enabled = enabled,
        singleLine = true,
        textStyle = (if (code) MoyiTheme.type.code else MoyiTheme.type.body).copy(color = textColor),
        cursorBrush = SolidColor(colors.ink),
        keyboardOptions = effectiveKeyboard,
        keyboardActions = keyboardActions,
        visualTransformation = if (secure && !revealed) PasswordVisualTransformation() else VisualTransformation.None,
        interactionSource = interactionSource,
        // The label sits inside the decoration box, as Material's text fields
        // do, so it lives under the text field's semantics node rather than
        // beside it. Not yet walked with TalkBack or VoiceOver: owed before
        // release, per SKILL.md §8.
        decorationBox = { innerTextField ->
            Column {
                MoyiText(text = label, role = TextRole.Label)
                Spacer(Modifier.height(MoyiDimens.Field.LabelGap))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = if (code) MoyiDimens.Field.CodeBoxMinHeight else MoyiDimens.Field.BoxMinHeight)
                        .background(colors.fieldFill(state), shapes.radius)
                        .border(stroke.width, stroke.color, shapes.radius)
                        // Vertical padding sits on the value, not the row, so the
                        // eye target can take the box's full height.
                        .padding(horizontal = MoyiDimens.Field.Padding),
                    horizontalArrangement = Arrangement.spacedBy(MoyiDimens.Field.ItemGap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // A Code value (32.5 line) is centred in its 65 box rather than padded.
                    val verticalPadding = if (code) Modifier else Modifier.padding(vertical = MoyiDimens.Field.Padding)
                    Box(Modifier.weight(1f).then(verticalPadding)) { innerTextField() }
                    if (secure) {
                        RevealToggle(revealed = revealed, enabled = enabled, onToggle = { revealed = !revealed })
                    }
                }
                val below = error ?: helper
                if (below != null) {
                    Spacer(Modifier.height(MoyiDimens.Field.SupportGap))
                    MoyiText(text = below, tone = if (error != null) TextTone.Danger else TextTone.Muted)
                }
            }
        },
    )
}

/**
 * [FieldType.Select]: the same label, box and helper, but the box is a button.
 * It shows the chosen value and a chevron, and [onClick] opens the picker
 * (onboarding's time zone). Focus is never drawn here: the picker takes over.
 */
@Composable
private fun SelectField(
    value: String,
    label: String,
    modifier: Modifier,
    helper: String?,
    error: String?,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = MoyiTheme.colors
    val shapes = MoyiTheme.shapes
    val state = fieldStateOf(enabled = enabled, hasError = error != null, focused = false)
    val stroke = colors.fieldStroke(state)
    Column(modifier.fillMaxWidth()) {
        // The box below speaks the label with its value; spoken here too, it was read twice.
        MoyiText(text = label, role = TextRole.Label, modifier = Modifier.clearAndSetSemantics {})
        Spacer(Modifier.height(MoyiDimens.Field.LabelGap))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = MoyiDimens.Field.BoxMinHeight)
                .clip(shapes.radius)
                .background(colors.fieldFill(state))
                .border(stroke.width, stroke.color, shapes.radius)
                .clickable(enabled = enabled, role = Role.DropdownList, onClick = onClick)
                .semantics(mergeDescendants = true) {
                    contentDescription = label
                    if (error != null) error(error)
                }
                .padding(horizontal = MoyiDimens.Field.Padding),
            horizontalArrangement = Arrangement.spacedBy(MoyiDimens.Field.ItemGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MoyiText(
                text = value,
                modifier = Modifier.weight(1f).padding(vertical = MoyiDimens.Field.Padding),
                tone = if (state == FieldState.Disabled) TextTone.Muted else TextTone.Ink,
            )
            Icon(
                imageVector = MoyiIcons.ChevronDown,
                contentDescription = null,
                tint = colors.ink,
                modifier = Modifier.size(MoyiDimens.IconGlyph),
            )
        }
        val below = error ?: helper
        if (below != null) {
            Spacer(Modifier.height(MoyiDimens.Field.SupportGap))
            MoyiText(text = below, tone = if (error != null) TextTone.Danger else TextTone.Muted)
        }
    }
}

@Composable
private fun RevealToggle(revealed: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    val description = stringResource(if (revealed) Res.string.ds_password_hide else Res.string.ds_password_show)
    Box(
        modifier = Modifier
            .width(MoyiDimens.Field.RevealTargetWidth)
            .heightIn(min = MoyiDimens.Field.RevealTargetMinHeight)
            // The Icon's contentDescription is the spoken name; an onClickLabel with
            // the same words made TalkBack say it twice.
            .clickable(enabled = enabled, role = Role.Button, onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (revealed) MoyiIcons.EyeOff else MoyiIcons.Eye,
            contentDescription = description,
            // `ink` in every state, Disabled included: the Figma Field set
            // (296:10, Type=Secure, State=Disabled) draws the eye in ink.
            tint = MoyiTheme.colors.ink,
            modifier = Modifier.size(MoyiDimens.IconGlyph),
        )
    }
}

@Preview
@Composable
private fun FieldDarkPreview() = FieldStates(darkTheme = true)

@Preview
@Composable
private fun FieldLightPreview() = FieldStates(darkTheme = false)

@Composable
private fun FieldStates(darkTheme: Boolean) {
    PreviewSurface(darkTheme) {
        Column {
            Field(value = "", onValueChange = {}, label = "Email")
            Spacer(Modifier.height(MoyiTheme.spacing.lg))
            Field(
                value = "correct horse",
                onValueChange = {},
                label = "Password",
                type = FieldType.Secure,
                helper = "8 characters or more. Anything you like.",
            )
            Spacer(Modifier.height(MoyiTheme.spacing.lg))
            Field(
                value = "hunter22",
                onValueChange = {},
                label = "Password",
                type = FieldType.Secure,
                error = "This password shows up on a list of leaked passwords. Pick a different one.",
            )
            Spacer(Modifier.height(MoyiTheme.spacing.lg))
            Field(value = "amara@example.com", onValueChange = {}, label = "Email", enabled = false)
            Spacer(Modifier.height(MoyiTheme.spacing.lg))
            Field(
                value = "K7Q2PD",
                onValueChange = {},
                label = "Invite code",
                type = FieldType.Code,
                helper = "Six characters. Capitals and numbers. Case does not matter.",
            )
            Spacer(Modifier.height(MoyiTheme.spacing.lg))
            Field(
                value = "Lagos",
                onValueChange = {},
                label = "Shared time zone",
                type = FieldType.Select,
                helper = "Your day starts and ends on Lagos time, for both of you, even when either of you travels.",
            )
        }
    }
}
