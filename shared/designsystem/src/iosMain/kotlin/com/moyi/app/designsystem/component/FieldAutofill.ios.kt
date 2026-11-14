package com.moyi.app.designsystem.component

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.text.input.PlatformImeOptions
import platform.UIKit.UITextContentTypeName
import platform.UIKit.UITextContentTypeNewPassword
import platform.UIKit.UITextContentTypeOneTimeCode
import platform.UIKit.UITextContentTypePassword
import platform.UIKit.UITextContentTypeUsername

/**
 * Apple's guidance for Password AutoFill: `.username` on the account field (an email
 * included), `.password` when signing in, `.newPassword` when choosing one. The code is
 * `.oneTimeCode` and not secure text, so iOS can offer a code from Mail above the keyboard;
 * a number-pad keyboard otherwise makes it a password field.
 */
@OptIn(ExperimentalComposeUiApi::class)
internal actual fun FieldAutofill.platformImeOptions(): PlatformImeOptions? = PlatformImeOptions {
    when (this@platformImeOptions) {
        FieldAutofill.AccountEmail -> textContentType(UITextContentTypeUsername)
        FieldAutofill.Password -> textContentType(UITextContentTypePassword)
        FieldAutofill.NewPassword -> textContentType(UITextContentTypeNewPassword)
        FieldAutofill.OneTimeCode -> {
            textContentType(UITextContentTypeOneTimeCode)
            isSecureTextEntry(false)
        }
        FieldAutofill.FullName -> textContentType(UITextContentTypeName)
    }
}
