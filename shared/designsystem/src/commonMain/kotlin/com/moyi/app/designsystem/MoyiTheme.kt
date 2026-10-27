package com.moyi.app.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * Placeholder theme. PR #5 replaces it with the Rosewood tokens (colour, type,
 * spacing) from the moyi-design-system skill; until then it is Material 3's
 * defaults, so screens already go through one theme entry point.
 */
@Composable
fun MoyiTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}
