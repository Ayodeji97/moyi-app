package com.moyi.app.designsystem.platform

import androidx.compose.runtime.Composable

/**
 * Whether the person asked the system for less motion, for movement that is **not** a
 * Compose animation (so Android's animator scale does not reach it on its own), such as
 * the recording waveform scrolling as samples arrive.
 *
 * - **Android**: "Remove animations" (the animator duration scale set to 0).
 * - **iOS**: Reduce Motion ([reduceMotionEnabled]).
 */
@Composable
expect fun rememberReduceMotion(): Boolean
