package com.moyi.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import com.moyi.app.designsystem.MoyiTheme
import com.moyi.app.designsystem.component.ConsentCheckbox
import com.moyi.app.designsystem.component.Field
import com.moyi.app.designsystem.component.FieldType
import com.moyi.app.designsystem.component.InlineError
import com.moyi.app.designsystem.component.MoyiScaffold
import com.moyi.app.designsystem.component.MoyiText
import com.moyi.app.designsystem.component.PrimaryAction
import com.moyi.app.designsystem.component.PrimaryActionState
import com.moyi.app.designsystem.component.TextLink
import com.moyi.app.designsystem.component.TextLinkStyle
import com.moyi.app.designsystem.component.TextRole
import moyi.shared.generated.resources.Res
import moyi.shared.generated.resources.gallery_action
import moyi.shared.generated.resources.gallery_check_again
import moyi.shared.generated.resources.gallery_checking
import moyi.shared.generated.resources.gallery_consent
import moyi.shared.generated.resources.gallery_disabled_label
import moyi.shared.generated.resources.gallery_disabled_value
import moyi.shared.generated.resources.gallery_email
import moyi.shared.generated.resources.gallery_entry
import moyi.shared.generated.resources.gallery_forgot
import moyi.shared.generated.resources.gallery_heading
import moyi.shared.generated.resources.gallery_inline_error_figma
import moyi.shared.generated.resources.gallery_label
import moyi.shared.generated.resources.gallery_password
import moyi.shared.generated.resources.gallery_password_breached
import moyi.shared.generated.resources.gallery_password_helper
import moyi.shared.generated.resources.gallery_privacy
import moyi.shared.generated.resources.gallery_terms
import moyi.shared.generated.resources.gallery_wordmark
import org.jetbrains.compose.resources.stringResource

/**
 * A temporary component gallery, so the design system can be seen on both
 * platforms. Laid out on the Figma sign-up frame's rhythm (eyebrow, 64,
 * heading, 24, fields 24 apart; the action block pinned). PR #6 replaces it.
 */
@Composable
@Preview
fun App() {
    MoyiTheme {
        ComponentGallery()
    }
}

@Composable
private fun ComponentGallery() {
    // Gallery-only state. Real screens hoist this into their ViewModel.
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var consent by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    val spacing = MoyiTheme.spacing

    MoyiScaffold(
        onBack = { checking = false },
        action = {
            InlineError(stringResource(Res.string.gallery_inline_error_figma))
            Spacer(Modifier.height(spacing.md))
            PrimaryAction(
                text = stringResource(Res.string.gallery_action),
                onClick = {},
                state = if (consent) PrimaryActionState.Idle else PrimaryActionState.Disabled,
            )
            Spacer(Modifier.height(spacing.md))
            PrimaryAction(
                text = stringResource(Res.string.gallery_check_again),
                onClick = { checking = true },
                state = if (checking) {
                    PrimaryActionState.Sending(stringResource(Res.string.gallery_checking))
                } else {
                    PrimaryActionState.Idle
                },
            )
        },
    ) {
        MoyiText(stringResource(Res.string.gallery_label), role = TextRole.Label)
        Spacer(Modifier.height(spacing.xxxl))
        MoyiText(stringResource(Res.string.gallery_heading), role = TextRole.Title)
        Spacer(Modifier.height(spacing.lg))
        Field(
            value = email,
            onValueChange = { email = it },
            label = stringResource(Res.string.gallery_email),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
        )
        Spacer(Modifier.height(spacing.lg))
        Field(
            value = password,
            onValueChange = { password = it },
            label = stringResource(Res.string.gallery_password),
            type = FieldType.Secure,
            helper = stringResource(Res.string.gallery_password_helper),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        )
        Spacer(Modifier.height(spacing.lg))
        TextLink(stringResource(Res.string.gallery_forgot), onClick = {}, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(spacing.lg))
        Field(
            value = "hunter22",
            onValueChange = {},
            label = stringResource(Res.string.gallery_password),
            type = FieldType.Secure,
            error = stringResource(Res.string.gallery_password_breached),
        )
        Spacer(Modifier.height(spacing.lg))
        Field(
            value = stringResource(Res.string.gallery_disabled_value),
            onValueChange = {},
            label = stringResource(Res.string.gallery_disabled_label),
            enabled = false,
        )
        Spacer(Modifier.height(spacing.lg))
        ConsentCheckbox(checked = consent, onCheckedChange = { consent = it }, text = stringResource(Res.string.gallery_consent))
        Spacer(Modifier.height(spacing.md))
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            TextLink(stringResource(Res.string.gallery_terms), onClick = {}, style = TextLinkStyle.Body)
            TextLink(stringResource(Res.string.gallery_privacy), onClick = {}, style = TextLinkStyle.Body)
        }
        Spacer(Modifier.height(spacing.lg))
        MoyiText(stringResource(Res.string.gallery_wordmark), role = TextRole.Title)
        Spacer(Modifier.height(spacing.md))
        MoyiText(stringResource(Res.string.gallery_entry), role = TextRole.Entry)
    }
}
