package com.moyi.app.designsystem.platform

import androidx.compose.foundation.Indication
import androidx.compose.material3.ripple
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.moyi.app.designsystem.icon.MoyiIcons

internal actual val PlatformBackIcon: ImageVector get() = MoyiIcons.ArrowBack

internal actual fun platformPressIndication(color: Color): Indication = ripple(color = color)

internal actual val DismissKeyboardOnTapAndDrag: Boolean = false
