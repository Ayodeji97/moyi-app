package com.moyi.app.feature.bond.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import com.moyi.app.designsystem.MoyiTheme
import com.moyi.app.designsystem.component.Field
import com.moyi.app.designsystem.component.FieldType
import com.moyi.app.designsystem.component.InlineError
import com.moyi.app.designsystem.component.MoyiScaffold
import com.moyi.app.designsystem.component.MoyiText
import com.moyi.app.designsystem.component.PrimaryAction
import com.moyi.app.designsystem.component.PrimaryActionState
import com.moyi.app.designsystem.component.ScreenHeader
import com.moyi.app.designsystem.component.StateBlock
import com.moyi.app.designsystem.component.TextLink
import com.moyi.app.designsystem.component.TextTone
import com.moyi.app.domain.account.DeletionRequest
import com.moyi.app.feature.bond.resources.Res
import com.moyi.app.feature.bond.resources.account_delete_value_pending
import com.moyi.app.feature.bond.resources.account_eyebrow
import com.moyi.app.feature.bond.resources.account_heading
import com.moyi.app.feature.bond.resources.account_row_delete
import com.moyi.app.feature.bond.resources.account_row_export
import com.moyi.app.feature.bond.resources.delete_account_confirm
import com.moyi.app.feature.bond.resources.delete_account_confirming
import com.moyi.app.feature.bond.resources.delete_account_consequence
import com.moyi.app.feature.bond.resources.delete_account_export_first
import com.moyi.app.feature.bond.resources.delete_account_heading
import com.moyi.app.feature.bond.resources.delete_account_keep
import com.moyi.app.feature.bond.resources.delete_account_keeping
import com.moyi.app.feature.bond.resources.delete_account_line
import com.moyi.app.feature.bond.resources.delete_account_password_helper
import com.moyi.app.feature.bond.resources.delete_account_password_label
import com.moyi.app.feature.bond.resources.delete_account_pending_heading
import com.moyi.app.feature.bond.resources.delete_account_pending_line
import com.moyi.app.feature.bond.resources.settings_cancel
import com.moyi.app.feature.bond.resources.settings_load_error_action
import com.moyi.app.feature.bond.resources.settings_load_error_heading
import com.moyi.app.feature.bond.resources.settings_load_error_line
import com.moyi.app.feature.bond.settings.RowKind
import com.moyi.app.feature.bond.settings.SettingsRow
import com.moyi.app.feature.bond.settings.localDayAndMonth
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

/**
 * Account (Figma `Account 7 · settings`, 575:1782; strings-proposed.md §9), reached from
 * Bond settings › Just you › Account. The eyebrow Account, the heading Your account, then
 * its rows: **Export your archive** (PROPOSED here: the drawn screen has Get help and Delete
 * your account) and **Delete your account**, underlined in `danger`, with the day it happens
 * while one is pending. Get help is left out, with no dead row: its screen is not built.
 *
 * Loading is the bare `surface`; a failed load is the Generic error with Try again, and
 * Export your archive stays under it, because it needs nothing `/me` holds.
 */
@Composable
fun AccountScreen(
    state: AccountState,
    onAction: (AccountAction) -> Unit,
    onBack: () -> Unit,
) {
    when (state.load) {
        AccountLoad.Loading -> Box(Modifier.fillMaxSize().background(MoyiTheme.colors.surface))
        AccountLoad.Failed -> MoyiScaffold(onBack = onBack, contentArrangement = Arrangement.Center) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                StateBlock(
                    heading = stringResource(Res.string.settings_load_error_heading),
                    line = stringResource(Res.string.settings_load_error_line),
                    actionLabel = stringResource(Res.string.settings_load_error_action),
                    onAction = { onAction(AccountAction.OnRetryClick) },
                )
            }
            Spacer(Modifier.height(MoyiTheme.spacing.xxl))
            ExportRow(onAction)
        }
        AccountLoad.Ready -> MoyiScaffold(onBack = onBack) {
            ScreenHeader(eyebrow = stringResource(Res.string.account_eyebrow), heading = stringResource(Res.string.account_heading))
            Spacer(Modifier.height(MoyiTheme.spacing.lg))
            ExportRow(onAction)
            SettingsRow(
                label = stringResource(Res.string.account_row_delete),
                value = state.pending?.let { stringResource(Res.string.account_delete_value_pending, localDayAndMonth(it.cancellableUntil)) },
                kind = RowKind.Navigates,
                destructive = true,
                onClick = { onAction(AccountAction.OnDeleteClick) },
            )
        }
    }
}

@Composable
private fun ExportRow(onAction: (AccountAction) -> Unit) {
    SettingsRow(
        label = stringResource(Res.string.account_row_export),
        kind = RowKind.Navigates,
        onClick = { onAction(AccountAction.OnExportClick) },
    )
}

/**
 * Delete your account (FR-008; states.md §9 "Account deletion"; PROPOSED words), one
 * destination whose content follows [AccountState.pending], as Close the box's does:
 *
 * - **Confirm**: what happens and when, in full (owner ruling 2026-11-09: a destructive,
 *   irreversible action says what it means), a link to export first, the password typed
 *   again, then **Delete my account** (`danger`, the primary: no friction, states.md §9) and
 *   Cancel 24 under it. A wrong password is the field's error; the input is kept.
 * - **Pending**: the day it happens (a date, never a countdown) and that nothing changes
 *   until then; the safe action, **Keep my account**, is the primary, as Keep the box is.
 */
@Composable
fun DeleteAccountScreen(
    state: AccountState,
    onAction: (AccountAction) -> Unit,
    onBack: () -> Unit,
) {
    val pending = state.pending
    if (pending != null) {
        PendingDeletion(state, pending, onAction, onBack)
    } else {
        ConfirmDeletion(state, onAction, onBack)
    }
}

@Composable
private fun ConfirmDeletion(state: AccountState, onAction: (AccountAction) -> Unit, onBack: () -> Unit) {
    val spacing = MoyiTheme.spacing
    val busy = state.inFlight != null
    val confirm = { onAction(AccountAction.OnConfirmDeletion) }
    MoyiScaffold(
        onBack = onBack,
        action = {
            state.error?.let {
                InlineError(it.asString())
                Spacer(Modifier.height(spacing.sm))
            }
            PrimaryAction(
                text = stringResource(Res.string.delete_account_confirm),
                onClick = confirm,
                destructive = true,
                state = if (state.inFlight == DeletionCall.Request) {
                    PrimaryActionState.Sending(stringResource(Res.string.delete_account_confirming))
                } else {
                    PrimaryActionState.Idle
                },
            )
            Spacer(Modifier.height(spacing.lg))
            TextLink(
                text = stringResource(Res.string.settings_cancel),
                onClick = onBack,
                centered = true,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) {
        val heading = stringResource(Res.string.delete_account_heading)
        ScreenHeader(eyebrow = heading, heading = heading)
        Spacer(Modifier.height(spacing.md))
        MoyiText(stringResource(Res.string.delete_account_line), tone = TextTone.Muted)
        Spacer(Modifier.height(spacing.md))
        MoyiText(stringResource(Res.string.delete_account_consequence), tone = TextTone.Muted)
        Spacer(Modifier.height(spacing.md))
        TextLink(
            text = stringResource(Res.string.delete_account_export_first),
            onClick = { onAction(AccountAction.OnExportClick) },
            enabled = !busy,
        )
        Spacer(Modifier.height(spacing.lg))
        Field(
            value = state.password,
            onValueChange = { onAction(AccountAction.OnPasswordChange(it)) },
            label = stringResource(Res.string.delete_account_password_label),
            type = FieldType.Secure,
            helper = stringResource(Res.string.delete_account_password_helper),
            error = state.passwordError?.asString(),
            enabled = !busy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { confirm() }),
        )
    }
}

@Composable
private fun PendingDeletion(state: AccountState, pending: DeletionRequest, onAction: (AccountAction) -> Unit, onBack: () -> Unit) {
    val spacing = MoyiTheme.spacing
    MoyiScaffold(
        onBack = onBack,
        action = {
            state.error?.let {
                InlineError(it.asString())
                Spacer(Modifier.height(spacing.sm))
            }
            PrimaryAction(
                text = stringResource(Res.string.delete_account_keep),
                onClick = { onAction(AccountAction.OnKeepAccount) },
                state = if (state.inFlight == DeletionCall.Cancel) {
                    PrimaryActionState.Sending(stringResource(Res.string.delete_account_keeping))
                } else {
                    PrimaryActionState.Idle
                },
            )
        },
    ) {
        val heading = stringResource(Res.string.delete_account_pending_heading, localDayAndMonth(pending.cancellableUntil))
        ScreenHeader(eyebrow = heading, heading = heading)
        Spacer(Modifier.height(spacing.md))
        MoyiText(stringResource(Res.string.delete_account_pending_line), tone = TextTone.Muted)
        Spacer(Modifier.height(spacing.md))
        TextLink(
            text = stringResource(Res.string.account_row_export),
            onClick = { onAction(AccountAction.OnExportClick) },
            enabled = state.inFlight == null,
        )
    }
}

private val previewPending = DeletionRequest(
    requestedAt = Instant.parse("2026-11-09T10:00:00Z"),
    cancellableUntil = Instant.parse("2026-11-23T10:00:00Z"),
    deleteBy = Instant.parse("2026-12-09T10:00:00Z"),
)

@Preview
@Composable
private fun AccountPreview() {
    MoyiTheme(darkTheme = true) { AccountScreen(AccountState(load = AccountLoad.Ready), onAction = {}, onBack = {}) }
}

@Preview
@Composable
private fun AccountPendingLightPreview() {
    MoyiTheme(darkTheme = false) { AccountScreen(AccountState(load = AccountLoad.Ready, pending = previewPending), onAction = {}, onBack = {}) }
}

@Preview
@Composable
private fun DeleteAccountPreview() {
    MoyiTheme(darkTheme = true) { DeleteAccountScreen(AccountState(load = AccountLoad.Ready), onAction = {}, onBack = {}) }
}

@Preview
@Composable
private fun DeleteAccountPendingPreview() {
    MoyiTheme(darkTheme = true) { DeleteAccountScreen(AccountState(load = AccountLoad.Ready, pending = previewPending), onAction = {}, onBack = {}) }
}
