package com.moyi.app.feature.auth.common

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.moyi.app.designsystem.MoyiTheme
import com.moyi.app.designsystem.component.MoyiText
import com.moyi.app.designsystem.component.PrimaryActionState
import com.moyi.app.designsystem.component.TextRole

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

/**
 * The eyebrow and heading every auth screen opens with (auth-placement-spec
 * §2): the eyebrow in `label`, 64 above the heading in `title`. The heading
 * is a semantic heading, so a screen reader can jump to it.
 */
@Composable
internal fun AuthHeader(eyebrow: String, heading: String) {
    MoyiText(eyebrow, role = TextRole.Label)
    Spacer(Modifier.height(MoyiTheme.spacing.xxxl))
    MoyiText(heading, role = TextRole.Title, modifier = Modifier.semantics { heading() })
}
