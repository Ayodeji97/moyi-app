package com.moyi.app.feature.onboarding.createbond

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import com.moyi.app.designsystem.MoyiTheme
import com.moyi.app.designsystem.component.Field
import com.moyi.app.designsystem.component.MoyiScaffold
import com.moyi.app.designsystem.component.MoyiText
import com.moyi.app.designsystem.component.ScreenHeader
import com.moyi.app.designsystem.component.TextTone
import com.moyi.app.designsystem.icon.MoyiIcons
import com.moyi.app.designsystem.theme.MoyiDimens
import com.moyi.app.feature.onboarding.resources.Res
import com.moyi.app.feature.onboarding.resources.onboarding_eyebrow_new_bond
import com.moyi.app.feature.onboarding.resources.timezone_picker_empty
import com.moyi.app.feature.onboarding.resources.timezone_picker_heading
import com.moyi.app.feature.onboarding.resources.timezone_picker_search_label
import org.jetbrains.compose.resources.stringResource

/**
 * The shared time zone's picker, opened from the field on Create bond.
 *
 * **Interim layout, not in Rosewood.** The design system has no picker, list
 * row or search component and Figma draws no frame for this, so it is composed
 * from what exists, as plainly as possible: the scaffold with its back arrow,
 * the screen header, a `Field` as the search, and one row per zone built from
 * `MoyiText` in a full-width target at least 48 tall. The owner's design ruling
 * is owed (see the PR).
 *
 * Everything scrolls together, header and search included, so at 200 % font the
 * list keeps the height it needs. Each row is one radio-button control that
 * says its city and region ("London, Europe") and whether it is the chosen one;
 * the chosen row also carries a check, so the choice is never colour alone.
 */
@Composable
internal fun TimeZonePicker(
    picker: TimeZonePickerState,
    onAction: (CreateBondAction) -> Unit,
) {
    val spacing = MoyiTheme.spacing
    val keyboard = LocalSoftwareKeyboardController.current
    MoyiScaffold(
        onBack = { onAction(CreateBondAction.OnTimeZoneDismiss) },
        scrollContent = false,
    ) {
        LazyColumn(Modifier.fillMaxSize()) {
            item(key = HEADER_KEY) {
                Column {
                    ScreenHeader(
                        eyebrow = stringResource(Res.string.onboarding_eyebrow_new_bond),
                        heading = stringResource(Res.string.timezone_picker_heading),
                    )
                    Spacer(Modifier.height(spacing.lg))
                    Field(
                        value = picker.query,
                        onValueChange = { onAction(CreateBondAction.OnTimeZoneQueryChange(it)) },
                        label = stringResource(Res.string.timezone_picker_search_label),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            autoCorrectEnabled = false,
                            imeAction = ImeAction.Search,
                        ),
                        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                    )
                    Spacer(Modifier.height(spacing.md))
                    if (picker.zones.isEmpty()) {
                        MoyiText(stringResource(Res.string.timezone_picker_empty), tone = TextTone.Muted)
                    }
                }
            }
            items(picker.zones, key = { it.id }) { zone ->
                ZoneRow(zone) {
                    keyboard?.hide()
                    onAction(CreateBondAction.OnTimeZoneSelect(zone.id))
                }
            }
        }
    }
}

@Composable
private fun ZoneRow(zone: TimeZoneOption, onClick: () -> Unit) {
    val spacing = MoyiTheme.spacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MoyiDimens.TouchTarget)
            // One control per row: selectable merges the city and region into it and
            // says "selected" on the chosen one.
            .selectable(selected = zone.selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            MoyiText(zone.city)
            if (zone.region.isNotEmpty()) MoyiText(zone.region, tone = TextTone.Muted)
        }
        if (zone.selected) {
            Icon(
                imageVector = MoyiIcons.Check,
                contentDescription = null,
                tint = MoyiTheme.colors.ink,
                modifier = Modifier.size(MoyiDimens.IconGlyph),
            )
        }
    }
}

private const val HEADER_KEY = "header"

private val previewPicker = TimeZonePickerState(
    zones = listOf("Africa/Lagos", "Europe/London", "America/Argentina/Buenos_Aires", "Asia/Kolkata").map {
        TimeZoneOption(it, CreateBondViewModel.cityOf(it), CreateBondViewModel.regionOf(it), selected = it == "Africa/Lagos")
    },
)

@Preview
@Composable
private fun TimeZonePickerPreview() {
    MoyiTheme(darkTheme = true) { TimeZonePicker(previewPicker, onAction = {}) }
}

@Preview
@Composable
private fun TimeZonePickerLightPreview() {
    MoyiTheme(darkTheme = false) { TimeZonePicker(previewPicker, onAction = {}) }
}

@Preview
@Composable
private fun TimeZonePickerNoMatchPreview() {
    MoyiTheme(darkTheme = true) {
        TimeZonePicker(TimeZonePickerState(query = "Springfield", zones = emptyList()), onAction = {})
    }
}
