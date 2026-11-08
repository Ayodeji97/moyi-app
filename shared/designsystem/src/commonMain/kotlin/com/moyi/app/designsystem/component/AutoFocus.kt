package com.moyi.app.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester

/**
 * A [FocusRequester] that focuses its field once, when the screen first appears, so
 * the keyboard is already up on a screen whose whole job is typing (a rename, a pasted
 * code, the email to send a reset link to), as the platforms' own apps do.
 *
 * Attach it with `Modifier.focusRequester(...)` on a [Field]. It fires once per
 * screen visit, not on every recomposition, and not again when the person comes back
 * to the screen or the configuration changes: the flag is saved. Pass `enabled = false`
 * when the screen should open without the keyboard (for example, when the field is
 * already filled and a different one should take focus).
 */
@Composable
fun rememberAutoFocus(enabled: Boolean = true): FocusRequester {
    val requester = remember { FocusRequester() }
    var done by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(enabled) {
        if (enabled && !done) {
            done = true
            requester.requestFocus()
        }
    }
    return requester
}
