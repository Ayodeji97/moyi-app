package com.moyi.app.feature.auth.common

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.moyi.app.designsystem.component.Field
import com.moyi.app.designsystem.component.FieldType
import com.moyi.app.feature.auth.resources.Res
import com.moyi.app.feature.auth.resources.code_label
import org.jetbrains.compose.resources.stringResource

/**
 * The one field for the emailed code, on verify and on Choose a new password
 * (docs/missing-components.md §58). It is the design system's `Field` `Code`
 * (`tokens.md` §4): one control in the code style, **not six boxes**, which
 * would announce as six controls, break paste and fail at 200 % text.
 *
 * - The label, "6-digit code", is the field's spoken name.
 * - The keyboard is the number pad (`NumberPassword`: no suggestions, nothing
 *   learned from it). A whole link pasted with the paste menu still arrives in
 *   full and is read by [parseCodeInput]; the ViewModel decides what is kept.
 * - `ContentType.SmsOtpCode` marks it as a one-time code for autofill. iOS
 *   offers a code it finds in a message or mail above the keyboard
 *   (`.oneTimeCode`); Android's autofill services do likewise.
 * - An error is a polite live region while it shows, so a refusal after the
 *   sixth digit (no button pressed, focus still here) is spoken. Typing clears
 *   the error, and the field stops being a live region with it, so digits
 *   are not read back as they are typed.
 */
@Composable
internal fun EmailCodeField(
    value: String,
    onValueChange: (String) -> Unit,
    helper: String,
    error: String?,
    imeAction: ImeAction,
    onImeAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Field(
        value = value,
        onValueChange = onValueChange,
        label = stringResource(Res.string.code_label),
        type = FieldType.Code,
        helper = helper,
        error = error,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.NumberPassword,
            autoCorrectEnabled = false,
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(onAny = { onImeAction() }),
        modifier = modifier.semantics {
            contentType = ContentType.SmsOtpCode
            if (error != null) liveRegion = LiveRegionMode.Polite
        },
    )
}
