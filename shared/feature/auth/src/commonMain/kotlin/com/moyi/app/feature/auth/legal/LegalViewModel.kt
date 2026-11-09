package com.moyi.app.feature.auth.legal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moyi.app.core.ApiErrorCode
import com.moyi.app.core.DataError
import com.moyi.app.core.Result
import com.moyi.app.designsystem.presentation.UiText
import com.moyi.app.domain.legal.LegalRepository
import com.moyi.app.feature.auth.common.SubmitStatus
import com.moyi.app.feature.auth.common.urlFor
import com.moyi.app.feature.auth.resources.Res
import com.moyi.app.feature.auth.resources.auth_error_generic
import com.moyi.app.feature.auth.resources.auth_error_rate_limited
import com.moyi.app.feature.auth.resources.legal_error_offline
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Terms and Privacy: reads the documents in force and what the person accepted
 * ([LegalRepository.status]), and records acceptance of the current ones.
 *
 * Accepting is a request with no body: the server records the versions IT names as
 * current (ADR-0056), so the screen re-reads the status afterwards only in settings
 * mode, where it stays open; the prompt closes.
 */
class LegalViewModel(
    prompt: Boolean,
    private val legalRepository: LegalRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(LegalState(prompt = prompt))
    val state: StateFlow<LegalState> = _state.asStateFlow()

    private val _events = Channel<LegalEvent>()
    val events = _events.receiveAsFlow()

    init {
        load()
    }

    fun onAction(action: LegalAction) {
        when (action) {
            LegalAction.OnRetryClick -> load()
            is LegalAction.OnDocumentClick ->
                _state.value.documents?.let { send(LegalEvent.OpenPage(it.urlFor(action.document))) }
            LegalAction.OnAcceptClick -> accept()
            LegalAction.OnCloseClick -> send(LegalEvent.Close)
        }
    }

    private fun load() {
        _state.update { it.copy(load = LegalLoad.Loading) }
        viewModelScope.launch {
            when (val result = legalRepository.status()) {
                is Result.Success -> _state.update {
                    it.copy(load = LegalLoad.Ready, documents = result.data.documents, upToDate = result.data.upToDate)
                }
                is Result.Failure -> _state.update { it.copy(load = LegalLoad.Failed) }
            }
        }
    }

    private fun accept() {
        val current = _state.value
        if (current.accept == SubmitStatus.Sending || current.load != LegalLoad.Ready) return
        _state.update { it.copy(accept = SubmitStatus.Sending) }
        viewModelScope.launch {
            when (val result = legalRepository.acceptCurrent()) {
                is Result.Success -> {
                    _state.update { it.copy(accept = SubmitStatus.Idle, upToDate = true) }
                    if (current.prompt) _events.send(LegalEvent.Close)
                }
                is Result.Failure -> _state.update { it.copy(accept = acceptFailure(result.error)) }
            }
        }
    }

    private fun send(event: LegalEvent) {
        viewModelScope.launch { _events.send(event) }
    }

    companion object {
        /**
         * A failed Accept, above the action. No code the contract lists is specific to this
         * call (`401` is the session's to handle, `403` and `429` are generic), so: offline
         * says to check the connection, a rate limit says when, anything else is the Generic
         * error. The action reads Try again unless waiting is the answer.
         */
        fun acceptFailure(error: DataError): SubmitStatus.Failed = when {
            error == DataError.Network.NoInternet || error == DataError.Network.Timeout ->
                SubmitStatus.Failed(UiText.Resource(Res.string.legal_error_offline), offerRetry = true)
            (error as? DataError.Network.Api)?.problem?.code == ApiErrorCode.RATE_LIMITED ->
                SubmitStatus.Failed(UiText.Resource(Res.string.auth_error_rate_limited), offerRetry = false)
            else -> SubmitStatus.Failed(UiText.Resource(Res.string.auth_error_generic), offerRetry = true)
        }
    }
}
