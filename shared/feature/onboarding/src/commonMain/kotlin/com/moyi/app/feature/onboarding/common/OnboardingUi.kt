package com.moyi.app.feature.onboarding.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import com.moyi.app.designsystem.MoyiTheme
import com.moyi.app.designsystem.component.MoyiText
import com.moyi.app.designsystem.component.TextRole

/**
 * The eyebrow and heading a detail form opens with (onboarding spec §2): the
 * eyebrow in `label`, then the heading in `title`, a semantic heading.
 *
 * [gap] is 64 on a resting form, so the heading lands at 171 under a back
 * target and at 107 on a root screen. Join's manual entry (309:42) is drawn
 * keyboard-up, with 16 throughout.
 *
 * The same shape as auth's `AuthHeader`, which is internal to that module.
 * Two copies of three lines is cheaper than a shared module for them; if a
 * third feature needs it, it moves to designsystem.
 */
@Composable
internal fun OnboardingHeader(eyebrow: String, heading: String, gap: Dp = MoyiTheme.spacing.xxxl) {
    MoyiText(eyebrow, role = TextRole.Label)
    Spacer(Modifier.height(gap))
    MoyiText(heading, role = TextRole.Title, modifier = Modifier.semantics { heading() })
}

/**
 * Places the first child at the top and centres the rest in the space left
 * below it, [gap] down. Join · cannot be used (310:23): the eyebrow stays at
 * the top and the StateBlock is centred in the content under it.
 */
internal class TopThenCentered(private val gap: Dp) : Arrangement.Vertical {
    override val spacing: Dp = gap

    override fun Density.arrange(totalSize: Int, sizes: IntArray, outPositions: IntArray) {
        if (sizes.isEmpty()) return
        outPositions[0] = 0
        val top = sizes[0] + gap.roundToPx()
        val rest = sizes.drop(1).sum()
        var y = top + ((totalSize - top - rest) / 2).coerceAtLeast(0)
        for (i in 1 until sizes.size) {
            outPositions[i] = y
            y += sizes[i]
        }
    }
}
