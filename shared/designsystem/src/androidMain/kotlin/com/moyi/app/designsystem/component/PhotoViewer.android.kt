package com.moyi.app.designsystem.component

import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy

// SecureOn, not Inherit: the viewer must be protected even if it outlived the card that
// set the flag on the activity, and Inherit reads the parent only when the window is set up.
internal actual fun photoViewerProperties(protectFromCapture: Boolean): DialogProperties = DialogProperties(
    dismissOnBackPress = true,
    dismissOnClickOutside = false,
    securePolicy = if (protectFromCapture) SecureFlagPolicy.SecureOn else SecureFlagPolicy.Inherit,
    usePlatformDefaultWidth = false,
    decorFitsSystemWindows = false,
)
