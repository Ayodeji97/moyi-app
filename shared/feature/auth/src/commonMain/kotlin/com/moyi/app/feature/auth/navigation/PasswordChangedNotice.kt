package com.moyi.app.feature.auth.navigation

import androidx.compose.runtime.Composable
import com.moyi.app.feature.auth.resources.Res
import com.moyi.app.feature.auth.resources.change_password_done
import org.jetbrains.compose.resources.stringResource

/**
 * What the shell's snackbar says over Account once a password changed while signed in is
 * saved and this phone kept signed in (docs/missing-components.md §83). The words live with
 * the rest of the reset's.
 */
@Composable
fun passwordChangedNotice(): String = stringResource(Res.string.change_password_done)
