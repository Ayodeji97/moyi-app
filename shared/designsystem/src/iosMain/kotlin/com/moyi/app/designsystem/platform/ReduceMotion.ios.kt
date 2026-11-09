package com.moyi.app.designsystem.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
actual fun rememberReduceMotion(): Boolean = remember { reduceMotionEnabled() }
