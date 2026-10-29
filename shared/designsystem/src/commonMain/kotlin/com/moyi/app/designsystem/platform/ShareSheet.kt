package com.moyi.app.designsystem.platform

import androidx.compose.runtime.Composable

/**
 * The platform's own share sheet (figma-deviations.md C1): `ACTION_SEND` in a
 * chooser on Android, `UIActivityViewController` on iOS. Never a custom one.
 *
 * Fire and forget: the sheet closing says nothing about whether anything was
 * sent, so nothing waits on it (states.md §1c, "Share cancelled" has no state).
 */
fun interface ShareSheet {
    fun share(text: String)
}

@Composable
expect fun rememberShareSheet(): ShareSheet

/** Puts plain text on the system clipboard. */
fun interface TextClipboard {
    fun copy(text: String)
}

@Composable
expect fun rememberTextClipboard(): TextClipboard
