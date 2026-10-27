package com.moyi.app.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
 * **Not one of the ten components.** `states.md` §1 puts "18+ confirmation,
 * Terms and Privacy consent" on Sign up, and neither `tokens.md` nor
 * `states.md` says how it looks. This is the minimum that is accessible and
 * stays inside the tokens, and it is on `docs/missing-components.md` for the
 * owner to design:
 *
 * - The **whole row** toggles, with `Role.Checkbox`, and is at least 48 tall.
 * - Unchecked is an empty square with a 2 dp `outlineStrong` border (the
 *   border is the only thing identifying it, so it takes the strong token);
 *   checked fills with `ink` and draws the Lucide check in `surface`. The two
 *   differ by shape as well as colour.
 * - The box is square-cornered: the system has radius 12 and full, and 12
 *   on a 20 dp box would read as a radio button.
 * - The Terms and Privacy links are not wired yet: `copy.md`'s inline-link
 *   pattern (ink plus underline inside an inkMuted sentence) needs the URLs.
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
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onCheckedChange)
            .padding(vertical = MoyiDimens.ConsentCheckbox.RowPaddingVertical),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(MoyiDimens.ConsentCheckbox.Box)
                .background(if (checked) colors.ink else colors.surface, RectangleShape)
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
        MoyiText(text = text, tone = if (enabled) TextTone.Ink else TextTone.Muted, modifier = Modifier.weight(1f))
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
