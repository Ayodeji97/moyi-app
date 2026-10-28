package com.moyi.app.feature.today.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.moyi.app.designsystem.MoyiTheme
import com.moyi.app.designsystem.component.MoyiText
import com.moyi.app.designsystem.component.TextLink
import com.moyi.app.designsystem.component.TextRole
import com.moyi.app.designsystem.component.TextTone
import com.moyi.app.designsystem.icon.MoyiIcons
import com.moyi.app.designsystem.theme.MoyiDimens
import com.moyi.app.feature.today.resources.Res
import com.moyi.app.feature.today.resources.today_nav_archive
import com.moyi.app.feature.today.resources.today_nav_settings
import org.jetbrains.compose.resources.stringResource

/**
 * Content that is **centred vertically when it fits** and top-aligned and
 * scrolling when it does not (Figma 159:9 "content is vertically centred when it
 * fits"; tokens.md §2 "the container centres when content fits, top-aligns and
 * scrolls when it does not"). At 200 % font nearly everything scrolls (NFR-102).
 */
@Composable
internal fun ColumnScope.CenteredContent(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxWidth().weight(1f)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight),
            verticalArrangement = Arrangement.Center,
            content = content,
        )
    }
}

/**
 * Today's `nav` row (Figma 159:2, 358 × 48 at y 24): the date, then the
 * calendar target (to Archive) and the settings target (to Bond settings),
 * both 48 × 48 with 24 glyphs. No back: Today is the root (SKILL.md §9).
 *
 * [onDebugCycle], when set, makes a long press on the date step the fake
 * state (PR #8 debug switch; see docs/figma/today-check.md "Reaching the screens").
 */
@Composable
internal fun TodayNavRow(
    date: String,
    onArchiveClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onDebugCycle: (() -> Unit)?,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(MoyiDimens.NavRow.Height),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.weight(1f).height(MoyiDimens.NavRow.Height).debugLongPress(onDebugCycle),
            contentAlignment = Alignment.CenterStart,
        ) {
            DateLabel(date)
        }
        NavTarget(MoyiIcons.Calendar, stringResource(Res.string.today_nav_archive), onArchiveClick)
        NavTarget(MoyiIcons.Settings, stringResource(Res.string.today_nav_settings), onSettingsClick)
    }
}

/** The day's date, `label` style in `inkMuted` (Figma 159:3 / 179:4; style derived, not read). */
@Composable
internal fun DateLabel(date: String, modifier: Modifier = Modifier) {
    MoyiText(text = date, role = TextRole.Label, modifier = modifier)
}

@Composable
private fun NavTarget(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(MoyiDimens.TouchTarget)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = MoyiTheme.colors.ink, modifier = Modifier.size(MoyiDimens.IconGlyph))
    }
}

/** A title-style heading, announced as a heading ("Your day is open."). */
@Composable
internal fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    MoyiText(text = text, role = TextRole.Title, modifier = modifier.semantics { heading() })
}

/** One action under a [DayNote]: a full-width 48 target, `ink` (or `danger`) with an underline. */
internal data class NoteAction(val label: String, val onClick: () -> Unit, val destructive: Boolean = false)

/**
 * A heading, one line and up to two actions under an entry: the waiting,
 * partner-pending, solo and offline notes on Today.
 *
 * TODO(PR #7): this is a stand-in for the design system's `StateBlock`
 * (tokens.md §4 component 5; Figma page 72:2), which PR #7 adds. After the
 * rebase, replace every DayNote with the matching StateBlock variant (Waiting,
 * PartnerPending, Solo, Offline) and delete this. Its internals were never read
 * from Figma; the spacing here is the screen rhythm (16 inside a block).
 */
@Composable
internal fun DayNote(
    line: String,
    modifier: Modifier = Modifier,
    heading: String? = null,
    actions: List<NoteAction> = emptyList(),
) {
    Column(modifier.fillMaxWidth()) {
        if (heading != null) {
            ScreenTitle(heading)
            Spacer(Modifier.height(MoyiTheme.spacing.md))
        }
        MoyiText(text = line, tone = TextTone.Muted)
        if (actions.isNotEmpty()) {
            Spacer(Modifier.height(MoyiTheme.spacing.sm))
            actions.forEach { action ->
                TextLink(
                    text = action.label,
                    onClick = action.onClick,
                    destructive = action.destructive,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * DEBUG ONLY (PR #8): a long press runs [onLongPress]. Null does nothing, so a
 * build without the debug switch has no hidden gesture.
 */
internal fun Modifier.debugLongPress(onLongPress: (() -> Unit)?): Modifier =
    if (onLongPress == null) this else pointerInput(onLongPress) { detectTapGestures(onLongPress = { onLongPress() }) }
