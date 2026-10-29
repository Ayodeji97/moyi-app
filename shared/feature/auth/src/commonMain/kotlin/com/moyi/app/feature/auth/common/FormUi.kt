package com.moyi.app.feature.auth.common

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import com.moyi.app.designsystem.component.PrimaryActionState

/**
 * Calls [onBlur] when focus leaves after having been inside. The first
 * callback, which reports "not focused" as the node attaches, is ignored.
 */
internal fun Modifier.onBlur(onBlur: () -> Unit): Modifier = composed {
    var hadFocus by remember { mutableStateOf(false) }
    onFocusChanged { state ->
        if (state.isFocused) {
            hadFocus = true
        } else if (hadFocus) {
            hadFocus = false
            onBlur()
        }
    }
}

/** The [PrimaryActionState] for a submission. [enabled] false is Disabled (e.g. consent unticked). */
internal fun SubmitStatus.toActionState(sendingLabel: String, enabled: Boolean = true): PrimaryActionState = when {
    this == SubmitStatus.Sending -> PrimaryActionState.Sending(sendingLabel)
    !enabled -> PrimaryActionState.Disabled
    else -> PrimaryActionState.Idle
}
