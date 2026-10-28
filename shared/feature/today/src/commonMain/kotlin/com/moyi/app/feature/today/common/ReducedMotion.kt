package com.moyi.app.feature.today.common

import androidx.compose.runtime.Composable

/**
 * Whether the person has asked the platform for less motion (SKILL.md §8, `14` §6):
 * iOS *Reduce Motion*, Android *Remove animations* (animator duration scale 0).
 *
 * Read once per composition of the reveal: the reveal is the product's one
 * animation, and under this setting it becomes a cross-fade that still reads as
 * a reveal (SKILL.md §5).
 */
@Composable
internal expect fun isReducedMotionEnabled(): Boolean
