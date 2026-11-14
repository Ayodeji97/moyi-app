@file:OptIn(ExperimentalComposeUiApi::class)

package com.moyi.app.designsystem

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.text.input.isSecureTextEntry
import androidx.compose.ui.text.input.textContentType
import com.moyi.app.designsystem.component.FieldAutofill
import com.moyi.app.designsystem.component.platformImeOptions
import platform.UIKit.UITextContentTypeName
import platform.UIKit.UITextContentTypeNewPassword
import platform.UIKit.UITextContentTypeOneTimeCode
import platform.UIKit.UITextContentTypePassword
import platform.UIKit.UITextContentTypeUsername
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * iOS reads `UITextContentType` from the keyboard, not the `ContentType` semantics, so each
 * field's kind must reach it: `.username` on the email is what lets Passwords pair the
 * email with the password.
 */
class FieldAutofillIosTest {
    @Test
    fun eachKindIsTheMatchingUITextContentType() {
        val expected = mapOf(
            FieldAutofill.AccountEmail to UITextContentTypeUsername,
            FieldAutofill.Password to UITextContentTypePassword,
            FieldAutofill.NewPassword to UITextContentTypeNewPassword,
            FieldAutofill.OneTimeCode to UITextContentTypeOneTimeCode,
            FieldAutofill.FullName to UITextContentTypeName,
        )
        for ((kind, type) in expected) {
            assertEquals(type, assertNotNull(kind.platformImeOptions()).textContentType, "for $kind")
        }
    }

    @Test
    fun theCodeIsNotSecureTextSoMailCanOfferIt() {
        assertEquals(false, assertNotNull(FieldAutofill.OneTimeCode.platformImeOptions()).isSecureTextEntry)
    }
}
