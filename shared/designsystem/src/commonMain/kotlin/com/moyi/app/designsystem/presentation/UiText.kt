package com.moyi.app.designsystem.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Text a ViewModel hands to the UI without resolving it: a ViewModel has no
 * Compose context, and resolving there would freeze the language at the
 * moment the text was made.
 *
 * - [Resource]: a string resource, with its format arguments (`%1$s`). Every
 *   piece of product copy is one of these (CLAUDE.md: all user-facing text is
 *   a string resource).
 * - [Dynamic]: a value that is never translated, such as an email address.
 *
 * Both are data classes so a test can compare an expected message by equality.
 *
 * Lives in designsystem, not a feature, because every feature module already
 * depends on designsystem and every screen needs it; a separate presentation
 * module would hold two files.
 */
@Immutable
sealed interface UiText {
    data class Dynamic(val value: String) : UiText

    data class Resource(val id: StringResource, val args: List<Any> = emptyList()) : UiText

    @Composable
    fun asString(): String = when (this) {
        is Dynamic -> value
        is Resource -> stringResource(id, *args.toTypedArray())
    }
}
