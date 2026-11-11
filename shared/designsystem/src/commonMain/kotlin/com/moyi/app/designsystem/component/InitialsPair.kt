package com.moyi.app.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.moyi.app.designsystem.MoyiTheme
import com.moyi.app.designsystem.theme.MoyiDimens

/**
 * The first letter of [name], upper-cased: "Pax" is "P", "ọlá" is "Ọ". A name that starts
 * with a character outside the Basic Multilingual Plane (an emoji, held as a surrogate
 * pair) keeps both halves, so the letter is never cut in two. Blank gives "".
 */
fun initialOf(name: String): String {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return ""
    val length = if (trimmed.length > 1 && trimmed[0].isHighSurrogate() && trimmed[1].isLowSurrogate()) 2 else 1
    return trimmed.substring(0, length).uppercase()
}

/**
 * The members of a bond as overlapping initials circles, the second over the first: the
 * header of Bond settings. There is no status dot and never an "online": the circles say who
 * is in the bond, not whether they are here. Decorative; the screen that places it describes
 * the pair in words. [second] null draws one circle (nobody has joined yet).
 */
@Composable
fun InitialsPair(first: String, second: String?, modifier: Modifier = Modifier) {
    val step = MoyiDimens.InitialsPair.Size - MoyiDimens.InitialsPair.Overlap
    val width = if (second != null) MoyiDimens.InitialsPair.Size + step else MoyiDimens.InitialsPair.Size
    Box(modifier.width(width)) {
        Circle(first)
        if (second != null) Box(Modifier.offset(x = step)) { Circle(second) }
    }
}

@Composable
private fun Circle(initial: String) {
    val colors = MoyiTheme.colors
    Box(
        modifier = Modifier
            .size(MoyiDimens.InitialsPair.Size)
            .background(colors.surface, MoyiTheme.shapes.full)
            .border(MoyiDimens.InitialsPair.Stroke, colors.outlineStrong, MoyiTheme.shapes.full),
        contentAlignment = Alignment.Center,
    ) {
        MoyiText(initial, role = TextRole.Title)
    }
}

@Preview
@Composable
private fun InitialsPairPreview() {
    MoyiTheme(darkTheme = false) {
        Box(Modifier.background(MoyiTheme.colors.surfaceRaised)) { InitialsPair("V", "P") }
    }
}
