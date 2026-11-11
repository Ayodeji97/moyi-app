package com.moyi.app.feature.bond.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.moyi.app.designsystem.MoyiTheme
import com.moyi.app.designsystem.component.GroupedRow
import com.moyi.app.designsystem.component.GroupedToggleRow
import com.moyi.app.designsystem.component.MoyiText
import com.moyi.app.designsystem.component.TextRole

/**
 * How a row behaves when tapped, which decides its mark (owner ruling 2026-11-11):
 *
 * - [Navigates]: opens another screen or a confirmation. A chevron.
 * - [Opens]: opens an OS surface (the time picker). No mark.
 * - [Acts]: does it in place. No mark.
 *
 * No kind underlines: an underline stays only for a link inside a sentence.
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
 * A row outside a card (Notifications, Report, the zone picker): [GroupedRow], the design
 * system's row, with the chevron its [kind] calls for. Rows inside a card call
 * `GroupedCardScope.row` directly.
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
    GroupedRow(
        label = label,
        value = value,
        onClick = onClick,
        showChevron = kind == RowKind.Navigates,
        destructive = destructive,
        enabled = enabled,
        error = error,
        onClickLabel = onClickLabel,
    )
}

/** A row that toggles in place (Notifications): [GroupedToggleRow]. */
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
    GroupedToggleRow(label = label, value = value, checked = checked, onToggle = onToggle, hint = hint, enabled = enabled, error = error)
}
