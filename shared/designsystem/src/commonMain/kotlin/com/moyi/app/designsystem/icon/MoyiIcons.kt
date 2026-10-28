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
 * one change: `eye`'s `<circle cx=12 cy=12 r=3>` is written as two arcs,
 * because a vector path has no circle element.
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

    /** Directional: mirrored under RTL with `autoMirror`. */
    val ChevronLeft: ImageVector by lazy { lucide("ChevronLeft", "m15 18-6-6 6-6", autoMirror = true) }

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
