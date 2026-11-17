package com.moyi.app.feature.today.today

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.moyi.app.designsystem.MoyiTheme
import com.moyi.app.domain.bond.BondType
import com.moyi.app.domain.bond.SharedSettingNotice
import com.moyi.app.feature.today.common.DayNote
import com.moyi.app.feature.today.common.NoteAction
import com.moyi.app.designsystem.platform.timeOfDayText
import com.moyi.app.feature.today.resources.Res
import com.moyi.app.feature.today.resources.today_settings_close_agreed
import com.moyi.app.feature.today.resources.today_settings_close_called_off
import com.moyi.app.feature.today.resources.today_settings_close_kept_open
import com.moyi.app.feature.today.resources.today_settings_close_withdrawn
import com.moyi.app.feature.today.resources.today_settings_name
import com.moyi.app.feature.today.resources.today_settings_ok
import com.moyi.app.feature.today.resources.today_settings_rest_days_off
import com.moyi.app.feature.today.resources.today_settings_rest_days_on
import com.moyi.app.feature.today.resources.today_settings_reveal
import com.moyi.app.feature.today.resources.today_settings_reveal_none
import com.moyi.app.feature.today.resources.today_settings_search_agreed
import com.moyi.app.feature.today.resources.today_settings_search_asked
import com.moyi.app.feature.today.resources.today_settings_search_kept_off
import com.moyi.app.feature.today.resources.today_settings_search_turned_off
import com.moyi.app.feature.today.resources.today_settings_search_withdrawn
import com.moyi.app.feature.today.resources.today_settings_see
import com.moyi.app.feature.today.resources.today_settings_type_couple
import com.moyi.app.feature.today.resources.today_settings_type_family
import com.moyi.app.feature.today.resources.today_settings_type_friends
import com.moyi.app.feature.today.resources.today_settings_type_parent_child
import com.moyi.app.feature.today.resources.today_settings_tz_agreed
import com.moyi.app.feature.today.resources.today_settings_tz_kept
import com.moyi.app.feature.today.resources.today_settings_tz_withdrawn
import org.jetbrains.compose.resources.stringResource

/**
 * A change to a setting both members share, told once (owner ask 2026-11-16). The same
 * [DayNote] the ask for notifications uses, in the same place, with no heading: the line says
 * what changed and never who changed it. "See Bond settings" (outlined, as "Allow
 * notifications") opens the screen where the change is; "OK" (a text link, as "Not now")
 * marks it read. Either one makes room for the next note, if another change is waiting.
 * A note about search by meaning opens its own screen in Bond settings, where the answer is
 * (owner rulings 2026-11-17, 6 and 13).
 */
@Composable
internal fun SettingsNote(state: TodayState, onAction: (TodayAction) -> Unit) {
    val notice = state.settingsNotice ?: return
    Spacer(Modifier.height(MoyiTheme.spacing.lg))
    DayNote(
        line = noticeLine(notice),
        actions = listOfNotNull(
            NoteAction(stringResource(Res.string.today_settings_see), { onAction(TodayAction.OnSettingsNoticeOpenSettings) }, outlined = true),
            NoteAction(stringResource(Res.string.today_settings_ok), { onAction(TodayAction.OnSettingsNoticeDismiss) }),
        ),
    )
}

@Composable
private fun noticeLine(notice: SharedSettingNotice): String = when (notice) {
    is SharedSettingNotice.TimezoneAgreed -> stringResource(Res.string.today_settings_tz_agreed, cityOf(notice.timezone))
    is SharedSettingNotice.TimezoneKept -> stringResource(Res.string.today_settings_tz_kept, cityOf(notice.timezone))
    is SharedSettingNotice.TimezoneWithdrawn -> stringResource(Res.string.today_settings_tz_withdrawn, cityOf(notice.proposed))
    SharedSettingNotice.DeletionAgreed -> stringResource(Res.string.today_settings_close_agreed)
    SharedSettingNotice.DeletionKeptOpen -> stringResource(Res.string.today_settings_close_kept_open)
    SharedSettingNotice.DeletionWithdrawn -> stringResource(Res.string.today_settings_close_withdrawn)
    SharedSettingNotice.DeletionCalledOff -> stringResource(Res.string.today_settings_close_called_off)
    SharedSettingNotice.SemanticSearchAgreed -> stringResource(Res.string.today_settings_search_agreed)
    SharedSettingNotice.SemanticSearchKeptOff -> stringResource(Res.string.today_settings_search_kept_off)
    SharedSettingNotice.SemanticSearchWithdrawn -> stringResource(Res.string.today_settings_search_withdrawn)
    SharedSettingNotice.SemanticSearchTurnedOff -> stringResource(Res.string.today_settings_search_turned_off)
    SharedSettingNotice.SemanticSearchAsked -> stringResource(Res.string.today_settings_search_asked)
    is SharedSettingNotice.NameChanged -> stringResource(Res.string.today_settings_name, notice.name)
    is SharedSettingNotice.RevealTimeChanged -> notice.time
        ?.let { stringResource(Res.string.today_settings_reveal, timeOfDayText(it.hour, it.minute)) }
        ?: stringResource(Res.string.today_settings_reveal_none)
    is SharedSettingNotice.RestDaysChanged ->
        stringResource(if (notice.on) Res.string.today_settings_rest_days_on else Res.string.today_settings_rest_days_off)
    is SharedSettingNotice.TypeChanged -> stringResource(
        when (notice.type) {
            BondType.COUPLE -> Res.string.today_settings_type_couple
            BondType.FRIENDS -> Res.string.today_settings_type_friends
            BondType.FAMILY -> Res.string.today_settings_type_family
            BondType.PARENT_CHILD -> Res.string.today_settings_type_parent_child
        },
    )
}

/**
 * A zone as its city, `Europe/London` as London: Bond settings' and onboarding's own rule
 * (CreateBondViewModel.cityOf), repeated because feature/today does not see those modules.
 */
internal fun cityOf(zoneId: String): String = zoneId.substringAfterLast('/').replace('_', ' ')
