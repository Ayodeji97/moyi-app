package com.moyi.app.designsystem.component

import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.text.input.PlatformImeOptions

/**
 * What a [Field] holds, for the platform's saved passwords and one-time codes. One value
 * says it once for both platforms, because they read it in different places:
 *
 * - Android (and the common semantics tree) reads the field's `ContentType` semantics:
 *   Google Password Manager, or whichever autofill service is chosen, fills and saves from it.
 * - iOS reads `UITextContentType` from the keyboard's `PlatformImeOptions`. Compose
 *   Multiplatform 1.12 does not map `ContentType` semantics to it; left alone it derives
 *   `.emailAddress` from an email keyboard and `.password` from any password keyboard,
 *   the six-digit code included.
 */
enum class FieldAutofill {
    /** The address someone signs in with: an email that is also the account's username. */
    AccountEmail,

    /** The current password, on sign in. */
    Password,

    /** A password being chosen: sign up, and a reset. Password managers offer a strong one. */
    NewPassword,

    /** The emailed six-digit code. */
    OneTimeCode,

    /** The person's own name, on sign up. */
    FullName,
}

/** The `ContentType` semantics Android's autofill reads. */
val FieldAutofill.contentType: ContentType
    get() = when (this) {
        FieldAutofill.AccountEmail -> ContentType.EmailAddress + ContentType.Username
        FieldAutofill.Password -> ContentType.Password
        FieldAutofill.NewPassword -> ContentType.NewPassword
        FieldAutofill.OneTimeCode -> ContentType.SmsOtpCode
        FieldAutofill.FullName -> ContentType.PersonFullName
    }

/** iOS: the `UITextContentType` for the keyboard. Android: null, the semantics are enough. */
internal expect fun FieldAutofill.platformImeOptions(): PlatformImeOptions?
