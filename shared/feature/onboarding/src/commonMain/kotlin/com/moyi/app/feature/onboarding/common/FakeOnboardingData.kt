package com.moyi.app.feature.onboarding.common

/**
 * The fake data PR #7's screens run on. Nothing here is real, and every use
 * sits next to a `TODO(logic)` naming the call that replaces it. Kept in one
 * place so the logic PR can delete one file and follow the compile errors.
 */
internal object FakeOnboardingData {
    /** A code in the invite alphabet (`23456789ABCDEFGHJKMNPQRSTVWXYZ`). */
    const val INVITE_CODE = "K7Q2PD"

    /** The device time zone's city, until the platform is asked. */
    const val TIME_ZONE_CITY = "Lagos"

    /** What `GET /api/v1/invites/{code}` would return for any well-formed code... */
    const val INVITER_NAME = "Amara"
    const val BOND_NAME = "Amara and Tunde"
    val BOND_TYPE = RelationshipType.Couple

    /** ...except this one, which plays "cannot be used" (expired, revoked, full or unknown). */
    const val UNUSABLE_CODE = "ZZZZZZ"
}
