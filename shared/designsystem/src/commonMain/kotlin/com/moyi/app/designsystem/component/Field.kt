package com.moyi.app.designsystem.component

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.moyi.app.designsystem.MoyiTheme
import com.moyi.app.designsystem.icon.MoyiIcons
import com.moyi.app.designsystem.resources.Res
import com.moyi.app.designsystem.resources.ds_password_hide
import com.moyi.app.designsystem.resources.ds_password_show
import com.moyi.app.designsystem.theme.MoyiColors
import org.jetbrains.compose.resources.stringResource

/**
 * The `Field` types this PR builds. `tokens.md` §4 defines five
 * (`Text` / `Secure` / `Select` / `Code` / `Multiline`); the other three are
 * added when a screen needs them.
 */
enum class FieldType { Text, Secure }

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

/**
 * Focus and Error change the stroke **weight**, not only the colour
 * (NFR-104: no information by colour alone), so both survive greyscale and
 * colour blindness. Focus is `ink`, not an accent (`tokens.md` §4).
 * Disabled drops to the decorative `outline`; WCAG 1.4.11 exempts inactive
 * controls, and the muted label and text carry the state too.
 */
fun MoyiColors.fieldStroke(state: FieldState): FieldStroke = when (state) {
    FieldState.Idle -> FieldStroke(1.dp, outlineStrong)
    FieldState.Focus -> FieldStroke(2.dp, ink)
    FieldState.Error -> FieldStroke(2.dp, danger)
    FieldState.Disabled -> FieldStroke(1.dp, outline)
}

/**
 * The one input control (`tokens.md` §4, component 8).
 *
 * - The [label] is always visible above the box. No placeholder stands in
 *   for it: a placeholder disappears the moment someone types.
 * - The box is at least 56 tall with radius 12; it grows with the font.
 * - [error], when set, replaces [helper], is drawn in `danger`, and is
 *   exposed to screen readers as the field's error.
 * - [FieldType.Secure] hides the text and carries an eye toggle: a second
 *   48 dp target inside the row, with a spoken label that says what a tap
 *   will do.
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
) {
    val colors = MoyiTheme.colors
    val spacing = MoyiTheme.spacing
    val shapes = MoyiTheme.shapes
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    var revealed by rememberSaveable { mutableStateOf(false) }

    val state = fieldStateOf(enabled = enabled, hasError = error != null, focused = focused)
    val stroke = colors.fieldStroke(state)
    val textColor = if (state == FieldState.Disabled) colors.inkMuted else colors.ink
    val secure = type == FieldType.Secure
    val effectiveKeyboard = if (secure && keyboardOptions == KeyboardOptions.Default) {
        KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false)
    } else {
        keyboardOptions
    }

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .semantics { if (error != null) error(error) },
        enabled = enabled,
        singleLine = true,
        textStyle = MoyiTheme.type.body.copy(color = textColor),
        cursorBrush = SolidColor(colors.ink),
        keyboardOptions = effectiveKeyboard,
        keyboardActions = keyboardActions,
        visualTransformation = if (secure && !revealed) PasswordVisualTransformation() else VisualTransformation.None,
        interactionSource = interactionSource,
        // Everything inside the decoration box belongs to the text field's
        // semantics node, so the label is read with the field ("Email, edit
        // box") instead of as a separate, unconnected piece of text.
        decorationBox = { innerTextField ->
            Column {
                MoyiText(text = label, role = TextRole.Label)
                Spacer(Modifier.height(spacing.sm))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = BoxMinHeight)
                        .border(stroke.width, stroke.color, shapes.radius)
                        .padding(start = spacing.md, end = if (secure) 0.dp else spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f).padding(vertical = spacing.sm)) { innerTextField() }
                    if (secure) {
                        RevealToggle(revealed = revealed, enabled = enabled, onToggle = { revealed = !revealed })
                    }
                }
                val below = error ?: helper
                if (below != null) {
                    Spacer(Modifier.height(spacing.sm))
                    MoyiText(text = below, tone = if (error != null) TextTone.Danger else TextTone.Muted)
                }
            }
        },
    )
}

@Composable
private fun RevealToggle(revealed: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    val description = stringResource(if (revealed) Res.string.ds_password_hide else Res.string.ds_password_show)
    Box(
        modifier = Modifier
            .size(MoyiTheme.spacing.touchTarget)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = description, onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (revealed) MoyiIcons.EyeOff else MoyiIcons.Eye,
            contentDescription = description,
            tint = if (enabled) MoyiTheme.colors.ink else MoyiTheme.colors.inkMuted,
        )
    }
}

/** `tokens.md` §4: 56 tall (65 for `Code`, not built yet). A floor, so 200 % text still fits. */
private val BoxMinHeight = 56.dp

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
        }
    }
}
