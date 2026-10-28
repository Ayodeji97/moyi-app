package com.moyi.app.feature.onboarding.common

import com.moyi.app.feature.onboarding.resources.Res
import com.moyi.app.feature.onboarding.resources.bond_type_couple
import com.moyi.app.feature.onboarding.resources.bond_type_family
import com.moyi.app.feature.onboarding.resources.bond_type_friends
import com.moyi.app.feature.onboarding.resources.join_confirm_line_couple
import com.moyi.app.feature.onboarding.resources.join_confirm_line_family
import com.moyi.app.feature.onboarding.resources.join_confirm_line_friends
import org.jetbrains.compose.resources.StringResource

/**
 * The three relationship-type cards (states.md §1a), in their drawn order.
 *
 * A UI model, not the API's `Bond.type`: the mapping is exact (COUPLE,
 * FRIENDS, FAMILY) and `PARENT_CHILD` has no card. It moves to the domain
 * when the bond repository exists.
 * TODO(logic): map to the domain's bond type for `POST /api/v1/bonds` (`type`).
 */
enum class RelationshipType(val label: StringResource, val confirmLine: StringResource) {
    Couple(Res.string.bond_type_couple, Res.string.join_confirm_line_couple),
    Friends(Res.string.bond_type_friends, Res.string.join_confirm_line_friends),
    Family(Res.string.bond_type_family, Res.string.join_confirm_line_family),
}
