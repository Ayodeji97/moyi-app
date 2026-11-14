package com.moyi.app.designsystem.component

import androidx.compose.ui.text.input.PlatformImeOptions

/** Android's autofill reads the `ContentType` semantics alone (FieldAutofill.contentType). */
internal actual fun FieldAutofill.platformImeOptions(): PlatformImeOptions? = null
