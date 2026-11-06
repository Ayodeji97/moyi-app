package com.moyi.app.feature.bond.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import com.moyi.app.designsystem.MoyiTheme
import com.moyi.app.designsystem.component.InlineError
import com.moyi.app.designsystem.component.MoyiText
import com.moyi.app.designsystem.component.TextRole

/**
 * How a row behaves when tapped, which decides its styling (states.md §8, tokens.md §1:
 * **an underline means it goes somewhere**).
 *
 * - [Navigates]: opens a screen or a confirmation. Underlined: the value if there is one,
 *   otherwise the label.
 * - [Opens]: opens an OS surface (the time picker). No underline.
 * - [Acts]: does it in place (sign out). No underline.
 */
enum class RowKind { Navigates, Opens, Acts }

/**
 * A group of rows under its `label` heading, 16 above the rows (Figma 562:1669: the
 * heading 19 tall, the rows 35 below its top).
 */
@Composable
internal fun SettingsGroup(heading: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        MoyiText(heading, role = TextRole.Label, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(MoyiTheme.spacing.md))
        content()
    }
}

/**
 * One Bond settings row: the label at the start and its value at the end, both `body`,
 * in a 56 row (Figma 396:13: text 24 tall at y 16). The whole row is the target.
 *
 * Built here, not in the design system: Figma draws these rows as loose frames, not a
 * component (docs/missing-components.md). [destructive] is `danger` with the underline
 * kept, so colour is never the only cue (NFR-104); [error] sits under the row, input kept.
 */
@Composable
internal fun SettingsRow(
    label: String,
    onClick: () -> Unit,
    kind: RowKind,
    value: String? = null,
    destructive: Boolean = false,
    enabled: Boolean = true,
    error: String? = null,
    /** What a tap does, spoken by assistive technology when the label alone does not say it. */
    onClickLabel: String? = null,
) {
    val colors = MoyiTheme.colors
    val underline = kind == RowKind.Navigates
    val body = MoyiTheme.type.body
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClickLabel = onClickLabel, role = Role.Button, onClick = onClick)
                .padding(vertical = MoyiTheme.spacing.md),
            horizontalArrangement = Arrangement.spacedBy(MoyiTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = if (underline && value == null) body.copy(textDecoration = TextDecoration.Underline) else body,
                color = if (destructive) colors.danger else colors.ink,
            )
            if (value != null) {
                Text(
                    text = value,
                    style = if (underline) body.copy(textDecoration = TextDecoration.Underline) else body,
                    color = colors.ink,
                    textAlign = TextAlign.End,
                )
            }
        }
        if (error != null) {
            InlineError(error)
            Spacer(Modifier.height(MoyiTheme.spacing.sm))
        }
    }
}

/**
 * A row that toggles in place (Rest days): no underline, a switch to assistive
 * technology, with its state spoken as the value says it.
 */
@Composable
internal fun SettingsToggleRow(
    label: String,
    value: String,
    checked: Boolean,
    onToggle: () -> Unit,
    hint: String,
    enabled: Boolean = true,
    error: String? = null,
) {
    val colors = MoyiTheme.colors
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = { onToggle() })
                .semantics {
                    stateDescription = value
                    onClick(label = hint) { onToggle(); true }
                }
                .padding(vertical = MoyiTheme.spacing.md),
            horizontalArrangement = Arrangement.spacedBy(MoyiTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, modifier = Modifier.weight(1f), style = MoyiTheme.type.body, color = colors.ink)
            Text(value, style = MoyiTheme.type.body, color = colors.ink, textAlign = TextAlign.End)
        }
        if (error != null) {
            InlineError(error)
            Spacer(Modifier.height(MoyiTheme.spacing.sm))
        }
    }
}
