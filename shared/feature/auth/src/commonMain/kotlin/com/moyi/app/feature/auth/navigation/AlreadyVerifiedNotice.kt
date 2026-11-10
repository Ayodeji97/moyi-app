package com.moyi.app.feature.auth.navigation

import androidx.compose.runtime.Composable
import com.moyi.app.feature.auth.resources.Res
import com.moyi.app.feature.auth.resources.verify_already_confirmed
import org.jetbrains.compose.resources.stringResource

/**
 * What the shell's snackbar says when a verify link opens the app for someone whose address
 * is already confirmed, so the link opens nothing (owner ruling 2026-11-10,
 * docs/missing-components.md §62). The words live with the rest of verify's.
 */
@Composable
fun alreadyVerifiedNotice(): String = stringResource(Res.string.verify_already_confirmed)
