package com.moyi.app.feature.onboarding.common

import com.moyi.app.core.ApiErrorCode
import com.moyi.app.core.DataError
import com.moyi.app.designsystem.presentation.UiText
import com.moyi.app.feature.onboarding.resources.Res
import com.moyi.app.feature.onboarding.resources.bond_create_error
import com.moyi.app.feature.onboarding.resources.onboarding_error_generic
import com.moyi.app.feature.onboarding.resources.onboarding_error_rate_limited

/**
 * Reading a [DataError] in onboarding. Everything switches on the server's
 * `code` (or on what kind of failure it was), never on its English `detail`.
 */
internal fun DataError.code(): ApiErrorCode? = (this as? DataError.Network.Api)?.problem?.code

/** The request never got an answer: offline, DNS, refused, reset, or timed out. */
internal fun DataError.isOffline(): Boolean =
    this == DataError.Network.NoInternet || this == DataError.Network.Timeout

/** FR-002: the account is not verified, so it may not create or join a bond. */
internal fun DataError.isUnverified(): Boolean = code() == ApiErrorCode.EMAIL_NOT_VERIFIED

/**
 * The inline error a form shows above its action when nothing more precise
 * applies, its input kept (states.md §1c, §10):
 * - offline: the sign-up precedent, "Your details are still here";
 * - `429`: when, never why;
 * - anything else, a `500` or a `403 FORBIDDEN` included: the generic error.
 *   A `403` is not reachable by a member (states.md §1c), so it is not explained.
 */
internal fun DataError.formMessage(): UiText = UiText.Resource(
    when {
        isOffline() -> Res.string.bond_create_error
        code() == ApiErrorCode.RATE_LIMITED -> Res.string.onboarding_error_rate_limited
        else -> Res.string.onboarding_error_generic
    },
)
