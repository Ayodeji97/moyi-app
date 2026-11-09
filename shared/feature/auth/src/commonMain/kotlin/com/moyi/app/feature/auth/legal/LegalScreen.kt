package com.moyi.app.feature.auth.legal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moyi.app.designsystem.MoyiTheme
import com.moyi.app.designsystem.component.InlineError
import com.moyi.app.designsystem.component.MoyiScaffold
import com.moyi.app.designsystem.component.MoyiText
import com.moyi.app.designsystem.component.PrimaryAction
import com.moyi.app.designsystem.component.ScreenHeader
import com.moyi.app.designsystem.component.StateBlock
import com.moyi.app.designsystem.component.TextLink
import com.moyi.app.designsystem.component.TextTone
import com.moyi.app.designsystem.platform.rememberInAppBrowser
import com.moyi.app.designsystem.presentation.ObserveAsEvents
import com.moyi.app.domain.legal.LegalDocuments
import com.moyi.app.domain.legal.PublishedDocument
import com.moyi.app.feature.auth.common.LegalDocument
import com.moyi.app.feature.auth.common.SubmitStatus
import com.moyi.app.feature.auth.common.toActionState
import com.moyi.app.feature.auth.resources.Res
import com.moyi.app.feature.auth.resources.auth_try_again
import com.moyi.app.feature.auth.resources.legal_accept
import com.moyi.app.feature.auth.resources.legal_accept_sending
import com.moyi.app.feature.auth.resources.legal_data_processing
import com.moyi.app.feature.auth.resources.legal_eyebrow
import com.moyi.app.feature.auth.resources.legal_heading
import com.moyi.app.feature.auth.resources.legal_heading_changed
import com.moyi.app.feature.auth.resources.legal_line_changed
import com.moyi.app.feature.auth.resources.legal_line_current
import com.moyi.app.feature.auth.resources.legal_load_error_action
import com.moyi.app.feature.auth.resources.legal_load_error_heading
import com.moyi.app.feature.auth.resources.legal_load_error_line
import com.moyi.app.feature.auth.resources.legal_not_now
import com.moyi.app.feature.auth.resources.register_privacy
import com.moyi.app.feature.auth.resources.register_terms
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun LegalRoot(
    prompt: Boolean,
    onClose: () -> Unit,
    viewModel: LegalViewModel = koinViewModel { parametersOf(prompt) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val browser = rememberInAppBrowser()
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is LegalEvent.OpenPage -> browser.open(event.url)
            LegalEvent.Close -> onClose()
        }
    }
    LegalScreen(state = state, onAction = viewModel::onAction)
}

/**
 * Terms and Privacy. PROPOSED (docs/missing-components.md §54): no frame draws it. Built
 * from the house parts only: the back target, ScreenHeader, a `body` line in `inkMuted`,
 * the three documents as TextLinks (an underline goes somewhere), and, while the person's
 * consent is older than the documents, PrimaryAction Accept with its Sending label.
 *
 * As the re-consent prompt it says what changed and what to do, and Not now sits under
 * Accept: the server refuses nothing for an old consent, so the prompt never traps anyone.
 * Opened from Bond settings it is the same screen, reading "Terms and Privacy".
 *
 * Loading is the bare `surface`; a failed load is the Generic error with Try again, under
 * the back target, so the way out stays.
 */
@Composable
fun LegalScreen(
    state: LegalState,
    onAction: (LegalAction) -> Unit,
) {
    val close = { onAction(LegalAction.OnCloseClick) }
    val documents = state.documents
    when {
        state.load == LegalLoad.Loading -> Box(Modifier.fillMaxSize().background(MoyiTheme.colors.surface))
        state.load == LegalLoad.Failed || documents == null ->
            MoyiScaffold(onBack = close, contentArrangement = Arrangement.Center) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    StateBlock(
                        heading = stringResource(Res.string.legal_load_error_heading),
                        line = stringResource(Res.string.legal_load_error_line),
                        actionLabel = stringResource(Res.string.legal_load_error_action),
                        onAction = { onAction(LegalAction.OnRetryClick) },
                    )
                }
            }
        else -> LegalReady(state, documents, onAction, close)
    }
}

@Composable
private fun LegalReady(state: LegalState, documents: LegalDocuments, onAction: (LegalAction) -> Unit, close: () -> Unit) {
    val spacing = MoyiTheme.spacing
    val failed = state.accept as? SubmitStatus.Failed
    val asking = !state.upToDate
    MoyiScaffold(
        onBack = close,
        action = if (!asking) null else {
            {
                if (failed != null) {
                    InlineError(failed.message.asString())
                    Spacer(Modifier.height(spacing.md))
                }
                PrimaryAction(
                    text = stringResource(if (failed?.offerRetry == true) Res.string.auth_try_again else Res.string.legal_accept),
                    onClick = { onAction(LegalAction.OnAcceptClick) },
                    state = state.accept.toActionState(sendingLabel = stringResource(Res.string.legal_accept_sending)),
                )
                if (state.prompt) {
                    Spacer(Modifier.height(spacing.md))
                    TextLink(
                        stringResource(Res.string.legal_not_now),
                        onClick = close,
                        centered = true,
                        enabled = state.accept != SubmitStatus.Sending,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
    ) {
        ScreenHeader(
            eyebrow = stringResource(Res.string.legal_eyebrow),
            heading = stringResource(if (asking) Res.string.legal_heading_changed else Res.string.legal_heading),
        )
        Spacer(Modifier.height(spacing.md))
        MoyiText(
            stringResource(if (asking) Res.string.legal_line_changed else Res.string.legal_line_current),
            tone = TextTone.Muted,
        )
        Spacer(Modifier.height(spacing.lg))
        Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            TextLink(stringResource(Res.string.register_terms), onClick = { onAction(LegalAction.OnDocumentClick(LegalDocument.Terms)) })
            TextLink(stringResource(Res.string.register_privacy), onClick = { onAction(LegalAction.OnDocumentClick(LegalDocument.Privacy)) })
            TextLink(
                stringResource(Res.string.legal_data_processing),
                onClick = { onAction(LegalAction.OnDocumentClick(LegalDocument.DataProcessing)) },
            )
        }
    }
}

private val previewDocuments = LegalDocuments(
    terms = PublishedDocument("2026-12-01", "https://getmoyi.app/terms"),
    privacy = PublishedDocument("2026-12-01", "https://getmoyi.app/privacy"),
    dataProcessingSummary = PublishedDocument("2026-12-01", "https://getmoyi.app/data"),
)

@Preview
@Composable
private fun LegalPromptPreview() {
    MoyiTheme(darkTheme = true) {
        LegalScreen(LegalState(prompt = true, load = LegalLoad.Ready, documents = previewDocuments, upToDate = false), onAction = {})
    }
}

@Preview
@Composable
private fun LegalSettingsPreview() {
    MoyiTheme(darkTheme = false) {
        LegalScreen(LegalState(prompt = false, load = LegalLoad.Ready, documents = previewDocuments, upToDate = true), onAction = {})
    }
}
