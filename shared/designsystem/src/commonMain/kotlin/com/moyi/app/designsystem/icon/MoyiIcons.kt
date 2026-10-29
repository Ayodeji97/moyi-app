package com.moyi.app.designsystem.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The Lucide glyphs the design system uses (`tokens.md` §6: Lucide, ISC
 * licence, lucide-static v1.40.0, 24 px, 2 px stroke, round cap and join).
 *
 * The path data is copied verbatim from the lucide-static v1.40.0 SVGs, with
 * one change: every `<circle>` and `<rect>` is written as the equivalent
 * path (two arcs for a circle, lines and corner arcs for a rounded rect),
 * because a vector path has no circle or rect element.
 *
 * Strokes are drawn black and tinted at the call site with the `ink` token
 * (`tokens.md` §6: "override the stroke colour on the instance, never inside
 * the vector").
 */
object MoyiIcons {
    val Eye: ImageVector by lazy {
        lucide(
            "Eye",
            "M2.062 12.348a1 1 0 0 1 0-.696 10.75 10.75 0 0 1 19.876 0 1 1 0 0 1 0 .696 10.75 10.75 0 0 1-19.876 0",
            "M15 12a3 3 0 1 1-6 0a3 3 0 1 1 6 0",
        )
    }

    val EyeOff: ImageVector by lazy {
        lucide(
            "EyeOff",
            "M10.733 5.076a10.744 10.744 0 0 1 11.205 6.575 1 1 0 0 1 0 .696 10.747 10.747 0 0 1-1.444 2.49",
            "M14.084 14.158a3 3 0 0 1-4.242-4.242",
            "M17.479 17.499a10.75 10.75 0 0 1-15.417-5.151 1 1 0 0 1 0-.696 10.75 10.75 0 0 1 4.446-5.143",
            "m2 2 20 20",
        )
    }

    val Check: ImageVector by lazy { lucide("Check", "M20 6 9 17l-5-5") }

    /** The Select field's trailing glyph (lucide `chevron-down`). */
    val ChevronDown: ImageVector by lazy { lucide("ChevronDown", "m6 9 6 6 6-6") }

    /**
     * lucide `copy`. Its `<rect x=8 y=8 width=14 height=14 rx=2>` is written as a
     * path, for the same reason as `eye`'s circle.
     */
    val Copy: ImageVector by lazy {
        lucide(
            "Copy",
            "M10 8h10a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H10a2 2 0 0 1-2-2V10a2 2 0 0 1 2-2z",
            "M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2",
        )
    }

    /** Directional: mirrored under RTL with `autoMirror`. */
    val ChevronLeft: ImageVector by lazy { lucide("ChevronLeft", "m15 18-6-6 6-6", autoMirror = true) }

    /** Today's nav row: to Archive (states.md §3). `calendar`: two `<path>`s, `<rect x3 y3 18x18 rx2>`, `<path>`. */
    val Calendar: ImageVector by lazy {
        lucide(
            "Calendar",
            "M8 2v3",
            "M16 2v3",
            "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z",
            "M3 9h18",
        )
    }

    /** Today's nav row: to Bond settings (states.md §3). `settings`: one `<path>`, `<circle cx12 cy12 r3>`. */
    val Settings: ImageVector by lazy {
        lucide(
            "Settings",
            "M9.671 4.136a2.34 2.34 0 0 1 4.659 0 2.34 2.34 0 0 0 3.319 1.915 2.34 2.34 0 0 1 2.33 4.033 " +
                "2.34 2.34 0 0 0 0 3.831 2.34 2.34 0 0 1-2.33 4.033 2.34 2.34 0 0 0-3.319 1.915 2.34 2.34 0 0 1-4.659 0 " +
                "2.34 2.34 0 0 0-3.32-1.915 2.34 2.34 0 0 1-2.33-4.033 2.34 2.34 0 0 0 0-3.831A2.34 2.34 0 0 1 6.35 6.051 " +
                "a2.34 2.34 0 0 0 3.319-1.915",
            "M15 12a3 3 0 1 1-6 0a3 3 0 1 1 6 0",
        )
    }

    /** ComposeField: dismisses the prompt. `x`. */
    val Close: ImageVector by lazy { lucide("Close", "M18 6 6 18", "m6 6 12 12") }

    /** ComposeField: add a photo. `image`: `<rect x3 y3 18x18 rx2>`, `<circle cx9 cy9 r2>`, `<path>`. */
    val Image: ImageVector by lazy {
        lucide(
            "Image",
            "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z",
            "M11 9a2 2 0 1 1-4 0a2 2 0 1 1 4 0",
            "m21 15-3.086-3.086a2 2 0 0 0-2.828 0L6 21",
        )
    }

    /** ComposeField: record a voice note. `mic`: two `<path>`s and `<rect x9 y2 6x13 rx3>`. */
    val Mic: ImageVector by lazy {
        lucide(
            "Mic",
            "M12 19v3",
            "M19 10v2a7 7 0 0 1-14 0v-2",
            "M9 5a3 3 0 0 1 6 0v7a3 3 0 0 1-6 0z",
        )
    }

    private fun lucide(name: String, vararg paths: String, autoMirror: Boolean = false): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            autoMirror = autoMirror,
        ).apply {
            paths.forEach { data ->
                addPath(
                    pathData = addPathNodes(data),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()
}
