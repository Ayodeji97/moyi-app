package com.moyi.app.feature.today.reminder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moyi.app.core.Result
import com.moyi.app.designsystem.platform.NotificationPermission
import com.moyi.app.domain.bond.BondSettingsRepository
import com.moyi.app.domain.bond.TimeOfDay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The reminder screen for [bondId] (owner rulings 2026-11-15). Today decides when it shows
 * and remembers that it did; this decides what it does.
 *
 * - **The time** is the reminder time the server holds for this member in this bond, the
 *   one Bond settings › Notifications shows (`/members/me/settings`). While it loads, the
 *   Field shows nothing and does not open; if it cannot be read, the Field is not drawn and
 *   nothing is saved, so no time is ever shown as fact on a guess.
 * - **Turn on reminders** saves a time picked here, the way Notification settings does
 *   (a `PUT` of the member's settings with every other field as read, the zone included),
 *   and only when it differs from the server's. Then the phone's own prompt. A save that
 *   fails does not stop the prompt: reminders still come, at the server's time.
 * - **The answer**: granted, on to Today. No, or a failed save: the screen says so and
 *   offers the way on (and Open phone settings after a no). Back from the phone's settings
 *   with notifications on, it goes on to Today by itself.
 * - **Not now**: on to Today. Today keeps its small note until the next day.
 */
class ReminderAskViewModel(
    private val bondId: String,
    private val settings: BondSettingsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ReminderAskState())
    val state: StateFlow<ReminderAskState> = _state.asStateFlow()

    private val _events = Channel<ReminderAskEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            when (val result = settings.memberSettings(bondId)) {
                is Result.Success -> _state.update { it.copy(load = ReminderTimeLoad.Ready, member = result.data) }
                is Result.Failure -> _state.update { it.copy(load = ReminderTimeLoad.Failed) }
            }
        }
    }

    fun onAction(action: ReminderAskAction) {
        when (action) {
            ReminderAskAction.OnTimeClick -> if (_state.value.canPick) _state.update { it.copy(picking = true) }
            ReminderAskAction.OnTimeDismiss -> _state.update { it.copy(picking = false) }
            is ReminderAskAction.OnTimePicked -> _state.update {
                if (it.canPick) it.copy(picking = false, chosen = TimeOfDay(action.hour, action.minute)) else it.copy(picking = false)
            }
            ReminderAskAction.OnTurnOnClick -> turnOn()
            is ReminderAskAction.OnPermissionAnswered -> answered(action.permission)
            is ReminderAskAction.OnPermissionChecked -> checked(action.permission)
            ReminderAskAction.OnNotNowClick -> if (_state.value.phase == ReminderAskPhase.Offering) send(ReminderAskEvent.Done)
            ReminderAskAction.OnOpenPhoneSettingsClick -> send(ReminderAskEvent.OpenPhoneSettings)
            ReminderAskAction.OnContinueClick -> if (_state.value.phase == ReminderAskPhase.Answered) send(ReminderAskEvent.Done)
        }
    }

    private fun turnOn() {
        val current = _state.value
        if (current.phase != ReminderAskPhase.Offering) return
        val member = current.member
        val chosen = current.chosen
        if (!current.changed || member == null || chosen == null) {
            prompt()
            return
        }
        _state.update { it.copy(phase = ReminderAskPhase.Saving, picking = false) }
        viewModelScope.launch {
            when (val result = settings.replaceMemberSettings(bondId, member.copy(reminderTime = chosen))) {
                is Result.Success -> _state.update { it.copy(member = result.data, chosen = null) }
                // The server keeps the time it had: the line names it, so the picked one goes.
                is Result.Failure -> _state.update { it.copy(saveFailed = true, chosen = null) }
            }
            prompt()
        }
    }

    private fun prompt() {
        _state.update { it.copy(phase = ReminderAskPhase.Prompting) }
        send(ReminderAskEvent.AskPermission)
    }

    private fun answered(permission: NotificationPermission) {
        if (_state.value.phase != ReminderAskPhase.Prompting) return
        val denied = permission == NotificationPermission.Denied
        _state.update { it.copy(phase = ReminderAskPhase.Answered, denied = denied) }
        // Granted, or no answer at all (a prompt closed without one): nothing is left to say.
        if (!denied && !_state.value.saveFailed) send(ReminderAskEvent.Done)
    }

    private fun checked(permission: NotificationPermission) {
        val current = _state.value
        if (current.phase != ReminderAskPhase.Answered || !current.denied) return
        if (permission != NotificationPermission.Granted) return
        _state.update { it.copy(denied = false) }
        if (!current.saveFailed) send(ReminderAskEvent.Done)
    }

    private fun send(event: ReminderAskEvent) {
        viewModelScope.launch { _events.send(event) }
    }
}
