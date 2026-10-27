package com.moyi.app.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import com.moyi.app.designsystem.MoyiTheme
import com.moyi.app.designsystem.theme.MoyiDimens
import com.moyi.app.designsystem.icon.MoyiIcons

/**
 * The sign-up consent row: "I am 18 or over, and I agree to the Terms and
 * the Privacy Policy." (`signup.consent`, FR-011).
 *
 * **Not one of the ten components**, and `tokens.md` and `states.md` do not
 * say how it looks. Figma draws it as a loose frame on Sign up
 * (`docs/figma/auth-placement-spec.md` §3.1, row 6), and this follows that:
 *
 * - The **whole row** toggles, with `Role.Checkbox`, and is at least 48 tall.
 * - A 24 x 24 square, radius 0, with a 1 px `outlineStrong` border and no
 *   fill, top-aligned with the text, 16 from it. Text is `body` `inkMuted`.
 * - **Checked is not drawn in Figma.** Here it fills with `ink` and draws the
 *   Lucide check in `surface`, so the states differ by shape as well as
 *   colour. On `docs/missing-components.md` for the owner.
 * - Figma puts "Terms" and "Privacy Policy" as separate `TextLink`s
 *   (`TextLinkStyle.Body`) on their own row, so they never toggle consent.
 */
@Composable
fun ConsentCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = MoyiTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MoyiDimens.ConsentCheckbox.RowMinHeight)
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(MoyiDimens.ConsentCheckbox.Box)
                .background(if (checked) colors.ink else Color.Transparent, RectangleShape)
                .border(MoyiDimens.ConsentCheckbox.BoxStroke, if (checked) colors.ink else colors.outlineStrong, RectangleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Icon(
                    imageVector = MoyiIcons.Check,
                    // The row's checkbox role already says "checked".
                    contentDescription = null,
                    tint = colors.surface,
                    modifier = Modifier.size(MoyiDimens.ConsentCheckbox.CheckGlyph),
                )
            }
        }
        Spacer(Modifier.width(MoyiDimens.ConsentCheckbox.Gap))
        MoyiText(text = text, tone = TextTone.Muted, modifier = Modifier.weight(1f))
    }
}

@Preview
@Composable
private fun ConsentCheckboxDarkPreview() = PreviewSurface(darkTheme = true) { ConsentSamples() }

@Preview
@Composable
private fun ConsentCheckboxLightPreview() = PreviewSurface(darkTheme = false) { ConsentSamples() }

@Composable
private fun ConsentSamples() {
    val consent = "I am 18 or over, and I agree to the Terms and the Privacy Policy."
    Column {
        ConsentCheckbox(checked = false, onCheckedChange = {}, text = consent)
        ConsentCheckbox(checked = true, onCheckedChange = {}, text = consent)
    }
}
