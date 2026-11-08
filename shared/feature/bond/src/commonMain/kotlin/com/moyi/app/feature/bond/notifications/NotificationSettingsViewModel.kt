package com.moyi.app.feature.bond.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moyi.app.core.ApiErrorCode
import com.moyi.app.core.DataError
import com.moyi.app.core.Result
import com.moyi.app.designsystem.platform.NotificationPermission
import com.moyi.app.designsystem.presentation.UiText
import com.moyi.app.domain.bond.BondSettingsRepository
import com.moyi.app.domain.bond.MemberSettings
import com.moyi.app.domain.bond.TimeOfDay
import com.moyi.app.domain.notification.NotificationCategory
import com.moyi.app.domain.notification.NotificationSettingsRepository
import com.moyi.app.feature.bond.resources.Res
import com.moyi.app.feature.bond.resources.settings_error_generic
import com.moyi.app.feature.bond.resources.settings_error_rate_limited
import com.moyi.app.feature.bond.settings.SettingsLoad
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Notifications for [bondId] (FR-080, FR-081).
 *
 * - **Load**: the six toggles and the member's own settings, side by side. Either
 *   failing is the Generic error with Try again; `404` or `409 BOND_ARCHIVED` is
 *   [NotificationSettingsEvent.Ended].
 * - **Each change** is one call, one at a time, with its row saying Saving. A toggle
 *   sends only itself; the reminder time and quiet hours replace the member's settings
 *   with every other field kept as read. A failure keeps the screen and what was there,
 *   and says so under the row.
 * - **The permission** is the phone's, not the server's: a toggle is saved whatever the
 *   phone allows, and the prompt is shown only as [NotificationPermissionPolicy] says.
 */
class NotificationSettingsViewModel(
    private val bondId: String,
    private val notifications: NotificationSettingsRepository,
    private val settings: BondSettingsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(NotificationSettingsState())
    val state: StateFlow<NotificationSettingsState> = _state.asStateFlow()

    private val _events = Channel<NotificationSettingsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        load()
    }

    fun onAction(action: NotificationSettingsAction) {
        when (action) {
            NotificationSettingsAction.OnRetryClick -> load()
            is NotificationSettingsAction.OnCategoryToggle -> toggle(action.category)
            NotificationSettingsAction.OnAllowClick -> _state.value.let {
                if (NotificationPermissionPolicy.offersAllow(it.permission) && !it.askingPermission) ask()
            }
            NotificationSettingsAction.OnOpenPhoneSettingsClick -> send(NotificationSettingsEvent.OpenPhoneSettings)
            is NotificationSettingsAction.OnPermissionChecked ->
                _state.update { it.copy(permission = action.permission, askingPermission = false) }
            NotificationSettingsAction.OnReminderTimeClick -> pick(TimeTarget.Reminder)
            NotificationSettingsAction.OnQuietStartClick -> pick(TimeTarget.QuietStart)
            NotificationSettingsAction.OnQuietEndClick -> pick(TimeTarget.QuietEnd)
            NotificationSettingsAction.OnTimeDismiss -> _state.update { it.copy(picking = null) }
            is NotificationSettingsAction.OnTimePicked -> timePicked(TimeOfDay(action.hour, action.minute))
            NotificationSettingsAction.OnQuietHoursToggle -> {
                val member = _state.value.member ?: return
                val next = if (_state.value.quietHoursOn) {
                    member.copy(quietHoursStart = null, quietHoursEnd = null)
                } else {
                    member.copy(quietHoursStart = DEFAULT_QUIET_START, quietHoursEnd = DEFAULT_QUIET_END)
                }
                saveMember(NotificationRow.QuietHours, next)
            }
        }
    }

    private fun load() {
        _state.update { it.copy(load = SettingsLoad.Loading, error = null) }
        viewModelScope.launch {
            val preferences = async { notifications.preferences(bondId) }
            val member = async { settings.memberSettings(bondId) }
            val prefs = preferences.await()
            val mine = member.await()
            when {
                prefs is Result.Success && mine is Result.Success ->
                    _state.update { it.copy(load = SettingsLoad.Ready, preferences = prefs.data, member = mine.data) }
                (prefs as? Result.Failure)?.error?.isGone() == true || (mine as? Result.Failure)?.error?.isGone() == true ->
                    send(NotificationSettingsEvent.Ended)
                else -> _state.update { it.copy(load = SettingsLoad.Failed) }
            }
        }
    }

    private fun toggle(category: NotificationCategory) {
        val current = _state.value
        val preferences = current.preferences ?: return
        if (current.saving != null) return
        val turningOn = !preferences.isOn(category)
        val row = NotificationRow.Category(category)
        _state.update { it.copy(saving = row, error = null) }
        if (NotificationPermissionPolicy.shouldAskOnToggle(current.permission, turningOn, current.askingPermission)) ask()
        viewModelScope.launch {
            when (val result = notifications.change(bondId, category, turningOn)) {
                is Result.Success -> _state.update { it.copy(preferences = result.data, saving = null) }
                is Result.Failure -> failed(row, result.error)
            }
        }
    }

    private fun pick(target: TimeTarget) {
        val current = _state.value
        if (current.member == null || current.saving != null) return
        if (target != TimeTarget.Reminder && !current.quietHoursOn) return
        _state.update { it.copy(picking = target, error = null) }
    }

    private fun timePicked(time: TimeOfDay) {
        val target = _state.value.picking ?: return
        val member = _state.value.member ?: return
        _state.update { it.copy(picking = null) }
        val next = when (target) {
            TimeTarget.Reminder -> member.copy(reminderTime = time)
            TimeTarget.QuietStart -> member.copy(quietHoursStart = time)
            TimeTarget.QuietEnd -> member.copy(quietHoursEnd = time)
        }
        if (next == member) return
        saveMember(if (target == TimeTarget.Reminder) NotificationRow.ReminderTime else NotificationRow.QuietHours, next)
    }

    /** A `PUT` of the whole of the member's settings: everything this screen does not change goes back as read. */
    private fun saveMember(row: NotificationRow, next: MemberSettings) {
        if (_state.value.saving != null) return
        _state.update { it.copy(saving = row, error = null) }
        viewModelScope.launch {
            when (val result = settings.replaceMemberSettings(bondId, next)) {
                is Result.Success -> _state.update { it.copy(member = result.data, saving = null) }
                is Result.Failure -> failed(row, result.error)
            }
        }
    }

    private fun failed(row: NotificationRow, error: DataError.Network) {
        if (error.isGone()) {
            _state.update { it.copy(saving = null) }
            send(NotificationSettingsEvent.Ended)
            return
        }
        _state.update { it.copy(saving = null, error = NotificationRowError(row, error.message())) }
    }

    private fun ask() {
        _state.update { it.copy(askingPermission = true) }
        send(NotificationSettingsEvent.AskPermission)
    }

    private fun send(event: NotificationSettingsEvent) {
        viewModelScope.launch { _events.send(event) }
    }

    private fun DataError.Network.code(): ApiErrorCode? = (this as? DataError.Network.Api)?.problem?.code

    private fun DataError.Network.isGone(): Boolean =
        (this as? DataError.Network.Api)?.problem?.status == 404 || code() == ApiErrorCode.BOND_ARCHIVED

    private fun DataError.Network.message(): UiText = UiText.Resource(
        if (code() == ApiErrorCode.RATE_LIMITED) Res.string.settings_error_rate_limited else Res.string.settings_error_generic,
    )

    companion object {
        /** Where quiet hours start when first turned on. PROPOSED: the owner's to choose. */
        val DEFAULT_QUIET_START = TimeOfDay(22, 0)
        val DEFAULT_QUIET_END = TimeOfDay(7, 0)
    }
}
