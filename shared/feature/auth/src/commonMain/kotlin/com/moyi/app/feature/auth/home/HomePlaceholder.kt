package com.moyi.app.feature.auth.home

import androidx.compose.foundation.layout.Spacer
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
import com.moyi.app.designsystem.component.TextRole
import com.moyi.app.designsystem.component.TextTone
import com.moyi.app.domain.auth.AuthRepository
import com.moyi.app.domain.auth.SessionState
import com.moyi.app.feature.auth.resources.Res
import com.moyi.app.feature.auth.resources.home_greeting
import com.moyi.app.feature.auth.resources.home_line
import com.moyi.app.feature.auth.resources.home_sign_out
import com.moyi.app.feature.auth.resources.home_sign_out_sending
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/*
 * TEMPORARY. The signed-in placeholder that proves the auth flow end to end
 * (M6) until Today is built. Its copy is proposed and temporary. After sign
 * out, the app shell sees SessionEvent.Ended(SignedOut) and routes to sign in;
 * this screen does not navigate itself.
 */

@Immutable
data class HomePlaceholderState(
    val displayName: String = "",
    val signingOut: Boolean = false,
)

sealed interface HomePlaceholderAction {
    data object OnSignOutClick : HomePlaceholderAction
}

class HomePlaceholderViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val signingOut = MutableStateFlow(false)

    val state: StateFlow<HomePlaceholderState> =
        combine(authRepository.session, signingOut) { session, out ->
            HomePlaceholderState(
                displayName = (session as? SessionState.SignedIn)?.user?.displayName.orEmpty(),
                signingOut = out,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomePlaceholderState())

    fun onAction(action: HomePlaceholderAction) {
        when (action) {
            HomePlaceholderAction.OnSignOutClick -> {
                if (signingOut.value) return
                signingOut.value = true
                viewModelScope.launch {
                    // Always ends the local session, even offline (AuthRepository.logout).
                    authRepository.logout()
                }
            }
        }
    }
}

@Composable
fun HomePlaceholderRoot(viewModel: HomePlaceholderViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomePlaceholderScreen(state = state, onAction = viewModel::onAction)
}

@Composable
fun HomePlaceholderScreen(
    state: HomePlaceholderState,
    onAction: (HomePlaceholderAction) -> Unit,
) {
    MoyiScaffold(
        action = {
            PrimaryAction(
                text = stringResource(Res.string.home_sign_out),
                onClick = { onAction(HomePlaceholderAction.OnSignOutClick) },
                state = if (state.signingOut) {
                    PrimaryActionState.Sending(stringResource(Res.string.home_sign_out_sending))
                } else {
                    PrimaryActionState.Idle
                },
            )
        },
    ) {
        Spacer(Modifier.height(MoyiTheme.spacing.xxl))
        MoyiText(
            text = stringResource(Res.string.home_greeting, state.displayName),
            role = TextRole.Title,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(MoyiTheme.spacing.md))
        MoyiText(stringResource(Res.string.home_line), tone = TextTone.Muted)
    }
}

@Preview
@Composable
private fun HomePlaceholderPreview() {
    MoyiTheme(darkTheme = true) {
        HomePlaceholderScreen(state = HomePlaceholderState(displayName = "Amara"), onAction = {})
    }
}
