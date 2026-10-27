package com.moyi.app.feature.auth

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.moyi.app.feature.auth.resources.Res
import com.moyi.app.feature.auth.resources.auth_placeholder_title
import org.jetbrains.compose.resources.stringResource

/**
 * Placeholder for the auth flow (register, verify, sign in), which arrives in
 * PR #6. It reads a string from this module's own resources, which proves the
 * per-module Res class works on both platforms.
 */
@Composable
fun AuthPlaceholder(modifier: Modifier = Modifier) {
    Text(text = stringResource(Res.string.auth_placeholder_title), modifier = modifier)
}
