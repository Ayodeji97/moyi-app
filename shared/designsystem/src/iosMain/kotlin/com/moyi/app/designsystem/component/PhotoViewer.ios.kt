package com.moyi.app.designsystem.component

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.DialogProperties

// The capture cover is the card's own (ProtectFromCapture, called in the viewer): the
// dialog is a layer of the app's window, so iOS needs nothing here for [protectFromCapture].
@OptIn(ExperimentalComposeUiApi::class)
internal actual fun photoViewerProperties(protectFromCapture: Boolean): DialogProperties = DialogProperties(
    dismissOnBackPress = true,
    dismissOnClickOutside = false,
    usePlatformDefaultWidth = false,
    usePlatformInsets = false,
)
