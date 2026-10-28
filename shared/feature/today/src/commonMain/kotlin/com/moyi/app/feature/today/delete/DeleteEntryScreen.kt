package com.moyi.app.feature.today.delete

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.moyi.app.designsystem.MoyiTheme
import com.moyi.app.designsystem.component.MoyiScaffold
import com.moyi.app.designsystem.component.MoyiText
import com.moyi.app.designsystem.component.PrimaryAction
import com.moyi.app.designsystem.component.PrimaryActionState
import com.moyi.app.designsystem.component.TextLink
import com.moyi.app.designsystem.component.TextRole
import com.moyi.app.designsystem.component.TextTone
import com.moyi.app.designsystem.presentation.ObserveAsEvents
import com.moyi.app.feature.today.common.FakeCopy
import com.moyi.app.feature.today.common.FakeDailyLoop
import com.moyi.app.feature.today.common.FakeLatency
import com.moyi.app.feature.today.common.TodayStatus
import com.moyi.app.feature.today.resources.Res
import com.moyi.app.feature.today.resources.delete_cancel
import com.moyi.app.feature.today.resources.delete_confirm
import com.moyi.app.feature.today.resources.delete_deleting
import com.moyi.app.feature.today.resources.delete_eyebrow
import com.moyi.app.feature.today.resources.delete_heading
import com.moyi.app.feature.today.resources.delete_line
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Duration

/** Delete after reveal, the confirmation (Figma 420:64). The only confirm in the daily loop (SKILL.md §3). */
@Immutable
data class DeleteEntryState(
    val partnerName: String = FakeCopy.PARTNER_NAME,
    val deleting: Boolean = false,
)

sealed interface DeleteEntryAction {
    data object OnConfirmClick : DeleteEntryAction
    data object OnCancelClick : DeleteEntryAction
}

sealed interface DeleteEntryEvent {
    /** Back to Today: deleted (it now shows the tombstone, 420:54) or cancelled. */
    data object NavigateBack : DeleteEntryEvent
}

class DeleteEntryViewModel(
    private val loop: FakeDailyLoop,
    private val fakeLatency: Duration = FakeLatency,
) : ViewModel() {
    private val _state = MutableStateFlow(DeleteEntryState())
    val state: StateFlow<DeleteEntryState> = _state.asStateFlow()

    private val _events = Channel<DeleteEntryEvent>()
    val events = _events.receiveAsFlow()

    fun onAction(action: DeleteEntryAction) {
        when (action) {
            DeleteEntryAction.OnConfirmClick -> {
                if (_state.value.deleting) return
                _state.update { it.copy(deleting = true) }
                viewModelScope.launch {
                    // TODO(logic): DELETE /api/v1/entries/{entryId} (Idempotency-Key optional). Not
                    //  optimistic: it is irreversible, so the tombstone shows only after a 2xx. A failure
                    //  stays here with the generic error (states.md §10). 409 BOND_ARCHIVED has no
                    //  drawn state (states.md §5a, owner's question 1).
                    delay(fakeLatency)
                    loop.setStatus(TodayStatus.Tombstone)
                    _events.send(DeleteEntryEvent.NavigateBack)
                }
            }
            DeleteEntryAction.OnCancelClick -> viewModelScope.launch { _events.send(DeleteEntryEvent.NavigateBack) }
        }
    }
}

@Composable
fun DeleteEntryRoot(
    onNavigateBack: () -> Unit,
    viewModel: DeleteEntryViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            DeleteEntryEvent.NavigateBack -> onNavigateBack()
        }
    }
    DeleteEntryScreen(state = state, onAction = viewModel::onAction)
}

/**
 * Figma 420:64, the root-form skeleton: the eyebrow at y 24, the heading at 107
 * (64 below), the line 16 below it; the danger PrimaryAction at 700 and Cancel at
 * 772. No back: Cancel is the way out (system back does the same).
 *
 * The line must never become a read receipt (states.md Appendix): it says what is
 * true without reporting on anyone, and that deleting yours never touches theirs.
 */
@Composable
fun DeleteEntryScreen(
    state: DeleteEntryState,
    onAction: (DeleteEntryAction) -> Unit,
) {
    MoyiScaffold(
        action = {
            PrimaryAction(
                text = stringResource(Res.string.delete_confirm),
                onClick = { onAction(DeleteEntryAction.OnConfirmClick) },
                state = if (state.deleting) PrimaryActionState.Sending(stringResource(Res.string.delete_deleting)) else PrimaryActionState.Idle,
                destructive = true,
            )
            Spacer(Modifier.height(MoyiTheme.spacing.lg))
            TextLink(
                text = stringResource(Res.string.delete_cancel),
                onClick = { onAction(DeleteEntryAction.OnCancelClick) },
                centered = true,
                enabled = !state.deleting,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) {
        MoyiText(stringResource(Res.string.delete_eyebrow), role = TextRole.Label)
        Spacer(Modifier.height(MoyiTheme.spacing.xxxl))
        MoyiText(
            text = stringResource(Res.string.delete_heading),
            role = TextRole.Title,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(MoyiTheme.spacing.md))
        MoyiText(stringResource(Res.string.delete_line, state.partnerName), tone = TextTone.Muted)
    }
}

@Preview
@Composable
private fun DeleteEntryPreview() {
    MoyiTheme(darkTheme = true) { DeleteEntryScreen(state = DeleteEntryState(), onAction = {}) }
}

@Preview
@Composable
private fun DeleteEntryDeletingLightPreview() {
    MoyiTheme(darkTheme = false) { DeleteEntryScreen(state = DeleteEntryState(deleting = true), onAction = {}) }
}
