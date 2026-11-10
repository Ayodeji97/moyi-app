package com.moyi.app.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.toUpperCase
import androidx.compose.ui.tooling.preview.Preview
import com.moyi.app.designsystem.MoyiTheme
import com.moyi.app.designsystem.theme.MoyiColors
import androidx.compose.ui.unit.em
import com.moyi.app.designsystem.theme.MoyiTypography

/** One of the six text styles in `tokens.md` §2. */
enum class TextRole {
    Entry, Title, Body, Label, Action, Code,

    /**
     * **PROPOSED** (§69): the `label` size and weight in sentence case, with no tracking and no
     * uppercasing. For chips, pills, legends, field captions: small text that is not an author
     * line, a section head or a date header (those stay [Label]).
     */
    Tag,
}

/**
 * Which colour token the text takes. A token, not a [Color], so a screen
 * cannot pass a literal colour. `Danger` is for error text only.
 */
enum class TextTone { Ink, Muted, Danger }

/**
 * Text in a design-system style. Screens use this instead of `Text` with a
 * hand-set size or colour.
 *
 * - [TextRole.Label] is uppercased here, with the reader's locale (a Turkish
 *   `i` uppercases differently), because a `TextStyle` cannot carry a
 *   transform. The screen reader still gets the original casing: some
 *   readers spell short all-caps words letter by letter.
 * - [TextRole.Tag] is the same size and weight, kept as typed and untracked.
 * - The default tone is `Muted` for labels, tags and `Ink` for everything else.
 * - No `maxLines` by default: at 200 % font scale text wraps, it never
 *   truncates (NFR-102). The one place that cuts text is the Archive list
 *   (states.md §6: two lines with an ellipsis, the day view shows it all);
 *   it passes [maxLines] with [ellipsis].
 */
@Composable
fun MoyiText(
    text: String,
    modifier: Modifier = Modifier,
    role: TextRole = TextRole.Body,
    tone: TextTone = if (role == TextRole.Label || role == TextRole.Tag) TextTone.Muted else TextTone.Ink,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    /** End a cut line with an ellipsis instead of clipping it. Ignored for labels. */
    ellipsis: Boolean = false,
) {
    val style = MoyiTheme.type.styleFor(role)
    val color = MoyiTheme.colors.colorFor(tone)
    if (role == TextRole.Label) {
        Text(
            text = displayedText(role, text),
            modifier = modifier.clearAndSetSemantics { this.text = AnnotatedString(text) },
            style = style,
            color = color,
            textAlign = textAlign,
            maxLines = maxLines,
            overflow = TextOverflow.Clip,
        )
    } else {
        Text(
            text = text,
            modifier = modifier,
            style = style,
            color = color,
            textAlign = textAlign,
            maxLines = maxLines,
            overflow = if (ellipsis) TextOverflow.Ellipsis else TextOverflow.Clip,
        )
    }
}

/** What is drawn for [text] in [role]: capitals for [TextRole.Label] (reader's locale), as typed for every other role. */
internal fun displayedText(role: TextRole, text: String): String =
    if (role == TextRole.Label) text.toUpperCase(Locale.current) else text

/** `label` without its tracking: tracking spaces capitals apart, and a [TextRole.Tag] is sentence case. */
val MoyiTypography.tag: TextStyle get() = label.copy(letterSpacing = 0.em)

/** The style for a role. Exposed for components that draw text themselves (text fields). */
fun MoyiTypography.styleFor(role: TextRole): TextStyle = when (role) {
    TextRole.Entry -> entry
    TextRole.Title -> title
    TextRole.Body -> body
    TextRole.Label -> label
    TextRole.Tag -> tag
    TextRole.Action -> action
    TextRole.Code -> code
}

fun MoyiColors.colorFor(tone: TextTone): Color = when (tone) {
    TextTone.Ink -> ink
    TextTone.Muted -> inkMuted
    TextTone.Danger -> danger
}

@Preview
@Composable
private fun MoyiTextDarkPreview() = TypeSpecimen(darkTheme = true)

@Preview
@Composable
private fun MoyiTextLightPreview() = TypeSpecimen(darkTheme = false)

@Composable
private fun TypeSpecimen(darkTheme: Boolean) {
    PreviewSurface(darkTheme) {
        Column {
            MoyiText("Written by Amara", role = TextRole.Label)
            MoyiText("Mọyì. You stayed up to help me finish it.", role = TextRole.Entry)
            MoyiText("Check your email", role = TextRole.Title)
            MoyiText("We sent a link to amara@example.com. Open the email, copy the link and paste it below.")
            MoyiText("Check again", role = TextRole.Action)
            MoyiText("K7Q2PD", role = TextRole.Code, modifier = Modifier.padding(top = MoyiTheme.spacing.md))
        }
    }
}
