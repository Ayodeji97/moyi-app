package com.moyi.app.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.moyi.app.designsystem.MoyiTheme

/**
 * Preview frame: the theme in a fixed mode on the `surface` ground, with an
 * optional font scale. The scale is applied through [LocalDensity] rather
 * than `@Preview(fontScale)`, so the same frame works in every renderer and
 * in the in-app gallery.
 */
@Composable
internal fun PreviewSurface(
    darkTheme: Boolean,
    fontScale: Float = 1f,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale * fontScale)) {
        MoyiTheme(darkTheme = darkTheme) {
            Box(
                Modifier
                    .background(MoyiTheme.colors.surface)
                    .padding(MoyiTheme.spacing.gutter),
            ) { content() }
        }
    }
}
