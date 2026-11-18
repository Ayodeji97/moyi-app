package com.moyi.app.feature.bond.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.moyi.app.designsystem.MoyiTheme
import com.moyi.app.designsystem.component.ConfirmDialog
import com.moyi.app.designsystem.component.FactsCard
import com.moyi.app.designsystem.component.FactsCardLink
import com.moyi.app.designsystem.component.InlineError
import com.moyi.app.designsystem.component.MoyiScaffold
import com.moyi.app.designsystem.component.MoyiText
import com.moyi.app.designsystem.component.PrimaryAction
import com.moyi.app.designsystem.component.PrimaryActionState
import com.moyi.app.designsystem.component.ScreenHeader
import com.moyi.app.designsystem.component.StateBlock
import com.moyi.app.designsystem.component.TextLink
import com.moyi.app.designsystem.component.TextTone
import com.moyi.app.domain.bond.BondSettings
import com.moyi.app.domain.bond.BondStatus
import com.moyi.app.domain.bond.BondType
import com.moyi.app.domain.bond.PendingSemanticSearchRequest
import com.moyi.app.feature.bond.resources.Res
import com.moyi.app.feature.bond.resources.settings_cancel
import com.moyi.app.feature.bond.resources.settings_load_error_action
import com.moyi.app.feature.bond.resources.settings_load_error_heading
import com.moyi.app.feature.bond.resources.settings_load_error_line
import com.moyi.app.feature.bond.resources.search_agree
import com.moyi.app.feature.bond.resources.search_agreeing
import com.moyi.app.feature.bond.resources.search_alone_line
import com.moyi.app.feature.bond.resources.search_ask
import com.moyi.app.feature.bond.resources.search_asked_heading
import com.moyi.app.feature.bond.resources.search_asked_line
import com.moyi.app.feature.bond.resources.search_asked_line_plain
import com.moyi.app.feature.bond.resources.search_asking
import com.moyi.app.feature.bond.resources.search_cancel
import com.moyi.app.feature.bond.resources.search_cancelling
import com.moyi.app.feature.bond.resources.search_decline
import com.moyi.app.feature.bond.resources.search_declining
import com.moyi.app.feature.bond.resources.search_ended_line
import com.moyi.app.feature.bond.resources.search_fact_either
import com.moyi.app.feature.bond.resources.search_fact_hidden
import com.moyi.app.feature.bond.resources.search_fact_hidden_unnamed
import com.moyi.app.feature.bond.resources.search_fact_sent
import com.moyi.app.feature.bond.resources.search_facts_label
import com.moyi.app.feature.bond.resources.search_heading_off
import com.moyi.app.feature.bond.resources.search_heading_on
import com.moyi.app.feature.bond.resources.search_off_confirm_line
import com.moyi.app.feature.bond.resources.search_off_confirm_line_alone
import com.moyi.app.feature.bond.resources.search_off_confirm_title
import com.moyi.app.feature.bond.resources.search_partner_heading
import com.moyi.app.feature.bond.resources.search_partner_heading_unnamed
import com.moyi.app.feature.bond.resources.search_partner_line
import com.moyi.app.feature.bond.resources.search_privacy_link
import com.moyi.app.feature.bond.resources.search_turn_off
import com.moyi.app.feature.bond.resources.search_turning_off
import com.moyi.app.feature.bond.resources.search_what
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

/**
 * Bond settings › Search by meaning (FR-204, ADR-0054; docs/missing-components.md §89). Not drawn
 * in Figma; built from the design preview of 2026-11-16 and the owner's rulings of 2026-11-17,
 * on the close-the-box screen's layout: back, the heading, a muted line, then the facts card
 * ("Good to know"), and the screen's one action pinned at the foot.
 *
 * - **Off**: what it does; **Ask to turn it on** (Sending: Asking). While nobody has joined, a
 *   line under it says it turns on now (the server applies it at once).
 * - **Asked** (mine): the day it lapses; only **Cancel this request** (Cancelling). Nothing for
 *   the person to do but wait or withdraw, so no primary.
 * - **PartnerAsked**: who asked and that it covers every day; **Agree** (Agreeing) and **Decline**
 *   (Declining), the time zone's Confirm layout.
 * - **On**: **Turn it off** (Turning off), after "Turn off search by meaning?", because turning it
 *   back on needs both (owner ruling 2026-11-17, 11). The dialog is the house `ConfirmDialog`,
 *   not destructive: nothing anyone wrote is lost.
 * - **Ended** (the bond has ended or counts down to deletion): off says it cannot be turned on,
 *   with no action; on still offers Turn it off (ADR-0054 decision 9). On an archived bond, reached
 *   from the archived screen (§91), the dialog's line is the ended line: it can never come back on.
 *
 * An error (412 conflict, rate limit, the rest) sits 8 above the action that caused it, as on
 * every settings screen; a failure to read the Privacy Policy's address shows there too.
 */
@Composable
fun SearchByMeaningScreen(
    state: BondSettingsState,
    onAction: (BondSettingsAction) -> Unit,
    onBack: () -> Unit,
) {
    val settings = state.settings
    if (settings == null) {
        NotRead(state.load, onAction, onBack)
        return
    }
    val spacing = MoyiTheme.spacing
    val busy = state.saving == SettingsField.Search
    val primaryInFlight = busy && !state.searchSecondaryInFlight
    val secondaryInFlight = busy && state.searchSecondaryInFlight
    val othersBusy = state.saving != null
    val step = state.searchStep
    val partner = settings.partnerName
    val ended = settings.ended
    MoyiScaffold(
        onBack = onBack,
        action = {
            state.error?.takeIf { it.field == SettingsField.Search }?.let {
                InlineError(it.message.asString())
                Spacer(Modifier.height(spacing.sm))
            }
            when {
                step == SearchStep.On -> PrimaryAction(
                    text = stringResource(Res.string.search_turn_off),
                    onClick = { onAction(BondSettingsAction.OnSearchTurnOffClick) },
                    state = when {
                        primaryInFlight -> PrimaryActionState.Sending(stringResource(Res.string.search_turning_off))
                        othersBusy -> PrimaryActionState.Disabled
                        else -> PrimaryActionState.Idle
                    },
                )
                // Ended: nothing turns it on (409 BOND_ARCHIVED), so nothing is offered.
                ended -> Unit
                step == SearchStep.Off -> {
                    PrimaryAction(
                        text = stringResource(Res.string.search_ask),
                        onClick = { onAction(BondSettingsAction.OnSearchAsk) },
                        state = when {
                            primaryInFlight -> PrimaryActionState.Sending(stringResource(Res.string.search_asking))
                            othersBusy -> PrimaryActionState.Disabled
                            else -> PrimaryActionState.Idle
                        },
                    )
                    if (settings.waitingForPartner) {
                        Spacer(Modifier.height(spacing.md))
                        MoyiText(
                            stringResource(Res.string.search_alone_line),
                            tone = TextTone.Muted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                step == SearchStep.PartnerAsked -> {
                    PrimaryAction(
                        text = stringResource(Res.string.search_agree),
                        onClick = { onAction(BondSettingsAction.OnSearchAsk) },
                        state = when {
                            primaryInFlight -> PrimaryActionState.Sending(stringResource(Res.string.search_agreeing))
                            othersBusy -> PrimaryActionState.Disabled
                            else -> PrimaryActionState.Idle
                        },
                    )
                    Spacer(Modifier.height(spacing.lg))
                    TextLink(
                        text = stringResource(if (secondaryInFlight) Res.string.search_declining else Res.string.search_decline),
                        onClick = { onAction(BondSettingsAction.OnSearchCancel) },
                        centered = true,
                        enabled = !othersBusy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                step == SearchStep.Asked -> TextLink(
                    text = stringResource(if (secondaryInFlight) Res.string.search_cancelling else Res.string.search_cancel),
                    onClick = { onAction(BondSettingsAction.OnSearchCancel) },
                    centered = true,
                    enabled = !othersBusy,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    ) {
        val heading = when (step) {
            SearchStep.Off -> stringResource(Res.string.search_heading_off)
            SearchStep.On -> stringResource(Res.string.search_heading_on)
            SearchStep.Asked -> stringResource(Res.string.search_asked_heading)
            SearchStep.PartnerAsked -> partner?.let { stringResource(Res.string.search_partner_heading, it) }
                ?: stringResource(Res.string.search_partner_heading_unnamed)
        }
        ScreenHeader(eyebrow = heading, heading = heading)
        Spacer(Modifier.height(spacing.md))
        when (step) {
            SearchStep.Asked -> {
                val lapses = settings.pendingSemanticSearch?.expiresAt
                MoyiText(
                    if (partner != null && lapses != null) {
                        stringResource(Res.string.search_asked_line, partner, localDayAndMonth(lapses))
                    } else {
                        stringResource(Res.string.search_asked_line_plain)
                    },
                    tone = TextTone.Muted,
                )
            }
            else -> MoyiText(stringResource(Res.string.search_what))
        }
        if (step == SearchStep.PartnerAsked) {
            Spacer(Modifier.height(spacing.md))
            MoyiText(stringResource(Res.string.search_partner_line), tone = TextTone.Muted)
        }
        if (ended && step != SearchStep.On) {
            Spacer(Modifier.height(spacing.md))
            MoyiText(stringResource(Res.string.search_ended_line), tone = TextTone.Muted)
        }
        Spacer(Modifier.height(spacing.lg))
        FactsCard(
            label = stringResource(Res.string.search_facts_label),
            facts = listOf(
                stringResource(Res.string.search_fact_sent),
                partner?.let { stringResource(Res.string.search_fact_hidden, it) } ?: stringResource(Res.string.search_fact_hidden_unnamed),
                stringResource(Res.string.search_fact_either),
            ),
            link = FactsCardLink(stringResource(Res.string.search_privacy_link)) { onAction(BondSettingsAction.OnPrivacyPolicyClick) },
        )
    }

    if (state.confirmingSearchOff) {
        ConfirmDialog(
            title = stringResource(Res.string.search_off_confirm_title),
            message = stringResource(
                when {
                    settings.waitingForPartner -> Res.string.search_off_confirm_line_alone
                    // Archived: nothing turns it back on, so "needs you both to agree" would not be true.
                    settings.status == BondStatus.ARCHIVED -> Res.string.search_ended_line
                    else -> Res.string.search_off_confirm_line
                },
            ),
            confirmLabel = stringResource(Res.string.search_turn_off),
            dismissLabel = stringResource(Res.string.settings_cancel),
            onConfirm = { onAction(BondSettingsAction.OnSearchTurnOffConfirm) },
            onDismiss = { onAction(BondSettingsAction.OnSearchTurnOffDismiss) },
        )
    }
}

private val previewSearchBond = BondSettings(
    bondId = "preview-bond",
    name = "Amara and Tunde",
    type = BondType.COUPLE,
    status = BondStatus.ACTIVE,
    anchorTimezone = "Africa/Lagos",
    revealTime = null,
    strictMode = false,
    version = "\"1\"",
    partnerName = "Tunde",
    semanticSearchEnabled = false,
)

private fun searchState(settings: BondSettings) = BondSettingsState(load = SettingsLoad.Ready, settings = settings)

/**
 * Opened from a note on Today, the screen can come before the bond is read: the bare `surface`
 * while it is, as the root draws it; a failed read is the root's Generic error with Try again,
 * under the back target, so the way out stays.
 */
@Composable
private fun NotRead(load: SettingsLoad, onAction: (BondSettingsAction) -> Unit, onBack: () -> Unit) {
    if (load != SettingsLoad.Failed) {
        Box(Modifier.fillMaxSize().background(MoyiTheme.colors.surface))
        return
    }
    MoyiScaffold(onBack = onBack, contentArrangement = Arrangement.Center) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            StateBlock(
                heading = stringResource(Res.string.settings_load_error_heading),
                line = stringResource(Res.string.settings_load_error_line),
                actionLabel = stringResource(Res.string.settings_load_error_action),
                onAction = { onAction(BondSettingsAction.OnRetryClick) },
            )
        }
    }
}

@Preview
@Composable
private fun SearchOffPreview() {
    MoyiTheme(darkTheme = true) { SearchByMeaningScreen(searchState(previewSearchBond), onAction = {}, onBack = {}) }
}

@Preview
@Composable
private fun SearchAskedLightPreview() {
    val asked = previewSearchBond.copy(pendingSemanticSearch = PendingSemanticSearchRequest(true, Instant.parse("2026-11-23T10:00:00Z")))
    MoyiTheme(darkTheme = false) { SearchByMeaningScreen(searchState(asked), onAction = {}, onBack = {}) }
}

@Preview
@Composable
private fun SearchPartnerAskedPreview() {
    val theirs = previewSearchBond.copy(pendingSemanticSearch = PendingSemanticSearchRequest(false))
    MoyiTheme(darkTheme = true) { SearchByMeaningScreen(searchState(theirs), onAction = {}, onBack = {}) }
}

@Preview
@Composable
private fun SearchOnPreview() {
    MoyiTheme(darkTheme = true) { SearchByMeaningScreen(searchState(previewSearchBond.copy(semanticSearchEnabled = true)), onAction = {}, onBack = {}) }
}

@Preview
@Composable
private fun SearchEndedLightPreview() {
    val ended = previewSearchBond.copy(status = BondStatus.PENDING_DELETION)
    MoyiTheme(darkTheme = false) { SearchByMeaningScreen(searchState(ended), onAction = {}, onBack = {}) }
}
