package com.moyi.app.feature.today.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled

/** iOS Settings › Accessibility › Motion › Reduce Motion. */
@Composable
internal actual fun isReducedMotionEnabled(): Boolean = remember { UIAccessibilityIsReduceMotionEnabled() }
